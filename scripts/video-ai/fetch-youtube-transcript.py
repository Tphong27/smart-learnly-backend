#!/usr/bin/env python3
"""Fetch one public YouTube transcript and write a small JSON result."""

from __future__ import annotations

import argparse
import json
import os
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
from youtube_transcript_api.proxies import GenericProxyConfig, WebshareProxyConfig


def parse_args() -> argparse.Namespace:
    """Đọc video ID và đường dẫn output do backend Java truyền vào."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--video-id", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


class TranscriptUnavailable(Exception):
    """Báo rằng YouTube không trả transcript track có thể sử dụng."""


class TranscriptConfigurationError(Exception):
    """Báo cấu hình proxy transcript thiếu hoặc không hợp lệ."""


def normalized_environment(name: str) -> str | None:
    """Đọc một biến môi trường và đổi giá trị rỗng thành None."""
    value = os.getenv(name)
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def proxy_retry_count() -> int:
    """Đọc số lần đổi IP khi Webshare bị chặn và giới hạn trong khoảng an toàn."""
    raw_value = normalized_environment("YOUTUBE_TRANSCRIPT_PROXY_RETRIES")
    if raw_value is None:
        return 10
    try:
        return min(20, max(1, int(raw_value)))
    except ValueError as exception:
        raise TranscriptConfigurationError() from exception


def build_proxy_config():
    """Tạo proxy config từ môi trường mà không đưa credential lên command line."""
    mode = (normalized_environment("YOUTUBE_TRANSCRIPT_PROXY_MODE") or "auto").lower()
    username = normalized_environment("YOUTUBE_TRANSCRIPT_PROXY_USERNAME")
    password = normalized_environment("YOUTUBE_TRANSCRIPT_PROXY_PASSWORD")
    http_url = normalized_environment("YOUTUBE_TRANSCRIPT_PROXY_HTTP_URL")
    https_url = normalized_environment("YOUTUBE_TRANSCRIPT_PROXY_HTTPS_URL")

    if mode == "auto":
        if username or password:
            mode = "webshare"
        elif http_url or https_url:
            mode = "generic"
        else:
            mode = "none"

    if mode == "none":
        return None
    if mode == "webshare":
        if not username or not password:
            raise TranscriptConfigurationError()
        locations_value = normalized_environment("YOUTUBE_TRANSCRIPT_PROXY_COUNTRIES")
        locations = None
        if locations_value:
            locations = [
                item.strip().lower()
                for item in locations_value.split(",")
                if item.strip()
            ] or None
        return WebshareProxyConfig(
            proxy_username=username,
            proxy_password=password,
            filter_ip_locations=locations,
            retries_when_blocked=proxy_retry_count(),
        )
    if mode == "generic":
        if not http_url and not https_url:
            raise TranscriptConfigurationError()
        return GenericProxyConfig(
            http_url=http_url,
            https_url=https_url,
        )
    raise TranscriptConfigurationError()


def build_transcript_api() -> YouTubeTranscriptApi:
    """Khởi tạo transcript client trực tiếp hoặc qua proxy đã cấu hình."""
    proxy_config = build_proxy_config()
    if proxy_config is None:
        return YouTubeTranscriptApi()
    return YouTubeTranscriptApi(proxy_config=proxy_config)


def choose_transcript(video_id: str):
    """Ưu tiên caption thủ công rồi mới dùng caption tự động."""
    transcripts = list(build_transcript_api().list(video_id))
    if not transcripts:
        raise TranscriptUnavailable()
    return next(
        (transcript for transcript in transcripts if not transcript.is_generated),
        transcripts[0],
    )


def main() -> int:
    """Lấy transcript, ghi JSON và trả marker lỗi ổn định cho backend Java."""
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
    except TranscriptConfigurationError:
        print("TRANSCRIPT_CONFIG_ERROR", file=sys.stderr)
    except Exception as exception:  # Library/network errors are mapped by Java.
        print(f"TRANSCRIPT_ERROR:{type(exception).__name__}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
