# [RU] Каталог радиостанций и обложек
# [EN] Radio Station Catalog & Artwork

---

## 1. Архитектура / Architecture

### [RU] Централизованное управление в AtlasMediaApi
Каталоги радиостанций (встроенный каталог станций Пензы и пользовательский ZIP-импорт) управляются централизованно в сервисе `AtlasMediaApi`. В стандартном варианте `integrated` он входит прямо в состав виджета (модуль `:media-runtime`); при раздельной установке `plain` используется автономный пакет `com.mmwtl.atlasmediaapi` (модуль `:api-app`).

- `AtlasMediaApi` автоматически сопоставляет текущую частоту радиоприёмника с каталогом;
- название станции передаётся в `snapshot.title`;
- диапазон и частота (например, `FM 101.8`) передаются в `snapshot.artist`;
- обложка станции предоставляется через `FileProvider` URI в `snapshot.artworkUri` с инкрементом `snapshot.artworkRevision`;
- виджет `AtlasMediaWidget` отображает полученные данные и декодирует обложку по URI без хранения локальных дубликатов файлов;
- в `integrated` сборке управление каталогом (информация о станциях, сброс к встроенному, импорт и экспорт) доступно прямо из единого экрана настроек `MainActivity` в секции «Медиасервис».

### [EN] Centralized Management in AtlasMediaApi
Radio station catalogs (the built-in Penza catalog and custom ZIP imports) are managed centrally by the `AtlasMediaApi` service. In the standard `integrated` variant, it is compiled directly into the widget package (module `:media-runtime`); in modular `plain` setups, the standalone `com.mmwtl.atlasmediaapi` package (module `:api-app`) is used.

- `AtlasMediaApi` automatically matches the current radio frequency against the active catalog;
- The resolved station name is supplied in `snapshot.title`;
- The band and frequency (e.g., `FM 101.8`) are supplied in `snapshot.artist`;
- Station artwork is provided via a `FileProvider` URI in `snapshot.artworkUri` with an incrementing `snapshot.artworkRevision`;
- `AtlasMediaWidget` renders the incoming metadata and decodes the artwork URI without storing redundant local assets;
- In `integrated` builds, catalog management (active station counts, reset to builtin, import and export) is accessible directly from the unified `MainActivity` under the "Media Service" section.

---

## 2. Структура архива каталога / Catalog Archive Structure

### [RU]
Пользовательский каталог радиостанций может импортироваться как в виде отдельного ZIP-архива, так и в составе единого контейнера полного резервного копирования `AtlasMediaWidget-backup.zip` (в поддиректории `radio/`). Структура каталога:

### [EN]
A custom radio catalog can be imported either as a standalone ZIP archive or as part of the unified full backup container `AtlasMediaWidget-backup.zip` (under the `radio/` directory). Catalog structure:

```text
my-radio.zip (или подкаталог radio/ в AtlasMediaWidget-backup.zip)
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
- **[RU] Атомарный своп и защита от сбоев питания:** при импорте каталог распаковывается во временную директорию `custom_radio_next`, валидируется, после чего текущий каталог переносится в `custom_radio_prev`, а `next` становится активным `custom_radio`. В случае аварийного завершения предыдущий каталог восстанавливается.
  **[EN] Atomic swap and power failure protection:** during import, files are staged to `custom_radio_next` and validated before replacing active data. The existing catalog is backed up to `custom_radio_prev`, and the staged directory becomes active `custom_radio`. If a crash occurs, the previous catalog is restored.
