# [RU] Каталог радиостанций и обложек
# [EN] Radio Station Catalog & Artwork

---

## 1. Архитектура / Architecture

### [RU] Централизованное управление в AtlasMediaApi
Каталоги радиостанций управляются централизованно в сервисе `AtlasMediaApi`, который входит прямо в единственный поддерживаемый интегрированный Widget APK (модуль `:media-runtime`). Каталогов может быть несколько: встроенный каталог станций Пензы и до 8 импортированных ZIP-каталогов (например, для разных городов). Активен ровно один.

- `AtlasMediaApi` сопоставляет текущую частоту радиоприёмника с активным каталогом;
- название станции передаётся в `snapshot.title`;
- диапазон и частота (например, `FM 101.8`) передаются в `snapshot.artist`;
- обложка станции предоставляется через `FileProvider` URI в `snapshot.artworkUri` с инкрементом `snapshot.artworkRevision`;
- при переключении каталога название и обложка текущей станции пересчитываются сразу; обложка из прежнего каталога не переиспользуется, даже если частота та же;
- виджет `AtlasMediaWidget` отображает полученные данные и декодирует обложку по URI без хранения локальных дубликатов файлов;
- управление каталогами доступно на вкладке «Радио» экрана `MainActivity` (карточка «Каталоги станций»): нажатие на каталог делает его активным, меню «⋯» — экспорт, замена из ZIP, переименование и удаление; «Добавить каталог (ZIP)» импортирует новый каталог и делает его активным. Встроенный каталог можно только включить и экспортировать.

### [EN] Centralized Management in AtlasMediaApi
Radio station catalogs are managed centrally by the `AtlasMediaApi` service, compiled directly into the only supported integrated Widget APK (module `:media-runtime`). There can be several catalogs: the built-in Penza catalog and up to 8 imported ZIP catalogs (for example, one per city). Exactly one is active.

- `AtlasMediaApi` matches the current radio frequency against the active catalog;
- The resolved station name is supplied in `snapshot.title`;
- The band and frequency (e.g., `FM 101.8`) are supplied in `snapshot.artist`;
- Station artwork is provided via a `FileProvider` URI in `snapshot.artworkUri` with an incrementing `snapshot.artworkRevision`;
- Switching catalogs re-resolves the current station at once; artwork from the previous catalog is never reused, even for the same frequency;
- `AtlasMediaWidget` renders the incoming metadata and decodes the artwork URI without storing redundant local assets;
- Catalogs are managed on the Radio tab of `MainActivity` ("Station catalogs" card): tapping a catalog activates it, the "⋯" menu exports, replaces from ZIP, renames or deletes it, and "Add catalog (ZIP)" imports a new active catalog. The built-in catalog can only be activated and exported.

### [RU] Хранение и общие обложки
Каталоги хранятся в `filesDir/radio_catalogs`: файл `index.json` (AtomicFile) содержит названия, порядок и активный каталог, а каждый каталог — свою папку `<id>/` с `stations.csv` и `covers.json` (имя обложки из архива → сохранённый файл). Все обложки лежат один раз в `filesDir/radio_covers/<sha256>.<ext>`: одинаковые по содержимому обложки разных каталогов (одна и та же станция на разных частотах в разных городах) не дублируются. Совпадение определяется побайтно; одна и та же картинка в разном размере или сжатии хранится дважды. Обложки всех каталогов вместе занимают не больше 128 МБ.

`index.json` — точка фиксации: импорт сначала записывает обложки и папку нового каталога, затем индекс. Папки и обложки, на которые индекс не ссылается, удаляются при запуске, поэтому прерванный импорт, замена или удаление оставляют прежнее состояние. Единственный пользовательский каталог прежних версий (`custom_radio`) один раз переносится как «Мой каталог» и остаётся активным, если был активен.

### [EN] Storage and Shared Covers
Catalogs live in `filesDir/radio_catalogs`: `index.json` (AtomicFile) holds names, order and the active catalog, and each catalog has a `<id>/` directory with `stations.csv` and `covers.json` (archive cover name → stored file). Every cover is stored once in `filesDir/radio_covers/<sha256>.<ext>`, so byte-identical covers shared by several catalogs (the same station on different frequencies in different cities) are not duplicated. Matching is byte-exact; the same picture at another size or compression is stored twice. All catalogs' covers together are limited to 128 MB.

`index.json` is the commit point: an import writes the covers and the new catalog directory first and the index last. Directories and covers the index does not reference are removed at startup, so an interrupted import, replacement or deletion leaves the previous state. The single custom catalog of earlier versions (`custom_radio`) is migrated once as "Мой каталог" and stays active if it was active.

---

## 2. Структура архива каталога / Catalog Archive Structure

### [RU]
Каталоги радиостанций экспортируются и импортируются отдельно от настроек, по одному. Прежние ZIP-архивы остаются совместимыми: `stations.csv` находится в корне, обложки — в `covers/`; экспорт восстанавливает исходные имена обложек. Импорт добавляет новый каталог, а «Заменить из ZIP» полностью заменяет станции и обложки выбранного каталога, без объединения наборов. Импорт настроек полностью заменяет переносимые параметры (отсутствующие параметры получают значения по умолчанию) и не меняет текущий каталог, в том числе при загрузке старой полной копии с секцией `radio/`. Структура каталога:

### [EN]
Radio catalogs are exported and imported separately from settings, one at a time. Existing ZIP archives remain compatible: `stations.csv` at the root and artwork in `covers/`; export restores the original cover names. An import adds a new catalog, and "Replace from ZIP" replaces all stations and covers of the chosen catalog. Settings imports replace portable preferences, defaulting omitted parameters, and preserve the current catalog, including imports of older full backups with a `radio/` section. Catalog structure:

```text
my-radio.zip
├── stations.csv
└── covers
    ├── radio7.webp
    └── local_station.png
```

---

## 3. Формат stations.csv / Format of stations.csv

### [RU]
Файл `stations.csv` кодируется в UTF-8 и содержит четыре обязательных столбца:

### [EN]
The `stations.csv` file must be UTF-8 encoded and contain four mandatory columns:

```csv
frequency_khz,name,band,cover
100100,Радио 7 на семи холмах,FM,radio7.webp
999,Пример AM,AM,
101800,"Моя станция, Пенза",FM,local_station.png
```

- `frequency_khz`:
  - **[RU]** для FM — от `87500` до `108000` кГц, для AM — от `500` до `1800` кГц.
  - **[EN]** for FM — `87500` to `108000` kHz, for AM — `500` to `1800` kHz.
- `name`:
  - **[RU]** отображаемое название (до 80 символов).
  - **[EN]** display name (up to 80 characters).
- `band`:
  - **[RU]** строго `FM` или `AM`.
  - **[EN]** strictly `FM` or `AM`.
- `cover`:
  - **[RU]** имя файла из папки `covers/` либо пустое поле (если обложка не требуется).
  - **[EN]** filename from `covers/` or empty (if no cover is required).

---

## 4. Ограничения и валидация / Constraints & Validation

- **[RU] Форматы изображений:** WebP, PNG, JPEG размером от 32×32 до 4096×4096 пикселей.
  **[EN] Image formats:** WebP, PNG, JPEG with dimensions from 32×32 to 4096×4096 px.
- **[RU] Имена файлов:** только латинские буквы, цифры, точки, дефисы и подчёркивания (`[A-Za-z0-9._-]`). Запрещены пути с обходом каталогов (`..`, абсолютные пути).
  **[EN] Filenames:** alphanumeric, dots, hyphens, and underscores only (`[A-Za-z0-9._-]`). Path traversal patterns (`..`, absolute paths) are strictly rejected.
- **[RU] Лимиты:** до 256 станций, до 300 файлов, суммарный размер распакованного архива — до 64 МБ.
  **[EN] Limits:** up to 256 stations, up to 300 files, uncompressed size up to 64 MB.
- **[RU] Целостность и валидация:** при наличии дублирующихся частот, несуществующих файлов обложек или повреждённых картинок импорт отклоняется целиком до внесения изменений.
  **[EN] Integrity and validation:** duplicate frequencies, missing referenced covers, or unreadable image files cause the entire import to be rejected prior to applying changes.
- **[RU] Лимит каталогов:** до 8 импортированных каталогов; названия до 40 символов, совпадающее название при импорте получает номер, например «Москва (2)».
  **[EN] Catalog limit:** up to 8 imported catalogs; names up to 40 characters, and an imported name that is already taken gets a number such as "Москва (2)".
- **[RU] Защита от сбоев питания:** архив распаковывается и проверяется во временной папке `staging_radio_*`; новый каталог появляется только после атомарной записи `index.json` (см. раздел «Хранение и общие обложки»).
  **[EN] Power failure protection:** the archive is extracted and validated in a temporary `staging_radio_*` directory; the new catalog appears only after `index.json` is written atomically (see "Storage and Shared Covers").
