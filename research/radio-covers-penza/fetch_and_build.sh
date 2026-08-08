#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
manifest="$script_dir/manifest.csv"
originals="$script_dir/originals"
covers="$script_dir/covers"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/atlas-radio-covers.XXXXXX")
skip_download=${ATLAS_RADIO_SKIP_DOWNLOAD:-0}

mkdir -p "$originals" "$covers"

cleanup() {
    find "$work_dir" -depth -mindepth 1 -delete
    rmdir "$work_dir"
}
trap cleanup EXIT HUP INT TERM

cover_style() {
    case "$1" in
        094200_radio_rossii.webp) printf '%s\n' '0xF2F8FC solid 410' ;;
        094700_radio_vera.webp) printf '%s\n' '0x14242D blur 424' ;;
        095200_radio_mayak.webp) printf '%s\n' '0xF7F3ED solid 408' ;;
        095600_retro_fm.webp) printf '%s\n' '0x171717 solid 432' ;;
        096000_vesti_fm.webp) printf '%s\n' '0xF4F5F6 solid 408' ;;
        096400_love_radio.webp) printf '%s\n' '0xFFF4F7 solid 420' ;;
        097000_radio_record.webp) printf '%s\n' '0x090909 solid 512' ;;
        097500_radio_dacha.webp) printf '%s\n' '0x159BCD solid 440' ;;
        098000_radio_gordost.webp) printf '%s\n' '0xED1C24 solid 416' ;;
        098700_like_fm.webp) printf '%s\n' '0xEF20BA solid 512' ;;
        099100_detskoe_radio.webp) printf '%s\n' '0xFFF7EA solid 432' ;;
        099600_radio_energy.webp) printf '%s\n' '0xF4F4F4 solid 420' ;;
        100100_radio_7.webp) printf '%s\n' '0xEF5A1F blur 456' ;;
        101400_silver_rain.webp) printf '%s\n' '0xF4F5F7 solid 456' ;;
        101800_radio_101_8.webp) printf '%s\n' '0xF4F8FB solid 512' ;;
        102300_avtoradio.webp) printf '%s\n' '0x171717 solid 438' ;;
        102800_comedy_radio.webp) printf '%s\n' '0x171717 solid 412' ;;
        103300_rodnoe_radio.webp) printf '%s\n' '0x171717 solid 512' ;;
        103800_europa_plus.webp) printf '%s\n' '0xF7F7F7 solid 430' ;;
        104300_dorozhnoe_radio.webp) printf '%s\n' '0xF4F4F2 solid 430' ;;
        104800_russkoe_radio.webp) printf '%s\n' '0xE30613 solid 416' ;;
        105200_radio_express.webp) printf '%s\n' '0x171717 solid 512' ;;
        105600_novoe_radio.webp) printf '%s\n' '0x050505 solid 416' ;;
        106700_yumor_fm.webp) printf '%s\n' '0xF6D500 solid 512' ;;
        107500_monte_carlo.webp) printf '%s\n' '0x07120B blur 456' ;;
        *) printf '%s\n' '0x1D2228 solid 420' ;;
    esac
}

tail -n +2 "$manifest" | while IFS=, read -r frequency_khz frequency_mhz input_label current_station cover_file source_url source_kind notes; do
    source_url=$(printf '%s' "$source_url" | sed 's/^"//; s/"$//')
    ext=${source_url%%\?*}
    ext=${ext##*.}
    original="$originals/${cover_file%.webp}.$ext"
    png="$work_dir/${cover_file%.webp}.png"
    set -- $(cover_style "$cover_file")
    background=$1
    composition=$2
    foreground_size=$3

    if [ "$skip_download" != "1" ]; then
        curl -L --fail --silent --show-error --max-time 30 \
            -A 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Safari/537.36' \
            -o "$original" "$source_url"
    elif [ ! -f "$original" ]; then
        echo "Missing cached original: $original" >&2
        exit 1
    fi

    if [ "$ext" = "svg" ]; then
        preview_dir="$work_dir/${cover_file%.webp}-preview"
        mkdir -p "$preview_dir"
        render_source="$original"
        case "$cover_file" in
            098000_radio_gordost.webp)
                render_source="$work_dir/${cover_file%.webp}-dark.svg"
                cp "$original" "$render_source"
                perl -0pi -e 's/fill="white"/fill="black"/g' "$render_source"
                ;;
            104800_russkoe_radio.webp)
                render_source="$work_dir/${cover_file%.webp}-dark.svg"
                cp "$original" "$render_source"
                perl -0pi -e 's/fill="white"/fill="black"/g' "$render_source"
                ;;
            105600_novoe_radio.webp)
                render_source="$work_dir/${cover_file%.webp}-dark.svg"
                cp "$original" "$render_source"
                perl -0pi -e 's/fill="white"/fill="black"/g' "$render_source"
                ;;
            *) ;;
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
                    -filter_complex "[1:v]crop=$crop,negate,colorkey=black:0.08:0.0,scale=$foreground_size:$foreground_size:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
                    -frames:v 1 "$png"
            else
                ffmpeg -hide_banner -loglevel error -y \
                    -f lavfi -i "color=c=$background:s=512x512" -i "$preview" \
                    -filter_complex "[1:v]crop=$crop,scale=$foreground_size:$foreground_size:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
                    -frames:v 1 "$png"
            fi
        else
            ffmpeg -hide_banner -loglevel error -y \
                -f lavfi -i "color=c=$background:s=512x512" -i "$preview" \
                -filter_complex "[1:v]scale=$foreground_size:$foreground_size:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
                -frames:v 1 "$png"
        fi
    elif [ "$composition" = "blur" ]; then
        ffmpeg -hide_banner -loglevel error -y \
            -i "$original" \
            -filter_complex "[0:v]scale=640:640:force_original_aspect_ratio=increase:flags=lanczos,crop=512:512,gblur=sigma=28,eq=brightness=-0.16:saturation=1.12[bg];[0:v]scale=$foreground_size:$foreground_size:force_original_aspect_ratio=decrease:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
            -frames:v 1 "$png"
    else
        ffmpeg -hide_banner -loglevel error -y \
            -f lavfi -i "color=c=$background:s=512x512" -i "$original" \
            -filter_complex "[1:v]scale=$foreground_size:$foreground_size:force_original_aspect_ratio=decrease:flags=lanczos[fg];[0:v][fg]overlay=(W-w)/2:(H-h)/2:format=auto" \
            -frames:v 1 "$png"
    fi

    cwebp -quiet -q 90 -m 6 "$png" -o "$covers/$cover_file"
done

ffmpeg -hide_banner -loglevel error -y -pattern_type glob -i "$covers/*.webp" \
    -vf 'tile=5x5' -frames:v 1 "$script_dir/contact-sheet.jpg"
