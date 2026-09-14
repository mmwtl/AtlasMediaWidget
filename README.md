<p align="center">
  <img src="docs/images/app-icon.png" width="160" alt="Atlas Media Widget Icon">
</p>

# [RU] Atlas Media Widget
# [EN] Atlas Media Widget

<p align="center">
  <b>[RU] Медиакарточка-оверлей для портретных автомобильных ГУ на Android 11</b><br>
  <b>[EN] Media overlay card for portrait automotive Android 11 head units</b>
</p>

### [RU]
Atlas Media Widget показывает на домашнем экране обложку, метаданные, прогресс воспроизведения, кнопки управления и переключатель источников. Состояние и команды передаются через Media Bridge `protocol v1`.

- **Стандартный релиз (`integrated`)** — виджет поставляется со встроенным медиабэкендом: рантайм (`:media-runtime`) работает прямо внутри виджета в изолированном фоновом процессе `:media` как приватный сервис. Настройки виджета, медиасервиса, разрешений, каталога радиостанций и диагностики объединены в один экран `MainActivity` из 5 секций. Дублирующий ярлык «Atlas Media API» в лаунчере скрыт, а экран диагностики медиасервиса доступен прямо из виджета.

> Atlas Media Widget использует `TYPE_APPLICATION_OVERLAY`, а не системный `AppWidget`. Карточка отображается только поверх HOME и не блокирует управление остальной частью экрана.

### [EN]
Atlas Media Widget displays album artwork, track metadata, playback progress, playback controls, and an audio source switcher directly on the home screen. State and commands are exchanged via Media Bridge `protocol v1`.

- **Standard release (`integrated`)** — The widget includes the built-in media backend: the runtime (`:media-runtime`) runs directly inside the widget in an isolated `:media` background process as a private service. Card appearance, media backend preferences, permissions, radio catalog, and diagnostics are unified into a 5-section `MainActivity` screen. The redundant "Atlas Media API" launcher icon is removed, and the backend diagnostic screen is launched directly from the widget.

> Atlas Media Widget utilizes `TYPE_APPLICATION_OVERLAY` rather than a standard `AppWidget`. The card is rendered exclusively over the HOME screen without blocking touch events outside its bounds.

---

## [RU] Интерфейс / [EN] User Interface

### [RU]
Карточка работает поверх штатного домашнего экрана и занимает только выделенную ей область. Остальные элементы HOME остаются видимыми и интерактивными.

### [EN]
The overlay operates on top of the stock launcher screen, occupying only its designated viewport. All other launcher elements remain visible and interactive.

<p align="center">
  <a href="docs/images/home-overview.webp">
    <img src="docs/images/home-overview.webp" width="520" alt="Atlas Media Widget on automotive head unit home screen">
  </a>
</p>

<table>
  <tr>
    <th>Bluetooth</th>
    <th>Радио / Radio</th>
    <th>Источники / Sources</th>
  </tr>
  <tr>
    <td>
      <a href="docs/images/media-bluetooth-device.webp">
        <img src="docs/images/media-bluetooth-device.webp" alt="Bluetooth media card with artwork and progress">
      </a>
    </td>
    <td>
      <a href="docs/images/media-radio-device.webp">
        <img src="docs/images/media-radio-device.webp" alt="Radio card with station logo">
      </a>
    </td>
    <td>
      <a href="docs/images/media-sources-device.webp">
        <img src="docs/images/media-sources-device.webp" alt="Source switcher: Bluetooth, Radio, USB, Online">
      </a>
    </td>
  </tr>
</table>

### [RU]
В приложении есть живой предпросмотр и отдельные настройки размера, формата и внешнего вида карточки.

### [EN]
The settings application includes a live preview and granular controls for card sizing, placement, and visual styles.

<p align="center">
  <a href="docs/images/settings-device.webp">
    <img src="docs/images/settings-device.webp" width="520" alt="Atlas Media Widget settings screen with live preview">
  </a>
</p>

---

## [RU] Возможности / [EN] Features

### [RU]
- Источники Bluetooth, Radio, USB и Online;
- Полноразмерная обложка с градиентной подложкой для идеальной читаемости текста;
- Play/Pause, Previous, Next и Seek с учётом возможностей активного источника;
- Локальное плавное обновление прогресса без ежесекундных Binder-запросов;
- Единая медиакарточка с точной настройкой ширины и высоты в пикселях;
- Настройка типографики, отступов, прогресс-бара и панели управления;
- Живой предпросмотр в настройках, использующий тот же `MediaCardView`, что и overlay;
- Единое окно настроек из 5 логических секций («Система и разрешения», «Медиасервис», «Виджет», «Резервная копия», «Диагностика»), с масштабом интерфейса последней карточкой;
- Управление источником звука по умолчанию, задержками автозапуска, действиями при потере источника и переключением на Online перед запуском сессии;
- Управление трансляцией названия и обложки радио на приборную панель Geely OneOS DIM и интервалом watchdog;
- Поддержка встроенного и пользовательского каталога радиостанций с обложками;
- Список лайкнутых радиостанций с обложками, сеткой от 2×2 до 4×4 и прямым переключением внутри карточки;
- Опциональная навигация кнопками назад/вперёд по всем сохранённым или только избранным станциям без поиска по эфиру;
- Открытие активного медиаприложения или штатного экрана Radio, Bluetooth и USB по клику на карточку;
- Отображение только поверх HOME, привязка к выбранному углу, перетаскивание и отключаемые маркеры перемещения;
- Экспорт и импорт настроек в ZIP (`AtlasMediaWidget-backup.zip`), включая прежние JSON-настройки; каталог радио переносится отдельно в ZIP (`stations.csv` и `covers/`), старые архивы совместимы;
- Двухфазный защищённый импорт с персистентным журналом восстановления (`import_journal.json`) для защиты от сбоев питания;
- Полная обратная совместимость со старыми файлами настроек JSON (схемы 1–9);
- Foreground service, автозапуск после загрузки ГУ и восстановление соединения с медиасервисом;
- Явное состояние недоступного сервиса вместо бессрочного показа устаревших данных.

### [EN]
- Bluetooth, Radio, USB, and Online media sources;
- Full-bleed artwork with gradient backdrop for maximum typography legibility;
- Capability-aware Play/Pause, Previous, Next, and Seek controls;
- Local, continuous progress interpolation without per-second IPC polling;
- Single unified media card with pixel-accurate width and height configuration;
- Customization of typography, paddings, progress bar, and playback controls;
- Live preview in settings using the exact same `MediaCardView` component;
- Unified settings interface featuring 5 sections ("System & Permissions", "Media Service", "Widget", "Backup & Restore", "Diagnostics"), with interface scale as the final card;
- Configuration of startup source, startup delays, source-loss behavior, and switching to Online before session playback;
- OneOS DIM cluster broadcast control (radio cover and title) with adaptive watchdog interval adjustment;
- Built-in and custom radio station catalog support with station logo art;
- Favorite stations grid (from 2×2 up to 4×4) with station logos and direct in-card switching;
- Optional next/previous button navigation cycling through saved presets or favorites without ether scanning;
- Tap-to-open shortcuts for active media applications or stock Radio, Bluetooth, and USB screens;
- Automatic visibility management (visible only on HOME), anchor corner alignment, and drag-and-drop support;
- Settings backup ZIP (`AtlasMediaWidget-backup.zip`) for widget and media preferences; separate radio catalog ZIP (`stations.csv` and `covers/`), compatible with existing archives;
- Two-phase crash-safe import protocol with persistent journal (`import_journal.json`) protecting against power loss;
- Full backward compatibility with legacy JSON settings files (schemas 1–9);
- Foreground service with boot auto-start and resilient IPC reconnection with backoff;
- Explicit disconnected/unavailable indicators instead of indefinite stale state presentation.

---

## [RU] Требования / [EN] Requirements

### [RU]
- Android 11 (`compileSdk 36`, `minSdk 30`);
- Портретный автомобильный экран (целевое разрешение — 1440×1920);
- **Стандартно**: встроенный рантайм `integrated` (всё включено в один APK);
- Разрешения для виджета: «Поверх других приложений», «Доступ к истории использования» и «Контроль окон» (специальные возможности);
- Разрешение «Доступ к уведомлениям» для медиасервиса (необходимо для чтения сессий Android-плееров);
- Разрешение на доступ к хранилищу для USB-обложек и импорта радио-каталогов.

### [EN]
- Android 11 (`compileSdk 36`, `minSdk 30`);
- Portrait automotive display (target resolution: 1440×1920);
- **Standard**: `integrated` runtime variant (all-in-one APK);
- Widget permissions: "Display over other apps", "Usage Access", and "Window Control" (Accessibility service);
- "Notification Access" for the media service to observe Android media sessions;
- Storage access permission for USB audio artwork and radio catalog imports.

---

## [RU] Установка и запуск / [EN] Installation & Quick Start

### [RU]
1. **Установите стандартный APK `integrated`**:
   - Он содержит виджет и встроенный медиабэкенд в одном пакете.
   - Второй APK устанавливать не требуется.
2. Откройте **Atlas Media Widget** и в разделе **«Система и разрешения»** предоставьте необходимые доступы:
   - Отображение поверх других окон;
   - Доступ к истории использования;
   - Контроль окон (служба доступности);
   - Доступ к уведомлениям (для чтения сессий плееров);
   - Доступ к файлам и медиа (для USB-музыки и архивов каталогов).
3. Настройте геометрию, отступы и привязку карточки в секции **«Виджет»**.
4. Настройте параметры автозапуска и источников в секции **«Медиасервис»**.
5. Нажмите кнопку **«Запустить виджет»**.
6. При необходимости включите тумблер «Автозапуск после загрузки».

### [EN]
1. **Install the standard `integrated` APK**:
   - Contains both the overlay widget and the media backend in a single package.
   - No secondary APK is required.
2. Launch **Atlas Media Widget** and grant required permissions under the **"System & Permissions"** section:
   - Display over other apps;
   - Usage Access;
   - Window Control (Accessibility service);
   - Notification Access (to observe third-party media players);
   - Storage Access (for USB music and catalog archives).
3. Configure card geometry, padding, and anchor corner in the **"Widget"** section.
4. Configure playback behavior and sources in the **"Media Service"** section.
5. Tap **"Start Widget"**.
6. Enable "Launch on boot" if automatic startup is desired.

---

## [RU] Сборка проекта / [EN] Building the Project

### [RU]
Для сборки требуются JDK 17 и Android SDK 36. Сборка осуществляется с помощью Gradle Wrapper.

```sh
# Сборка единственного поддерживаемого релиза (виджет со встроенным API):
./gradlew assembleRelease
# или целевая задача только для виджета:
./gradlew :app:assembleRelease
```

Собранные файлы:
- **Стандартный виджет (с встроенным API)**: `app/build/outputs/apk/integrated/release/<versionName>[<versionCode>]AtlasMediaWidget-release.apk`

#### Запуск модульных тестов:
```sh
./gradlew testReleaseUnitTest
```

### [EN]
Requires JDK 17 and Android SDK 36. The build is managed via the repository Gradle Wrapper.

```sh
# Build the only supported release (widget with integrated API):
./gradlew assembleRelease
# or target only the widget:
./gradlew :app:assembleRelease
```

Generated artifacts:
- **Standard widget (with integrated API)**: `app/build/outputs/apk/integrated/release/<versionName>[<versionCode>]AtlasMediaWidget-release.apk`

#### Running Unit Tests:
```sh
./gradlew testReleaseUnitTest
```

---

## [RU] Документация / [EN] Documentation

- [🔌 Контракт Media Bridge v1](docs/full-media-bridge.md) — подробная спецификация wire-протокола IPC.
- [📻 Каталог радиостанций](docs/radio-catalog.md) — форматы CSV/ZIP, интеграция обложек и прямой выбор станций.
- [🏛 Архитектурные варианты](docs/architecture-options.md) — обоснование выбора архитектуры и процесса `:media`.
- [🕰 Совместимость с GInputBridge](docs/ginputbridge-api.md) — архивный справочник по устаревшему legacy-протоколу.

---

## [RU] Совместимость / [EN] Compatibility

### [RU]
Основная целевая платформа — портретная головная система Geely OneOS на Android 11 (1440×1920). Поведение системных компонентов OneOS, логика автозапуска и управление питанием могут варьироваться в зависимости от прошивки автомобиля и требуют верификации на реальном головном устройстве.

### [EN]
The primary target environment is a Geely OneOS portrait head unit running Android 11 (1440×1920). Behavior of OEM services, background power management, and boot sequence may differ across firmware releases and should be validated on the physical vehicle hardware.
