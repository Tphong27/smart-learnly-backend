# FFmpeg Installation Guide for HLS Video Processing

This guide explains how to install FFmpeg on your server for HLS video processing.

## Server Requirements

- **CPU**: Minimum 2 cores recommended for video encoding
- **RAM**: Minimum 4GB
- **Disk**: Fast SSD recommended for temp processing directory
- **OS**: Ubuntu 20.04+, Debian 11+, or Windows Server 2019+

---

## Installation

### Ubuntu / Debian

```bash
# Update package list
sudo apt update

# Install FFmpeg with all codecs
sudo apt install -y ffmpeg

# Verify installation
ffmpeg -version
```

### Windows Server (for local development or Windows server)

#### Option 1: Download binaries
1. Go to https://ffmpeg.org/download.html#windows-builds
2. Download `ffmpeg-release-essentials.zip`
3. Extract to `C:\ffmpeg`
4. Add `C:\ffmpeg\bin` to PATH

#### Option 2: Winget (Windows 10/11)
```powershell
winget install ffmpeg
```

#### Option 3: Chocolatey
```powershell
choco install ffmpeg
```

### macOS

```bash
# Using Homebrew
brew install ffmpeg

# Verify
ffmpeg -version
```

### Docker

If running Spring Boot in Docker, add FFmpeg to your Dockerfile:

```dockerfile
FROM eclipse-temurin:17-jre

# Install FFmpeg
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

# ... rest of your Dockerfile
```

---

## Required FFmpeg Build Features

Ensure FFmpeg is built with:
- `libx264` (H.264 video codec)
- `libfaac` or `libfdk-aac` (AAC audio codec)
- `hls` muxer

Most pre-built versions include these. Verify with:
```bash
ffmpeg -formats | grep -E "264|AAC|HLS"
ffmpeg -codecs | grep -E "264|AAC"
```

---

## Environment Variables

Add these to your `.env` or deployment configuration:

```bash
# Enable HLS processing
APP_HLS_ENABLED=true

# Generate a secure token secret (32+ characters)
HLS_TOKEN_SECRET=your-secure-random-string-at-least-32-characters
```

## Troubleshooting

### FFmpeg not found
```bash
# Check if FFmpeg is in PATH
which ffmpeg
ffmpeg -version

# If not found, check installation location
ls /usr/bin/ffmpeg
ls /usr/local/bin/ffmpeg
```

### Permission denied on temp directory
```bash
# Check temp directory permissions
ls -la /tmp/hls-processing-*

# Fix permissions if needed
sudo chmod 755 /tmp
```

### Out of memory during encoding
- Reduce concurrent encoding (set `videoProcessingExecutor` pool to 1)
- Use lower quality settings
- Increase server RAM

### Slow encoding
- Use `ultrafast` or `superfast` preset instead of `fast`
- Disable subtitle streams if not needed
- Use faster CRF values (24-26 instead of 23)

---

## Performance Notes

| Quality | Resolution | Bitrate | Encoding Time (relative) |
|---------|------------|---------|--------------------------|
| 480p    | 854x480    | 1.5 Mbps | 1x (fastest)            |
| 720p    | 1280x720   | 3 Mbps  | 1.5x                    |
| 1080p   | 1920x1080  | 5 Mbps  | 2.5x (slowest)          |

For faster processing, reduce qualities to just `720p`.
