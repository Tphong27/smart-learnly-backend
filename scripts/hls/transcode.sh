#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 2 || $# -gt 4 ]]; then
    echo "Usage: $0 <input-file> <output-dir> [segment-duration] [qualities-file]" >&2
    exit 64
fi

input_file=$1
output_dir=$2
segment_duration=${3:-10}
qualities_file=${4:-"${output_dir}.qualities"}

if [[ -z "$output_dir" ||
    "$output_dir" != /* ||
    "$output_dir" == "/" ||
    "$output_dir" == *".."* ]]; then
    echo "Refusing to use an unsafe output directory." >&2
    exit 65
fi

if [[ ! -s "$input_file" ]]; then
    echo "Input video does not exist or is empty: $input_file" >&2
    exit 65
fi

if [[ ! "$segment_duration" =~ ^[0-9]+$ ]] ||
    ((segment_duration < 2 || segment_duration > 30)); then
    echo "Segment duration must be an integer between 2 and 30 seconds." >&2
    exit 65
fi

for command_name in ffmpeg ffprobe openssl; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command is unavailable: $command_name" >&2
        exit 69
    fi
done

source_width=$(ffprobe \
    -v error \
    -select_streams v:0 \
    -show_entries stream=width \
    -of csv=p=0 \
    "$input_file")
source_height=$(ffprobe \
    -v error \
    -select_streams v:0 \
    -show_entries stream=height \
    -of csv=p=0 \
    "$input_file")
duration_seconds=$(ffprobe \
    -v error \
    -show_entries format=duration \
    -of csv=p=0 \
    "$input_file")

if [[ ! "$source_width" =~ ^[0-9]+$ ]] ||
    [[ ! "$source_height" =~ ^[0-9]+$ ]] ||
    [[ -z "$duration_seconds" ]]; then
    echo "The uploaded file does not contain a readable video stream." >&2
    exit 65
fi

rm -rf -- "$output_dir"
mkdir -p -- "$output_dir"
mkdir -p -- "$(dirname "$qualities_file")"

key_file="${output_dir}/enc.key"
key_info_file=$(mktemp)
trap 'rm -f -- "$key_info_file"' EXIT

openssl rand 16 >"$key_file"
key_file_for_ffmpeg=$key_file
if command -v cygpath >/dev/null 2>&1; then
    key_file_for_ffmpeg=$(cygpath -w "$key_file")
fi
printf '%s\n%s\n' "../enc.key" "$key_file_for_ffmpeg" >"$key_info_file"

master_playlist="${output_dir}/master.m3u8"
printf '#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-INDEPENDENT-SEGMENTS\n' >"$master_playlist"

quality_names=("480p" "720p" "1080p")
quality_widths=(854 1280 1920)
quality_heights=(480 720 1080)
video_bitrates=(1500 3000 5000)
audio_bitrates=(96 128 192)
generated_qualities=()

encode_variant() {
    local quality_name=$1
    local target_width=$2
    local target_height=$3
    local video_bitrate=$4
    local audio_bitrate=$5
    local video_filter=$6
    local variant_dir="${output_dir}/${quality_name}"
    local bandwidth=$(((video_bitrate + audio_bitrate) * 1000))

    mkdir -p -- "$variant_dir"

    ffmpeg \
        -hide_banner \
        -loglevel warning \
        -y \
        -i "$input_file" \
        -map 0:v:0 \
        -map '0:a:0?' \
        -vf "$video_filter" \
        -c:v libx264 \
        -preset fast \
        -profile:v main \
        -pix_fmt yuv420p \
        -b:v "${video_bitrate}k" \
        -maxrate "$((video_bitrate * 107 / 100))k" \
        -bufsize "$((video_bitrate * 2))k" \
        -c:a aac \
        -b:a "${audio_bitrate}k" \
        -ac 2 \
        -force_key_frames "expr:gte(t,n_forced*${segment_duration})" \
        -hls_time "$segment_duration" \
        -hls_playlist_type vod \
        -hls_flags independent_segments \
        -hls_key_info_file "$key_info_file" \
        -hls_segment_filename "${variant_dir}/segment_%05d.ts" \
        -f hls \
        "${variant_dir}/playlist.m3u8"

    if [[ ! -s "${variant_dir}/playlist.m3u8" ]] ||
        ! compgen -G "${variant_dir}/segment_*.ts" >/dev/null; then
        echo "FFmpeg did not generate a complete ${quality_name} variant." >&2
        exit 70
    fi

    printf '#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d,NAME="%s"\n%s/playlist.m3u8\n' \
        "$bandwidth" \
        "$target_width" \
        "$target_height" \
        "$quality_name" \
        "$quality_name" >>"$master_playlist"

    generated_qualities+=("$quality_name")
}

for index in "${!quality_names[@]}"; do
    quality_name=${quality_names[$index]}
    target_width=${quality_widths[$index]}
    target_height=${quality_heights[$index]}

    if ((source_height < target_height)); then
        continue
    fi

    filter="scale=${target_width}:${target_height}:force_original_aspect_ratio=decrease:force_divisible_by=2"
    filter+=",pad=${target_width}:${target_height}:(ow-iw)/2:(oh-ih)/2"

    encode_variant \
        "$quality_name" \
        "$target_width" \
        "$target_height" \
        "${video_bitrates[$index]}" \
        "${audio_bitrates[$index]}" \
        "$filter"
done

if ((${#generated_qualities[@]} == 0)); then
    even_width=$((source_width - (source_width % 2)))
    even_height=$((source_height - (source_height % 2)))

    encode_variant \
        "source" \
        "$even_width" \
        "$even_height" \
        1200 \
        96 \
        "scale=trunc(iw/2)*2:trunc(ih/2)*2"
fi

printf '%s\n' "${generated_qualities[@]}" >"$qualities_file"

echo "HLS processing completed."
echo "Source: ${source_width}x${source_height}, duration=${duration_seconds}s"
echo "Qualities: ${generated_qualities[*]}"
