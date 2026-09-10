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

- **Стандартный релиз (`integrated`)** — виджет поставляется со встроенным медиабэкендом: рантайм (`:media-runtime`) работает прямо внутри виджета в изолированном фоновом процессе `:media` как приватный сервис. Отдельный ярлык «Atlas Media API» в лаунчере открывает экран настроек и диагностики медиасервиса.
- **Раздельная сборка (при необходимости)** — виджет можно собрать в виде тонкого UI-клиента (`plain`), а медиабэкенд скомпилировать и установить отдельно в виде автономного пакета `Atlas Media API` (`com.mmwtl.atlasmediaapi`) из модуля `:api-app`.

> Atlas Media Widget использует `TYPE_APPLICATION_OVERLAY`, а не системный `AppWidget`. Карточка отображается только поверх HOME и не блокирует управление остальной частью экрана.

### [EN]
Atlas Media Widget displays album artwork, track metadata, playback progress, playback controls, and an audio source switcher directly on the home screen. State and commands are exchanged via Media Bridge `protocol v1`.

- **Standard release (`integrated`)** — The widget includes the built-in media backend: the runtime (`:media-runtime`) runs directly inside the widget in an isolated `:media` background process as a private service. A dedicated "Atlas Media API" launcher icon opens the media service diagnostic and settings screen.
- **Modular build (optional)** — The widget can be built as a thin UI client (`plain`), while the media backend is built and installed separately as the standalone `Atlas Media API` package (`com.mmwtl.atlasmediaapi`) from the `:api-app` module.

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
- Поддержка обложек и названий станций радио из встроенного или автономного Atlas Media API;
- Список лайкнутых радиостанций с обложками, сеткой от 2×2 до 4×4 и прямым переключением внутри карточки;
- Опциональная навигация кнопками назад/вперёд по всем сохранённым или только избранным станциям без поиска по эфиру;
- Открытие активного медиаприложения или штатного экрана Radio, Bluetooth и USB по клику на карточку;
- Отображение только поверх HOME, привязка к выбранному углу, перетаскивание и отключаемые маркеры перемещения;
- Импорт и экспорт настроек в версионированный JSON-файл в папке «Загрузки»;
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
- Radio station names and logo artwork supplied via integrated or standalone Atlas Media API;
- Favorite stations grid (from 2×2 up to 4×4) with station logos and direct in-card switching;
- Optional next/previous button navigation cycling through saved presets or favorites without ether scanning;
- Tap-to-open shortcuts for active media applications or stock Radio, Bluetooth, and USB screens;
- Automatic visibility management (visible only on HOME), anchor corner alignment, and drag-and-drop support;
- Configuration export and import via versioned JSON files in the Downloads folder;
- Foreground service with boot auto-start and resilient IPC reconnection with backoff;
- Explicit disconnected/unavailable indicators instead of indefinite stale state presentation.

---

## [RU] Требования / [EN] Requirements

### [RU]
- Android 11 (`compileSdk 36`, `minSdk 30`);
- Портретный автомобильный экран (целевое разрешение — 1440×1920);
- **Стандартно**: встроенный рантайм `integrated` (всё включено в один APK);
- **При раздельной установке**: установленный пакет `Atlas Media API` (`com.mmwtl.atlasmediaapi`);
- Разрешения для виджета: «Поверх других приложений», «Доступ к истории использования» и «Контроль окон» (специальные возможности);
- Разрешение «Доступ к уведомлениям» для медиасервиса (необходимо для чтения сессий Android-плееров);
- Разрешение на доступ к хранилищу для USB-обложек и импорта радио-каталогов.

### [EN]
- Android 11 (`compileSdk 36`, `minSdk 30`);
- Portrait automotive display (target resolution: 1440×1920);
- **Standard**: `integrated` runtime variant (all-in-one APK);
- **Modular setup**: Installed `Atlas Media API` package (`com.mmwtl.atlasmediaapi`);
- Widget permissions: "Display over other apps", "Usage Access", and "Window Control" (Accessibility service);
- "Notification Access" for the media service to observe Android media sessions;
- Storage access permission for USB audio artwork and radio catalog imports.

---

## [RU] Установка и запуск / [EN] Installation & Quick Start

### [RU]
1. **Установите стандартный APK `integrated`**:
   - Он содержит виджет и встроенный медиабэкенд в одном пакете.
   - Второй APK устанавливать не требуется.
2. Откройте **Atlas Media Widget** и в разделе «Системные разрешения» предоставьте:
   - Отображение поверх других окон;
   - Доступ к истории использования;
   - Контроль окон (служба доступности).
3. В разделе разрешений медиасервиса (или через ярлык «Atlas Media API» в лаунчере) выдайте доступ к уведомлениям и хранилищу.
4. Настройте геометрию, отступы и привязку карточки на экране настроек.
5. Нажмите кнопку **«Запустить»**.
6. При необходимости включите тумблер «Автозапуск после загрузки».

*(При использовании раздельной сборки `plain` сначала установите и настройте отдельный пакет `AtlasMediaApi.apk`, затем установите `plain` APK виджета).*

### [EN]
1. **Install the standard `integrated` APK**:
   - Contains both the overlay widget and the media backend in a single package.
   - No secondary APK is required.
2. Launch **Atlas Media Widget** and grant required system permissions:
   - Display over other apps;
   - Usage Access;
   - Window Control (Accessibility service).
3. Under media permissions (or via the "Atlas Media API" launcher icon), grant Notification Access and Storage access.
4. Configure card geometry, padding, and anchor corner using the live preview.
5. Tap **"Start"**.
6. Enable "Launch on boot" if automatic startup is desired.

*(If choosing the modular `plain` setup, install and configure `AtlasMediaApi.apk` first, then install the `plain` widget APK).*

---

## [RU] Сборка проекта / [EN] Building the Project

### [RU]
Для сборки требуются JDK 17 и Android SDK 36. Сборка осуществляется с помощью Gradle Wrapper.

```sh
# 1. Сборка стандартного релиза (виджет со встроенным API):
./gradlew :app:assembleIntegratedRelease

# 2. Сборка тонкого виджета (без встроенного API):
./gradlew :app:assemblePlainRelease

# 3. Сборка автономного медиасервиса (Atlas Media API APK):
./gradlew :api-app:assembleRelease

# Сборка всех релизных артефактов (виджет + standalone API):
./gradlew assembleRelease
```

Собранные файлы:
- Стандартный виджет: `app/build/outputs/apk/integrated/release/<versionName>[<versionCode>]AtlasMediaWidget-integrated-release.apk`
- Тонкий виджет: `app/build/outputs/apk/plain/release/<versionName>[<versionCode>]AtlasMediaWidget-plain-release.apk`
- Автономный API: `api-app/build/outputs/apk/release/<versionName>[<versionCode>]AtlasMediaApi-release.apk`

#### Запуск модульных тестов:
```sh
./gradlew testReleaseUnitTest
```

### [EN]
Requires JDK 17 and Android SDK 36. The build is managed via the repository Gradle Wrapper.

```sh
# 1. Build standard release (widget with integrated API):
./gradlew :app:assembleIntegratedRelease

# 2. Build thin widget (plain overlay without integrated runtime):
./gradlew :app:assemblePlainRelease

# 3. Build standalone media backend APK (Atlas Media API):
./gradlew :api-app:assembleRelease

# Build all release targets:
./gradlew assembleRelease
```

Generated artifacts:
- Standard widget: `app/build/outputs/apk/integrated/release/<versionName>[<versionCode>]AtlasMediaWidget-integrated-release.apk`
- Plain widget: `app/build/outputs/apk/plain/release/<versionName>[<versionCode>]AtlasMediaWidget-plain-release.apk`
- Standalone API: `api-app/build/outputs/apk/release/<versionName>[<versionCode>]AtlasMediaApi-release.apk`

#### Running Unit Tests:
```sh
./gradlew testReleaseUnitTest
```

---

## [RU] Документация / [EN] Documentation

- [📖 Atlas Media API README](api-app/README.md) — руководство по медиасервису, архитектуре и модулям.
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
