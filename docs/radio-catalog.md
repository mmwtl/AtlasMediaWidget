# [RU] Каталог радиостанций и обложек
# [EN] Radio Station Catalog & Artwork

---

## 1. Архитектура / Architecture

### [RU] Централизованное управление в AtlasMediaApi
Каталоги радиостанций (встроенный каталог станций Пензы и пользовательский ZIP-импорт) управляются централизованно в сервисе `AtlasMediaApi`. В `integrated` он входит в package Widget; в `plain` и `bundled` используется автономный `com.mmwtl.atlasmediaapi`.

- `AtlasMediaApi` автоматически сопоставляет текущую частоту радиоприёмника с каталогом;
- название станции передаётся в `snapshot.title`;
- диапазон и частота (например, `FM 101.8`) передаются в `snapshot.artist`;
- обложка станции предоставляется через `FileProvider` URI в `snapshot.artworkUri` с инкрементом `snapshot.artworkRevision`;
- виджет `AtlasMediaWidget` отображает полученные данные и декодирует обложку по URI без хранения локальных дубликатов файлов.

### [EN] Centralized Management in AtlasMediaApi
Radio station catalogs (the built-in Penza catalog and custom ZIP imports) are managed centrally by the `AtlasMediaApi` service. The `integrated` variant includes it in the Widget package; `plain` and `bundled` use the standalone `com.mmwtl.atlasmediaapi` package.

- `AtlasMediaApi` automatically matches the current radio frequency against the active catalog;
- The resolved station name is supplied in `snapshot.title`;
- The band and frequency (e.g., `FM 101.8`) are supplied in `snapshot.artist`;
- Station artwork is provided via a `FileProvider` URI in `snapshot.artworkUri` with an incrementing `snapshot.artworkRevision`;
- `AtlasMediaWidget` renders the incoming metadata and decodes the artwork URI without storing redundant local assets.

---

## 2. Структура ZIP-архива каталога / Catalog ZIP Structure

### [RU]
Импорт пользовательского каталога выполняется в настройках медиасервиса (`AtlasMediaApi DiagnosticActivity`). ZIP-архив должен иметь следующую структуру:

### [EN]
Custom catalog import is performed in the `AtlasMediaApi DiagnosticActivity`. The ZIP archive must follow this structure:

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
- **[RU] Имена файлов:** только латинские буквы, цифры, точки, дефисы и подчёркивания (`[A-Za-z0-9._-]`).
  **[EN] Filenames:** alphanumeric, dots, hyphens, and underscores only (`[A-Za-z0-9._-]`).
- **[RU] Лимиты:** до 256 станций, до 300 файлов, суммарный размер распакованного архива — до 64 МБ.
  **[EN] Limits:** up to 256 stations, up to 300 files, uncompressed size up to 64 MB.
- **[RU] Целостность:** при наличии дублирующихся частот или отсутствующих обложек импорт отклоняется целиком.
  **[EN] Integrity:** duplicate frequencies or missing cover files will cause the entire import to be rejected.
