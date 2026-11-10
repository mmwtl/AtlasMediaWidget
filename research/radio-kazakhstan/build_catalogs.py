#!/usr/bin/env python3
"""Build the three reviewed Kazakhstan catalogs from cached artwork and CSVs.

Requires ImageMagick 7. --download refreshes official originals, not restored art.
"""
import argparse
import csv
import hashlib
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--download', action='store_true')
args = parser.parse_args()
HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
ASSETS = REPO / 'docs/assets/radio-packs'
originals = HERE / 'originals'
covers = HERE / 'covers'
covers.mkdir(exist_ok=True)
sources = json.loads((HERE / 'cover-sources.json').read_text())
ET.register_namespace('', 'http://www.w3.org/2000/svg')

with tempfile.TemporaryDirectory(prefix='atlas-kazakhstan-') as work:
    work = Path(work)
    for name, source in sources.items():
        original = originals / source['original']
        if args.download:
            subprocess.run(['curl', '-fLsS', '--max-time', '30', '-A', 'Mozilla/5.0',
                            source['url'], '-o', str(original)], check=True)
        composition = source['composition']
        if composition == 'restored-tile':
            # Retain the restored lettering; replace incidental background texture with solid red.
            mask = work / 'halyq-mask.png'
            logo = work / 'halyq-logo.png'
            subprocess.run(['magick', str(originals / source['prepared']), '-colorspace', 'gray',
                            '-level', '40%,85%', '-resize', '512x512', str(mask)], check=True)
            subprocess.run(['magick', '-size', '512x512', 'xc:white', str(mask), '-alpha', 'off',
                            '-compose', 'CopyOpacity', '-composite', str(logo)], check=True)
            subprocess.run(['magick', '-size', '512x512', 'xc:' + source['background'], str(logo),
                            '-compose', 'over', '-composite', '-strip', '-define', 'webp:lossless=true',
                            str(covers / name)], check=True)
            continue
        if composition in ('classic-mono', 'svg-transparent'):
            # Keep the original paths; remove the source tile's background rectangle.
            tree = ET.fromstring(original.read_text())
            for parent in tree.iter():
                for child in list(parent):
                    if child.tag == '{http://www.w3.org/2000/svg}rect':
                        parent.remove(child)
            if composition == 'classic-mono':
                # The official header uses a white mark. Retain the designer's curves.
                for style in tree.iter('{http://www.w3.org/2000/svg}style'):
                    style.text = style.text.replace('fill:url(#id0)', 'fill:white')
            prepared = work / source['original']
            ET.ElementTree(tree).write(prepared, encoding='utf-8', xml_declaration=True)
            original = prepared
        # Render small SVG viewports with enough samples before reducing to 512px.
        command = ['magick', '-background', 'none']
        if original.suffix == '.svg':
            command += ['-density', '600' if name == 'classic.webp' else '2400']
        command += [str(original)]
        if composition == 'crop':
            # Remove the neutral grey source-tile background without changing the red mark.
            command += ['-crop', source['crop'], '+repage', '-channel', 'RGB', '-fx',
                        '(max(r,max(g,b))-min(r,min(g,b))<0.04&&min(r,min(g,b))>0.72)?1:u', '+channel']
        logo = work / (name + '.png')
        command += ['-trim', '+repage', '-resize', f"{source['size']}x{source['size']}>", str(logo)]
        subprocess.run(command, check=True)
        subprocess.run(['magick', '-size', '512x512', 'xc:' + source['background'], str(logo),
                        '-gravity', 'center', '-composite', '-strip', '-define', 'webp:lossless=true',
                        str(covers / name)], check=True)

subprocess.run(['magick', 'montage', *map(str, sorted(covers.glob('*.webp'))),
                '-tile', '3x2', '-geometry', '256x256+0+0', '-background', '#171717',
                '-strip', '-quality', '92', str(HERE / 'contact-sheet.jpg')], check=True)

# Reuse approved covers byte for byte, including every matching Penza station.
with zipfile.ZipFile(ASSETS / 'stations_kostanay.zip') as z:
    artwork = {Path(n).name: z.read(n) for n in z.namelist() if n.startswith('covers/')}
artwork.update({p.name: p.read_bytes() for p in covers.glob('*.webp')})
artwork['radio_dacha.webp'] = (REPO / 'research/radio-covers-penza/covers/097500_radio_dacha.webp').read_bytes()
with zipfile.ZipFile(ASSETS / 'atlas-radio-6plus.zip') as z:
    artwork['business_fm.webp'] = z.read('covers/biznes_fm.webp')

report = {}
for city in ('astana', 'kokshetau', 'pavlodar'):
    csv_bytes = (HERE / 'cities' / (city + '.csv')).read_bytes()
    rows = list(csv.DictReader(csv_bytes.decode().splitlines()))
    filenames = sorted({row['cover'] for row in rows})
    files = {'stations.csv': csv_bytes, **{'covers/' + n: artwork[n] for n in filenames}}
    archive = ASSETS / f'stations_{city}.zip'
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in files.items():
            info = zipfile.ZipInfo(name, (2026, 11, 10, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)
    report[city] = {'stations': len(rows), 'zip_bytes': archive.stat().st_size,
                    'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()}
print(json.dumps(report, ensure_ascii=False, indent=2))
