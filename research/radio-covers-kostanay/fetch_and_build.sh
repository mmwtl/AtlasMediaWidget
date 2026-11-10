#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
originals="$script_dir/originals"
covers="$script_dir/covers"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/atlas-kostanay-covers.XXXXXX")
skip_download=${ATLAS_RADIO_SKIP_DOWNLOAD:-0}

cleanup() {
    find "$work_dir" -depth -mindepth 1 -delete
    rmdir "$work_dir"
}
trap cleanup EXIT HUP INT TERM

command -v magick >/dev/null 2>&1 || {
    echo "ImageMagick 7 (magick) is required" >&2
    exit 1
}
mkdir -p "$originals" "$covers"

# Same composition rules as ../radio-covers-penza/fetch_and_build.sh:
# a solid brand background, a centred logo and safe margins. Never upscale.
tail -n +2 "$script_dir/manifest.csv" |
while IFS=, read -r cover original source_url background foreground_size composition; do
    input="$originals/$original"
    logo="$work_dir/${cover%.webp}.png"
    if [ "$skip_download" != "1" ]; then
        curl -L --fail --silent --show-error --max-time 30 -A 'Mozilla/5.0' \
            -o "$input" "$source_url"
    fi
    [ -f "$input" ] || { echo "Missing original: $input" >&2; exit 1; }

    case "$composition" in
        flatten-trim)
            # Blend the source tile into its exact background colour before
            # trimming. This removes white padding / the green rounded card.
            magick "$input" -background "$background" -alpha remove -alpha off \
                -fuzz 1% -trim +repage -resize "${foreground_size}x${foreground_size}>" "$logo"
            ;;
        logo)
            magick "$input" -trim +repage \
                -resize "${foreground_size}x${foreground_size}>" "$logo"
            ;;
        *) echo "Unknown composition: $composition" >&2; exit 1 ;;
    esac
    magick -size 512x512 "xc:$background" "$logo" -gravity center -composite \
        -strip -define webp:lossless=true "$covers/$cover"
done

# The built-in Penza cover always wins for a recognised station, byte for byte.
penza="$repo_dir/media-runtime/src/main/assets/radio/covers"
cp "$penza/103800_europa_plus.webp" "$covers/europa_plus.webp"
cp "$penza/096400_love_radio.webp" "$covers/love_radio.webp"
cp "$penza/095600_retro_fm.webp" "$covers/retro_fm.webp"
cp "$penza/102300_avtoradio.webp" "$covers/avtoradio.webp"
cp "$penza/104800_russkoe_radio.webp" "$covers/russkoe_radio.webp"

set -- "$covers"/*.webp
[ "$#" -eq 14 ] || { echo "Expected 14 covers, found $#" >&2; exit 1; }

# Frequency order, matching stations.csv in the import archive.
magick montage \
    "$covers/lux_fm.webp" "$covers/europa_plus.webp" "$covers/alau.webp" \
    "$covers/radio_kn.webp" "$covers/love_radio.webp" "$covers/retro_fm.webp" \
    "$covers/russkoe_radio.webp" "$covers/avtoradio.webp" "$covers/zhuldyz_fm.webp" \
    "$covers/qazaq_radio.webp" "$covers/narodnoe_radio.webp" "$covers/radio_ns.webp" \
    "$covers/shalqar.webp" "$covers/zhana_fm.webp" \
    -tile 4x -geometry 256x256+0+0 -background '#171717' \
    -strip -quality 92 "$script_dir/contact-sheet.jpg"
