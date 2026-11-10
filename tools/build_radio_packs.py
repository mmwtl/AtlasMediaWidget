#!/usr/bin/env python3
"""Assemble the supplied catalogs: built-in Penza > city ZIPs > national covers.

Requires Pillow. Does not change source catalogs or application assets.
"""
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

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--sources', type=Path, required=True, help='Folder containing radio_covers_russia and Радио/*.zip')
parser.add_argument('--output', type=Path, required=True, help='Directory for ZIPs and provenance records')
args = parser.parse_args()
REPO = Path(__file__).resolve().parent.parent
ROOT = args.sources.resolve()
OUT = args.output.resolve()
OUT.mkdir(parents=True, exist_ok=True)
BASE = ROOT / 'radio_covers_russia'

def read_csv(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))

def digest(data):
    return hashlib.sha256(data).hexdigest()

def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def write_archive(path, files):
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in files.items():
            info = zipfile.ZipInfo(name, (2026,11,9,0,0,0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)

CYR = dict(zip('абвгдеёжзийклмнопрстуфхцчшщъыьэюя',
               ['a','b','v','g','d','e','yo','zh','z','i','y','k','l','m','n','o','p','r','s','t','u','f','kh','ts','ch','sh','sch','','y','','e','yu','ya']))

def ascii_slug(text):
    text = ''.join(CYR.get(c, c) for c in text.lower())
    text = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode()
    return re.sub('[^a-z0-9]+', '-', text).strip('-')

def city_key(region, city):
    # The hash keeps long and similarly transliterated place names distinct.
    readable = ascii_slug(region + '-' + city)[:29].rstrip('-')
    return readable + '-' + digest((region + '\0' + city).encode())[:10]

NAMES = {
    'mayak': 'Радио Маяк', 'russkoe_radio': 'Русское Радио',
    'evropa': 'Европа Плюс', 'novoe_radio': 'Новое Радио',
    'love_radio': 'Love Radio', 'nrj': 'Радио ENERGY',
    'nashe_radio': 'НАШЕ Радио', 'rekord': 'Радио Record',
    'monte_karlo': 'Radio Monte Carlo', 'kp': 'Радио Комсомольская правда',
    'mir': 'Радио МИР', 'sputnik': 'Радио Sputnik', 'relaks_fm': 'Relax FM',
    'biznes_fm': 'Business FM', 'maksimum': 'Радио MAXIMUM',
    'russkiy_khit': 'Русский Хит', 'na_donu': 'ФМ-на-Дону',
    'kanal_melodiya_voronezh': 'Радио Мелодия — Воронеж',
    'dzhaz': 'Радио JAZZ', 'romantika': 'Радио Romantika',
    'dlya_dvoikh': 'Радио Для Двоих', 'dlya_dvoikh_0f30': 'Радио Для Двоих',
    'rossii_f550': 'Радио России (не подтверждено)',
    'rossii_mayak': 'Не уточнено (Радио России / Маяк)',
    '7_304e': 'Радио 7 (не подтверждено)',
}

def name_key(name):
    return re.sub(r'[^\w]+', '', name.casefold().replace('ё', 'е'))

national = read_csv(BASE / 'stations_national.csv')
station_by_name = {x['name']: x['slug'] for x in national}
display = {x['slug']: NAMES.get(x['slug'], x['name']) for x in national}
source_type = {x['slug']: x['cover_source'] for x in national}
alias = {}
for station in national:
    if '?' in station['name']:
        continue
    for name in [station['name'], display[station['slug']]]:
        alias[name_key(name)] = station['slug']
EXTRA_ALIASES = {
    'Радио NRG':'nrj', 'Радио ЭНЕРДЖИ (NRG)':'nrj', 'Energy (NRJ)':'nrj',
    'Радио ЭНЕРДЖИ':'nrj', 'Радио Энерджи':'nrj', 'Радио Вести FM':'vesti_fm',
    'Вести ФМ':'vesti_fm', 'Радио Рекорд':'rekord', 'Радио DFM':'dfm',
    'Comedy Радио':'comedy_radio', 'Радио Европа Плюс':'evropa',
    'Радио Monte Carlo':'monte_karlo', 'Радио Мир':'mir',
    'Радио России / ГТРК Алтай':'rossii', 'Радио России / Дон-ТР':'rossii',
    'Радио России Калининград':'rossii', 'Радио Жара FM':'zhara_fm',
    'Радио Пи FM':'pi_fm', 'Радио Business FM':'biznes_fm',
    'Радио Мелодия':'kanal_melodiya_voronezh', 'Радио Максимум':'maksimum',
    'Радио Балтик Плюс':'baltik_plyus', 'Радио Русский Хит':'russkiy_khit',
    'Радио Калина Красная':'kalina_krasnaya', 'Комсомольская правда':'kp',
    'ТКР ФМ':'tkr_fm', 'MAXIMUM':'maksimum',
    'Радио Серебряный дождь':'serebryanyy_dozhd',
    'Радио России / ГТРК Пенза':'rossii', 'Радио 7 на семи холмах':'7',
    'Ретро FM Казахстан':'retro_fm', 'Авторадио Казахстан':'avtoradio',
}
alias.update({name_key(name): slug for name, slug in EXTRA_ALIASES.items()})

rows = read_csv(BASE / 'stations_by_city.csv')
PLACE_ALIASES = {
    ('Новосибирская','Краснозерское'):'Краснозёрское',
    ('Саратовская','Пугачев'):'Пугачёв',
    ('Свердловская','Артемовский'):'Артёмовский',
    ('Ставропольский','Зеленокумск'):'Зелёнокумск',
}
catalogs = defaultdict(dict)
assets = defaultdict(list)
origins = {}
changes = []
uncertain = []
excluded_curated = []
for x in rows:
    station = station_by_name[x['name']]
    frequency = int(x['frequency_khz'])
    source = BASE / 'covers' / x['cover']
    assert source.is_file(), source
    assets[station].append((30 if x['cover_source'] != 'generated' else 5, source, x['cover_source']))
    # One conflicting frequency, resolved from the station's official coverage page.
    if (x['region'], x['city'], frequency, station) == ('Магаданская','Омчак',102000,'mayak'):
        changes.append({'action':'remove_conflicting_row','row':x,'reason':'102.0: Импульс FM подтверждён официальной картой вещания','url':'https://impulsfm.ru/?page_id=63'})
        continue
    key = (x['region'], PLACE_ALIASES.get((x['region'],x['city']),x['city']))
    assert frequency not in catalogs[key], (key, frequency)
    catalogs[key][frequency] = station
    if '?' in x['name']:
        uncertain.append({**x, 'output_name':display[station], 'action':'Сохранена пометка неопределённости; обложка не назначена'})

penza = REPO / 'media-runtime/src/main/assets/radio'
cover_aliases = {name_key(name): x['slug'] for x in national if '?' not in x['name']
                 for name in (x['slug'], x['name'], display[x['slug']])}
for x in read_csv(penza / 'stations.csv'):
    station = alias[name_key(x['name'])]
    path = penza / 'covers' / x['cover']
    assert path.is_file(), path
    assets[station].append((100, path, 'penza:built-in'))
    origins[path] = {'source': str(path.relative_to(REPO)), 'source_tier': 'penza'}
    cover_aliases[name_key(re.sub(r'^\d{5,6}_', '', Path(x['cover']).stem))] = station

CURATED = {
    'stations_moscow_v2':('Москва','Москва'),
    'minsk-radio':('Беларусь','Минск'),
    'AtlasMediaWidget_smolensk-radio':('Смоленская','Смоленск'),
    'barnaul-radio-radiobells':('Алтайский','Барнаул'),
    'stations_ryazan':('Рязанская','Рязань'),
    'radio_rostov':('Ростовская','Ростов-на-Дону'),
    'kaliningrad_radio_covers':('Калининградская','Калининград'),
    'voronezh-radio':('Воронежская','Воронеж'),
    'stations_kostanay':('Казахстан','Костанай'),
}
extra_city_covers = []
for folder, place in CURATED.items():
    archive_name = 'stations_ryazan1.zip' if folder == 'stations_ryazan' else folder + '.zip'
    archive = (REPO / 'docs/assets/radio-packs/stations_kostanay.zip'
               if folder == 'stations_kostanay' else ROOT / 'Радио' / archive_name)
    archive_source = str(archive.relative_to(REPO if folder == 'stations_kostanay' else ROOT))
    path = OUT / 'source-city-archives' / folder
    (path / 'covers').mkdir(parents=True, exist_ok=True)
    archive_images = []
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None, archive
        csv_paths = [n for n in z.namelist() if Path(n).name == 'stations.csv']
        assert len(csv_paths) == 1, archive
        csv_path = csv_paths[0]
        (path / 'stations.csv').write_bytes(z.read(csv_path))
        prefix = str(Path(csv_path).parent / 'covers') + '/'
        for entry in z.infolist():
            if entry.is_dir() or not entry.filename.startswith(prefix):
                continue
            filename = entry.filename[len(prefix):]
            assert re.fullmatch(r'[A-Za-z0-9._-]+', filename), filename
            target = path / 'covers' / filename
            target.write_bytes(z.read(entry))
            archive_images.append(target)
            origins[target] = {'source': archive_source + '!/' + entry.filename,
                               'source_tier': 'city_archive',
                               'archive_sha256': digest(archive.read_bytes())}
    city_rows = read_csv(path / 'stations.csv')
    referenced = {x['cover'] for x in city_rows}
    for x in city_rows:
        frequency = int(x['frequency_khz'])
        station = alias.get(name_key(x['name']))
        if place == ('Казахстан','Костанай'):
            # Keep the approved CSV names; shared artwork still resolves by alias.
            station = 'kz_' + ascii_slug(x['name']).replace('-','_')
            display[station] = x['name']
            source_type[station] = 'curated'
            assert frequency not in catalogs[place]
            catalogs[place][frequency] = station
        elif place == ('Беларусь','Минск'):
            # Preserve original catalog identities; artwork is shared by name aliases below.
            if station is None or x['name'] in ('Energy FM','Радио Ретро','Новое Радио','Русское Радио','Радио Шансон'):
                station = 'by_' + ascii_slug(x['name']).replace('-','_')
                display[station] = x['name']
                source_type[station] = 'curated'
            assert frequency not in catalogs[place]
            catalogs[place][frequency] = station
        elif (catalogs[place].get(frequency) != station and
              not (station in ('dlya_dvoikh','dlya_dvoikh_0f30') and
                   catalogs[place].get(frequency) in ('dlya_dvoikh','dlya_dvoikh_0f30'))):
            nearby = [city for (region,city), stations in catalogs.items()
                      if region == place[0] and stations.get(frequency) == station]
            excluded_curated.append({'source':folder,**x,'base_name':display.get(catalogs[place].get(frequency,''),''),
                                     'same_region_matches':nearby,
                                     'reason':('Приоритет частот/географии большой базы; передатчик из соседнего города' if nearby
                                               else 'Несовпадение с большой базой; требуется подтверждение')})
            # A logo is still useful when its transmitter belongs to another city.
            if station is not None:
                assets[station].append((60, path / 'covers' / x['cover'], 'city_archive:' + archive_name))
            continue
        elif place != ('Беларусь','Минск'):
            station = catalogs[place][frequency]
        assets[station].append((60, path / 'covers' / x['cover'], 'city_archive:' + archive_name))
        if not station.startswith('by_'):
            cover_aliases.setdefault(name_key(re.sub(r'^\d{5,6}_', '', Path(x['cover']).stem)), station)
    for image in sorted(archive_images):
        if image.name in referenced:
            continue
        key = name_key(re.sub(r'^\d{5,6}_', '', image.stem))
        station = cover_aliases.get(key)
        extra_city_covers.append({'source': origins[image]['source'], 'station': station})
        if station is not None:
            assets[station].append((60, image, 'city_archive:' + archive_name))

# Full region names for a readable city picker. Original region identity remains in keys.
SPECIAL_REGIONS = {
    'Адыгея':'Республика Адыгея','Алтай':'Республика Алтай',
    'Башкирия':'Республика Башкортостан','Бурятия':'Республика Бурятия',
    'Дагестан':'Республика Дагестан','Еврейская':'Еврейская автономная область',
    'Ингушетия':'Республика Ингушетия','Кабардино-Балкария':'Кабардино-Балкарская Республика',
    'Калмыкия':'Республика Калмыкия','Карачаево-Черкесия':'Карачаево-Черкесская Республика',
    'Карелия':'Республика Карелия','Коми':'Республика Коми','Крым':'Республика Крым',
    'Марий Эл':'Республика Марий Эл','Мордовия':'Республика Мордовия',
    'Ненецкий':'Ненецкий автономный округ','Северная Осетия':'Республика Северная Осетия — Алания',
    'Татарстан':'Республика Татарстан','Тува':'Республика Тыва',
    'Удмуртия':'Удмуртская Республика','Хакасия':'Республика Хакасия',
    'Ханты-Мансийский':'Ханты-Мансийский АО — Югра','Чечня':'Чеченская Республика',
    'Чувашия':'Чувашская Республика','Чукотский':'Чукотский автономный округ',
    'Якутия':'Республика Саха (Якутия)','Ямало-Ненецкий':'Ямало-Ненецкий автономный округ',
    'Москва':'Москва','Санкт-Петербург':'Санкт-Петербург','Севастополь':'Севастополь',
}
KRAIS = set('Алтайский Забайкальский Камчатский Краснодарский Красноярский Пермский Приморский Ставропольский Хабаровский'.split())

def region_name(region):
    return SPECIAL_REGIONS.get(region, region + (' край' if region in KRAIS else ' область'))

# The city picker groups country > region > city. Source regions of the places outside Russia
# name the country; keys keep that source region so selected cities survive the regrouping.
FOREIGN_PLACES = {
    ('Беларусь','Минск'):('Беларусь','Минск'),
    ('Казахстан','Костанай'):('Казахстан','Костанайская область'),
}
COUNTRY_ORDER = ['Россия','Казахстан','Беларусь']

def place_location(region, city):
    if region in {country for country, _ in FOREIGN_PLACES.values()}:
        assert (region, city) in FOREIGN_PLACES, (region, city)
    return FOREIGN_PLACES.get((region, city), ('Россия', region_name(region)))

def place_order(item):
    (region, city), _ = item
    country, name = place_location(region, city)
    return COUNTRY_ORDER.index(country), name.casefold(), city.casefold()

used_stations = set(s for stations in catalogs.values() for s in stations.values())
# Source IDs and country prefixes must not split the same station's artwork pool.
# For example, NRJ/Радио Энерджи and both spellings of Радио Для Двоих share a pool.
cover_identity = {station: alias.get(name_key(display[station]), station) for station in assets}
shared_assets = defaultdict(list)
for station, candidates in assets.items():
    shared_assets[cover_identity[station]].extend(candidates)
cover_for = {}
cover_bytes = {}
provenance = {}
hash_to_name = {}
for station in sorted(used_stations):
    if station in ('rossii_f550','rossii_mayak','7_304e'):
        cover_for[station] = ''
        continue
    # Source priority wins; within one tier prefer native dimensions, then detail/file size.
    candidates = {p:(rank,source) for rank,p,source in shared_assets[cover_identity[station]]}
    def rank_asset(path):
        with Image.open(path) as im:
            return candidates[path][0], min(im.size), im.width * im.height, path.stat().st_size, str(path)
    chosen = max(candidates, key=rank_asset)
    source_sha = digest(chosen.read_bytes())
    cache = OUT / 'normalized-covers'
    cache.mkdir(exist_ok=True)
    cached = cache / (source_sha + '.webp')
    with Image.open(chosen) as im:
        im.load()
        if im.format == 'WEBP' and im.size == (512, 512):
            # Preserve the exact approved asset, especially every built-in Penza cover.
            data = chosen.read_bytes()
        elif cached.is_file():
            data = cached.read_bytes()
        else:
            rgba = im.convert('RGBA')
            thumb = ImageOps.contain(rgba, (512,512), Image.Resampling.LANCZOS)
            canvas = Image.new('RGBA',(512,512),(0,0,0,0))
            canvas.paste(thumb,((512-thumb.width)//2,(512-thumb.height)//2))
            buffer = io.BytesIO()
            canvas.save(buffer, format='WEBP', lossless=True, method=6)
            data = buffer.getvalue()
            cached.write_bytes(data)
    sha = digest(data)
    name = hash_to_name.get(sha)
    if not name:
        name = ascii_slug(cover_identity[station]).replace('-','_') + '.webp'
        assert name not in cover_bytes, name
        hash_to_name[sha] = name
        cover_bytes[name] = data
    cover_for[station] = name
    origin = origins[chosen] if chosen in origins else {
        'source': str(chosen.relative_to(ROOT)), 'source_tier': 'national',
    }
    provenance[station] = {'name':display[station], 'cover':name,
                           'cover_identity':cover_identity[station], **origin,
                           'source_kind':candidates[chosen][1],
                           'chosen_priority':candidates[chosen][0],
                           'available_priorities':sorted({rank for rank, source in candidates.values()}, reverse=True),
                           'source_sha256':source_sha,'output_sha256':sha}

manifest = {'format':'atlas-radio-pack','version':1,'id':'atlas-radio-all',
            'name':'Россия, Казахстан, Беларусь','catalogs':[]}
files = {}
index = []
all_rows = []
for (region,city), stations in sorted(catalogs.items(), key=place_order):
    key = city_key(region,city)
    city_name = 'Санкт-Петербург' if (region,city)==('Санкт-Петербург','СПб') else city
    country, region_title = place_location(region, city)
    entry = {'key':key,'name':city_name,'country':country,'region':region_title,'file':f'cities/{key}.csv'}
    assert all(len(entry[field])<=40 for field in ('name','country','region'))
    manifest['catalogs'].append(entry)
    output = io.StringIO(newline='')
    writer = csv.writer(output, lineterminator='\n')
    writer.writerow(['frequency_khz','name','band','cover'])
    for frequency, station in sorted(stations.items()):
        writer.writerow([frequency, display[station],'FM',cover_for[station]])
        all_rows.append({'country':country,'region':entry['region'],'city':city_name,'key':key,
                         'frequency_khz':frequency,'name':display[station],'band':'FM',
                         'cover':cover_for[station]})
    files[entry['file']] = output.getvalue().encode('utf-8')
    index.append({**entry,'stations':len(stations)})
files = {'pack.json':(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode('utf-8'),
         **files, **{'covers/'+name:data for name,data in sorted(cover_bytes.items())}}
archive = OUT / 'atlas-radio-all.zip'
write_archive(archive, files)

stats = {'catalogs':len(catalogs),'regions':len({r for r,c in catalogs}),
         'countries':dict(Counter(city['country'] for city in manifest['catalogs'])),
         'station_rows':len(all_rows),'station_brands':len(used_stations),
         'covers':len(cover_bytes),'entries':len(files),
         'uncompressed_bytes':sum(map(len,files.values())), 'zip_bytes':archive.stat().st_size,
         'sha256':digest(archive.read_bytes()),'max_stations_per_city':max(map(len,catalogs.values())),
         'oirt_rows':sum(x['frequency_khz']<87500 for x in all_rows),
         'uncertain_rows':len(uncertain),'text_placeholder_brands':sum(p['source_kind']=='generated' for p in provenance.values()),
         'radio_logo_brands':sum(p['source_kind']=='Radio:visual_identification' for p in provenance.values()),
         'cover_source_tiers':dict(Counter(p['source_tier'] for p in provenance.values())),
         'unique_cover_source_tiers':{tier:len({p['cover'] for p in provenance.values() if p['source_tier']==tier})
                                      for tier in ('penza','city_archive','national')},
         'curated_rows_not_merged':len(excluded_curated),
         'source_national_rows':len(rows),'source_city_region_pairs':len({(x['region'],x['city']) for x in rows}),
         'catalogs_with_only_oirt':sum(all(f<87500 for f in stations) for stations in catalogs.values())}
write_json(OUT/'stats.json',stats)
write_json(OUT/'city-index.json',index)
write_json(OUT/'stations-normalized.json',all_rows)
write_json(OUT/'cover-provenance.json',provenance)
write_json(OUT/'data-decisions.json',{'corrections':changes,'uncertain_stations':uncertain,
           'cover_priority':['penza', 'city_archive', 'national'],
           'unranked_Radio_folder':'Not used; only the three user-specified sources participate',
           'extra_city_archive_covers':extra_city_covers,
           'merged_place_spellings':[{'region':r,'from':c,'to':v} for (r,c),v in PLACE_ALIASES.items()],
           'curated_rows_not_merged':excluded_curated,
           'moscow_107_0':{'kept':'Маруся FM','rejected':'KISS FM','url':'https://marus.fm/moscow'},
           'duplicate_smolensk':'_smolensk-radio идентичен AtlasMediaWidget_smolensk-radio; добавлен один раз'})
print(json.dumps(stats,ensure_ascii=False,indent=2))

selected = [city for city in index if city['stations'] >= 6]
selected_keys = {city['key'] for city in selected}
selected_rows = [row for row in all_rows if row['key'] in selected_keys]
selected_covers = {row['cover'] for row in selected_rows if row['cover']}
selected_manifest = {**manifest, 'name': 'Россия, Казахстан, Беларусь — 6+ станций',
                     'catalogs': [city for city in manifest['catalogs'] if city['key'] in selected_keys]}
selected_files = {
    'pack.json': (json.dumps(selected_manifest, ensure_ascii=False, indent=2)+'\n').encode('utf-8'),
    **{city['file']: files[city['file']] for city in selected},
    **{'covers/'+cover: cover_bytes[cover] for cover in sorted(selected_covers)},
}
selected_out = OUT / 'six-plus'
selected_out.mkdir(exist_ok=True)
selected_archive = selected_out / 'atlas-radio-6plus.zip'
write_archive(selected_archive, selected_files)
selected_provenance = {key: value for key, value in provenance.items() if value['cover'] in selected_covers}
selected_stats = {
    'minimum_stations': 6, 'catalogs': len(selected), 'station_rows': len(selected_rows),
    'regions': len({(city['country'], city['region']) for city in selected}),
    'countries': dict(Counter(city['country'] for city in selected)), 'covers': len(selected_covers),
    'entries': len(selected_files), 'zip_bytes': selected_archive.stat().st_size,
    'uncompressed_bytes': sum(map(len, selected_files.values())),
    'min_stations_per_city': min(city['stations'] for city in selected),
    'max_stations_per_city': max(city['stations'] for city in selected),
    'oirt_rows': sum(row['frequency_khz'] < 87500 for row in selected_rows),
    'uncertain_rows': sum(not row['cover'] for row in selected_rows),
    'text_placeholder_brands': sum(p['source_kind']=='generated' for p in selected_provenance.values()),
    'cover_source_tiers': dict(Counter(p['source_tier'] for p in selected_provenance.values())),
    'unique_cover_source_tiers': {tier:len({p['cover'] for p in selected_provenance.values() if p['source_tier']==tier})
                                  for tier in ('penza','city_archive','national')},
    'pack_id': selected_manifest['id'], 'sha256': digest(selected_archive.read_bytes()),
    'source_sha256': stats['sha256'],
}
assert len(selected) == 463 and len(selected_rows) == 6700
assert len(manifest['name']) <= 40 and len(selected_manifest['name']) <= 40
write_json(selected_out/'stats.json', selected_stats)
write_json(selected_out/'city-index.json', selected)
write_json(selected_out/'cover-provenance.json', selected_provenance)
write_json(selected_out/'uncertain-stations.json', [row for row in selected_rows if not row['cover']])
print(json.dumps(selected_stats, ensure_ascii=False, indent=2))
