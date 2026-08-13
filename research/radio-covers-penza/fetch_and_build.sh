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

command -v magick >/dev/null 2>&1 || {
    echo "ImageMagick (magick) is required" >&2
    exit 1
}

# Backgrounds match each brand or the edge colour of an official logo tile. Keeping them solid
# prevents a smaller rectangle or a blurred enlargement from appearing behind the mark.
cover_style() {
    case "$1" in
        094200_radio_rossii.webp) printf '%s\n' '#EAF6FC logo 430' ;;
        094700_radio_vera.webp) printf '%s\n' '#14242D logo 430' ;;
        095200_radio_mayak.webp) printf '%s\n' '#F7F3ED logo 430' ;;
        095600_retro_fm.webp) printf '%s\n' '#171717 logo 440' ;;
        096000_vesti_fm.webp) printf '%s\n' '#F4F5F6 logo 430' ;;
        096400_love_radio.webp) printf '%s\n' '#D8232A logo 440' ;;
        097000_radio_record.webp) printf '%s\n' '#080808 logo 430' ;;
        097500_radio_dacha.webp) printf '%s\n' '#009DD8 full 512' ;;
        098000_radio_gordost.webp) printf '%s\n' '#ED1C24 logo 440' ;;
        098700_like_fm.webp) printf '%s\n' '#EF20BA logo 430' ;;
        099100_detskoe_radio.webp) printf '%s\n' '#FFFFFF logo 455' ;;
        099600_radio_energy.webp) printf '%s\n' '#F4F4F4 logo 440' ;;
        100100_radio_7.webp) printf '%s\n' '#FF641E crop-fixed 512' ;;
        101400_silver_rain.webp) printf '%s\n' '#F6F7F9 logo 440' ;;
        101800_radio_101_8.webp) printf '%s\n' '#FFFFFF full 512' ;;
        102300_avtoradio.webp) printf '%s\n' '#171717 logo 430' ;;
        102800_comedy_radio.webp) printf '%s\n' '#E5133B logo 440' ;;
        103300_rodnoe_radio.webp) printf '%s\n' '#171717 logo 455' ;;
        103800_europa_plus.webp) printf '%s\n' '#FFFFFF logo 435' ;;
        104300_dorozhnoe_radio.webp) printf '%s\n' '#F4F4F2 logo 440' ;;
        104800_russkoe_radio.webp) printf '%s\n' '#E30613 logo 420' ;;
        105200_radio_express.webp) printf '%s\n' '#171717 logo 460' ;;
        105600_novoe_radio.webp) printf '%s\n' '#050505 logo 440' ;;
        106700_yumor_fm.webp) printf '%s\n' '#F4F4F2 logo 470' ;;
        107500_monte_carlo.webp) printf '%s\n' '#061A0E extract-bright 440' ;;
        *) printf '%s\n' '#171717 logo 420' ;;
    esac
}

tail -n +2 "$manifest" | while IFS=, read -r frequency_khz frequency_mhz input_label current_station cover_file source_url source_kind notes; do
    source_url=$(printf '%s' "$source_url" | sed 's/^"//; s/"$//')
    source_path=${source_url%%\?*}
    ext=${source_path##*.}
    original="$originals/${cover_file%.webp}.$ext"
    output="$covers/$cover_file"
    logo="$work_dir/${cover_file%.webp}-logo.png"
    set -- $(cover_style "$cover_file")
    background=$1
    composition=$2
    foreground_size=$3

    case "$source_kind" in
        *-restored|*-prepared) download=0 ;;
        *) download=1 ;;
    esac

    if [ "$skip_download" != "1" ] && [ "$download" = "1" ]; then
        curl -L --fail --silent --show-error --max-time 30 \
            -A 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Safari/537.36' \
            -o "$original" "$source_url"
    elif [ ! -f "$original" ]; then
        echo "Missing cached original: $original" >&2
        exit 1
    fi

    case "$composition" in
        crop-fixed)
            magick "$original" -crop 1080x1080+850+0 +repage -resize 512x512 \
                -strip -quality 92 "$output"
            ;;
        extract-bright)
            magick "$original" \( +clone -colorspace gray -level 22%,48% \) \
                -alpha off -compose CopyOpacity -composite -trim +repage \
                -resize "${foreground_size}x${foreground_size}" "$logo"
            magick -size 512x512 "xc:$background" "$logo" -gravity center -composite \
                -strip -quality 92 "$output"
            ;;
        full)
            magick "$original" -resize '512x512^' -gravity center -extent 512x512 \
                -strip -quality 92 "$output"
            ;;
        logo)
            if [ "$ext" = "svg" ]; then
                magick -background none -density 600 "$original" -trim +repage \
                    -resize "${foreground_size}x${foreground_size}" "$logo"
            else
                magick -background none "$original" -trim +repage \
                    -resize "${foreground_size}x${foreground_size}" "$logo"
            fi
            magick -size 512x512 "xc:$background" "$logo" -gravity center -composite \
                -strip -quality 92 "$output"
            ;;
        *)
            echo "Unknown composition for $cover_file: $composition" >&2
            exit 1
            ;;
    esac
done

set -- "$covers"/*.webp
[ "$#" -eq 25 ] || {
    echo "Expected 25 covers, found $#" >&2
    exit 1
}

magick montage "$covers"/*.webp -tile 5x5 -geometry 256x256+0+0 \
    -background '#171717' -strip -quality 92 "$script_dir/contact-sheet.jpg"
