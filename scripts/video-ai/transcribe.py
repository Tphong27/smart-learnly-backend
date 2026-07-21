#!/usr/bin/env python3
"""Offline, deterministic JSON transcription entrypoint for Smart Learnly."""

from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import sys
import tempfile

from faster_whisper import WhisperModel


ALLOWED_LANGUAGES = {"auto", "vi", "en"}
ALLOWED_DEVICES = {"cpu", "cuda"}
ALLOWED_COMPUTE_TYPES = {"int8", "int8_float16", "float16", "float32"}
MAX_INPUT_BYTES = 2 * 1024 * 1024 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Transcribe one local audio file to JSON")
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--model", default="small")
    parser.add_argument("--language", default="auto", choices=sorted(ALLOWED_LANGUAGES))
    parser.add_argument("--device", default="cpu", choices=sorted(ALLOWED_DEVICES))
    parser.add_argument("--compute-type", default="int8", choices=sorted(ALLOWED_COMPUTE_TYPES))
    parser.add_argument("--cpu-threads", type=int, default=4)
    parser.add_argument("--beam-size", type=int, default=5)
    parser.add_argument("--download-root")
    return parser.parse_args()


def validate_args(args: argparse.Namespace) -> tuple[Path, Path]:
    requested_input = Path(args.input)
    if requested_input.is_symlink():
        raise ValueError("input must not be a symbolic link")
    input_path = requested_input.resolve(strict=True)
    if not input_path.is_file():
        raise ValueError("input must be a regular local file")
    if input_path.stat().st_size <= 0 or input_path.stat().st_size > MAX_INPUT_BYTES:
        raise ValueError("input size is outside the supported range")
    requested_output = Path(args.output)
    if requested_output.is_symlink():
        raise ValueError("output must not be a symbolic link")
    output_path = requested_output.resolve(strict=False)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if not 1 <= args.cpu_threads <= 64:
        raise ValueError("cpu-threads must be between 1 and 64")
    if not 1 <= args.beam_size <= 10:
        raise ValueError("beam-size must be between 1 and 10")
    if not args.model or len(args.model) > 128 or any(ch in args.model for ch in "\r\n\0"):
        raise ValueError("model is invalid")
    return input_path, output_path


def finite_seconds(value: float, field: str) -> float:
    number = float(value)
    if not math.isfinite(number) or number < 0:
        raise ValueError(f"invalid {field} returned by transcription model")
    return round(number, 3)


def atomic_write_json(output_path: Path, payload: dict) -> None:
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output_path.name}.", suffix=".tmp", dir=output_path.parent
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, output_path)
    except BaseException:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def collect_segments(segments_iterator) -> list[dict]:
    segments = []
    for segment in segments_iterator:
        text = segment.text.strip()
        if not text:
            continue
        start = finite_seconds(segment.start, "segment start")
        end = finite_seconds(segment.end, "segment end")
        if end <= start:
            continue
        segments.append({"index": len(segments), "start": start, "end": end, "text": text})
    return segments


def main() -> int:
    args = parse_args()
    input_path, output_path = validate_args(args)
    language = None if args.language == "auto" else args.language
    model = WhisperModel(
        args.model,
        device=args.device,
        compute_type=args.compute_type,
        cpu_threads=args.cpu_threads,
        download_root=args.download_root,
    )
    segments_iterator, info = model.transcribe(
        str(input_path),
        language=language,
        beam_size=args.beam_size,
        vad_filter=True,
        vad_parameters={"min_silence_duration_ms": 500},
        condition_on_previous_text=False,
    )
    segments = collect_segments(segments_iterator)
    if not segments:
        print(
            "voice activity detection found no speech; retrying the full audio",
            file=sys.stderr,
        )
        segments_iterator, info = model.transcribe(
            str(input_path),
            language=language,
            beam_size=args.beam_size,
            vad_filter=False,
            condition_on_previous_text=False,
        )
        segments = collect_segments(segments_iterator)

    duration = finite_seconds(info.duration, "duration")
    detected_language = (info.language or language or "unknown").lower()
    payload = {
        "language": detected_language,
        "languageProbability": round(float(info.language_probability or 0.0), 6),
        "duration": duration,
        "segments": segments,
    }
    atomic_write_json(output_path, payload)
    print(f"transcribed {len(segments)} segments ({detected_language}, {duration:.3f}s)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print(f"transcription failed: {error}", file=sys.stderr)
        raise SystemExit(2)
