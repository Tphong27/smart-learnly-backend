#!/usr/bin/env python3
"""Fetch one public YouTube transcript and write a small JSON result."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import (
    IpBlocked,
    NoTranscriptFound,
    RequestBlocked,
    TranscriptsDisabled,
    VideoUnavailable,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--video-id", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


class TranscriptUnavailable(Exception):
    """Raised when the API returns no usable transcript tracks."""


def choose_transcript(video_id: str):
    transcripts = list(YouTubeTranscriptApi().list(video_id))
    if not transcripts:
        raise TranscriptUnavailable()
    return next(
        (transcript for transcript in transcripts if not transcript.is_generated),
        transcripts[0],
    )


def main() -> int:
    args = parse_args()
    try:
        transcript = choose_transcript(args.video_id)
        fetched = transcript.fetch()
        payload = {
            "language": transcript.language_code,
            "segments": [
                {
                    "start": snippet.start,
                    "duration": snippet.duration,
                    "text": snippet.text,
                }
                for snippet in fetched
                if snippet.text and snippet.text.strip()
            ],
        }
        Path(args.output).write_text(
            json.dumps(payload, ensure_ascii=False),
            encoding="utf-8",
        )
        return 0
    except TranscriptsDisabled:
        print("TRANSCRIPT_DISABLED", file=sys.stderr)
    except (NoTranscriptFound, TranscriptUnavailable):
        print("TRANSCRIPT_NOT_FOUND", file=sys.stderr)
    except VideoUnavailable:
        print("VIDEO_UNAVAILABLE", file=sys.stderr)
    except (RequestBlocked, IpBlocked):
        print("YOUTUBE_BLOCKED", file=sys.stderr)
    except Exception as exception:  # Library/network errors are mapped by Java.
        print(f"TRANSCRIPT_ERROR:{type(exception).__name__}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
