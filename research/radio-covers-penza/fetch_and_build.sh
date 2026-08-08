#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
manifest="$script_dir/manifest.csv"
originals="$script_dir/originals"
covers="$script_dir/covers"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/atlas-radio-covers.XXXXXX")

mkdir -p "$originals" "$covers"

cleanup() {
    find "$work_dir" -depth -mindepth 1 -delete
    rmdir "$work_dir"
}
trap cleanup EXIT HUP INT TERM

tail -n +2 "$manifest" | while IFS=, read -r frequency_khz frequency_mhz input_label current_station cover_file source_url source_kind notes; do
    source_url=$(printf '%s' "$source_url" | sed 's/^"//; s/"$//')
    ext=${source_url%%\?*}
    ext=${ext##*.}
    original="$originals/${cover_file%.webp}.$ext"
    png="$work_dir/${cover_file%.webp}.png"
    background=0x171717

    curl -L --fail --silent --show-error --max-time 30 \
        -A 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Safari/537.36' \
        -o "$original" "$source_url"

    if [ "$ext" = "svg" ]; then
        preview_dir="$work_dir/${cover_file%.webp}-preview"
        mkdir -p "$preview_dir"
        render_source="$original"
        case "$cover_file" in
            098000_radio_gordost.webp)
                background=0xED1C24
                render_source="$work_dir/${cover_file%.webp}-dark.svg"
                cp "$original" "$render_source"
                perl -0pi -e 's/fill="white"/fill="black"/g' "$render_source"
                ;;
            104800_russkoe_radio.webp)
                background=0xE30613
                render_source="$work_dir/${cover_file%.webp}-dark.svg"
                cp "$original" "$render_source"
                perl -0pi -e 's/fill="white"/fill="black"/g' "$render_source"
                ;;
            105600_novoe_radio.webp)
                background=0x050505
                render_source="$work_dir/${cover_file%.webp}-dark.svg"
                cp "$original" "$render_source"
                perl -0pi -e 's/fill="white"/fill="black"/g' "$render_source"
                ;;
            *)
                background=white
                ;;
        esac
        qlmanage -t -s 1024 -o "$preview_dir" "$render_source" >/dev/null
        preview="$preview_dir/$(basename "$render_source").png"
        crop=$(ffmpeg -hide_banner -loglevel info -loop 1 -i "$preview" \
            -vf 'negate,format=gray,cropdetect=limit=0.005:round=2:reset=0' \
            -t 0.2 -f null - 2>&1 | sed -n 's/.*crop=\([0-9:]*\).*/\1/p' | tail -1)
        if [ -n "$crop" ]; then
            if [ "$render_source" != "$original" ]; then
                ffmpeg -hide_banner -loglevel error -y \
                    -f lavfi -i "color=c=$background:s=512x512" -i "$preview" \
                    -filter_complex "[1:v]crop=$crop,negate,colorkey=black:0.08:0.0,scale=416:416:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
                    -frames:v 1 "$png"
            else
                ffmpeg -hide_banner -loglevel error -y \
                    -f lavfi -i "color=c=$background:s=512x512" -i "$preview" \
                    -filter_complex "[1:v]crop=$crop,scale=416:416:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
                    -frames:v 1 "$png"
            fi
        else
            ffmpeg -hide_banner -loglevel error -y \
                -f lavfi -i "color=c=$background:s=512x512" -i "$preview" \
                -filter_complex "[1:v]scale=416:416:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
                -frames:v 1 "$png"
        fi
    else
        ffmpeg -hide_banner -loglevel error -y \
            -f lavfi -i "color=c=$background:s=512x512" -i "$original" \
            -filter_complex "[1:v]scale=512:512:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
            -frames:v 1 "$png"
    fi

    cwebp -quiet -q 90 -m 6 "$png" -o "$covers/$cover_file"
done
