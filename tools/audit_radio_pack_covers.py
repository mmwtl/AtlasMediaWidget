"""Independent checks: raw source names/ZIPs -> expected tiers -> every final row."""
import argparse
import csv
import hashlib
import io
import json
import re
import unicodedata
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from PIL import Image, ImageOps

REPO = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--sources', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True, help='JSON audit report')
args = parser.parse_args()
SOURCE = args.sources.resolve()
OUTPUT = args.output.resolve()
OUTPUT.parent.mkdir(parents=True, exist_ok=True)

def identity(name):
    value = unicodedata.normalize('NFKC', name).casefold().replace('ё','е')
    if '?' in value or 'не подтверждено' in value or 'не уточнено' in value:
        return None
    value = re.sub(r'\bradio\b', 'радио', value)
    value = re.sub(r'^радио(?=[\s.\-]|\d)[\s.\-]*', '', value)
    value = value.replace('фм', 'fm')
    value = ''.join(c for c in value if c.isalnum())
    aliases = {
        'россиигтркпенза':'россии', 'россиигтркалтай':'россии',
        'россиидонтр':'россии', 'россиикалининград':'россии',
        '7насемихолмах':'7', 'энерджи':'energy', 'nrj':'energy',
        'nrg':'energy', 'energynrj':'energy', 'энерджиnrg':'energy',
        'рекорд':'record', 'loveрадио':'loveradio', 'монтекарло':'montecarlo',
        'бизнесfm':'businessfm', 'максимум':'maximum', 'джаз':'jazz',
        'романтика':'romantika', 'релаксfm':'relaxfm',
        'кп':'комсомольскаяправда', 'comedyрадио':'comedy', 'comedyradio':'comedy',
        'спутник':'sputnik',
        'мелодияворонеж':'каналмелодияворонеж', 'мелодия':'каналмелодияворонеж',
    }
    return aliases.get(value, value)

def sha(data): return hashlib.sha256(data).hexdigest()
def rows(data): return list(csv.DictReader(io.StringIO(data.decode('utf-8-sig'))))

candidates = defaultdict(lambda: defaultdict(dict))
stems = {}
def add(name, tier, source, data, stem=None):
    key = identity(name)
    if key is None: return
    candidates[key][tier][sha(data)] = (source, data)
    if stem: stems.setdefault(stem, key)

penza = REPO/'media-runtime/src/main/assets/radio'
for row in rows((penza/'stations.csv').read_bytes()):
    add(row['name'], 3, str(penza/'covers'/row['cover']), (penza/'covers'/row['cover']).read_bytes(),
        re.sub(r'^\d{5,6}_','',Path(row['cover']).stem).casefold())
city_archives = []
for path in sorted((SOURCE/'Радио').glob('*.zip')):
    with zipfile.ZipFile(path) as z:
        csv_path = next(n for n in z.namelist() if Path(n).name=='stations.csv')
        data = rows(z.read(csv_path))
        for row in data:
            member = str(Path(csv_path).parent/'covers'/row['cover'])
            add(row['name'], 2, str(path)+'!/'+member, z.read(member),
                re.sub(r'^\d{5,6}_','',Path(row['cover']).stem).casefold())
        city_archives.append((path,csv_path,{row['cover'] for row in data}))
for row in rows((SOURCE/'radio_covers_russia/stations.csv').read_bytes()):
    path = SOURCE/'radio_covers_russia/covers'/row['cover']
    add(row['name'], 1, str(path), path.read_bytes())
    key=identity(row['name'])
    if key: stems.setdefault(re.sub('[^a-z0-9]','',key),key)
for path,csv_path,referenced in city_archives:
    prefix=str(Path(csv_path).parent/'covers')+'/'
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if not name.startswith(prefix) or Path(name).name in referenced or name.endswith('/'):continue
            stem=re.sub(r'^\d{5,6}_','',Path(name).stem).casefold()
            key=stems.get(stem)
            if key:candidates[key][2][sha(z.read(name))]=(str(path)+'!/'+name,z.read(name))

def normalized_pixels(data):
    with Image.open(io.BytesIO(data)) as image:
        rgba=image.convert('RGBA')
        rgba=ImageOps.contain(rgba,(512,512),Image.Resampling.LANCZOS)
        canvas=Image.new('RGBA',(512,512))
        canvas.paste(rgba,((512-rgba.width)//2,(512-rgba.height)//2))
        return tuple(hashlib.sha256(Image.alpha_composite(Image.new('RGBA',(512,512),(v,v,v,255)),canvas).tobytes()).hexdigest() for v in [0,255])

reports=[]
for filename in ['atlas-radio-all.zip','atlas-radio-6plus.zip']:
    wrong=defaultdict(list);family_covers=defaultdict(set);counts=Counter();checked=0;unknown=[];uncertain=0
    with zipfile.ZipFile(REPO/'docs/assets/radio-packs'/filename) as z:
        manifest=json.loads(z.read('pack.json'))
        matches={}
        for city in manifest['catalogs']:
            for row in rows(z.read(city['file'])):
                key=identity(row['name'])
                if key is None:
                    assert not row['cover'];uncertain+=1;continue
                if key not in candidates:
                    unknown.append({'city':city['name'],**row});continue
                tier=max(candidates[key]);counts[tier]+=1;checked+=1
                cover=row['cover'];data=z.read('covers/'+cover);actual=sha(data)
                family_covers[key].add(actual)
                cache_key=(key,actual)
                if cache_key not in matches:
                    source=candidates[key][tier]
                    # Penza is already 512 WebP and must be copied byte for byte.
                    matches[cache_key]=(actual in source or tier<3 and any(normalized_pixels(data)==normalized_pixels(value[1]) for value in source.values()))
                if not matches[cache_key]:
                    wrong[key].append({'city':city['name'],'region':city['region'],**row,'expected_tier':tier})
    split={key:len(covers) for key,covers in family_covers.items() if len(covers)>1}
    reports.append({'filename':filename,'sha256':sha((REPO/'docs/assets/radio-packs'/filename).read_bytes()),'checked_rows':checked,'uncertain_rows_without_cover':uncertain,'expected_tier_rows':dict(counts),
                    'wrong_priority':dict(wrong),'same_station_multiple_covers':split,'unmatched_names':unknown})
OUTPUT.write_text(json.dumps(reports,ensure_ascii=False,indent=2)+'\n')
for report in reports:
    print(report['filename'],'checked',report['checked_rows'],'mismatched rows',sum(map(len,report['wrong_priority'].values())))
    for key,items in report['wrong_priority'].items():print('WRONG',key,len(items),'examples',[(x['city'],x['name'],x['cover']) for x in items[:3]])
    print('SPLIT',report['same_station_multiple_covers'])
    print('UNKNOWN',report['unmatched_names'][:10], 'count',len(report['unmatched_names']))

if any(r['wrong_priority'] or r['same_station_multiple_covers'] or r['unmatched_names'] for r in reports):
    raise SystemExit(1)
