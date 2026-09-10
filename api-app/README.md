<p align="center">
  <img src="../docs/images/media-api-icon.png" width="160" alt="Atlas Media API Icon">
</p>

# [RU] Atlas Media API
# [EN] Atlas Media API

<p align="center">
  <b>[RU] Медиабэкенд для автомобильных головных устройств Geely OneOS и Android</b><br>
  <b>[EN] Media backend for Geely OneOS and Android automotive head units</b>
</p>

---

## 1. Обзор и назначение / Overview & Purpose

### [RU]
`Atlas Media API` — это медиабэкенд, объединяющий штатные источники Geely OneOS (Радио, Bluetooth, USB, CPAA/CarPlay) со сторонними Android-медиаплеерами (`MediaSession`: Яндекс Музыка, Spotify, VK и др.). Сервис предоставляет единую точку интеграции через Messenger IPC (`protocol v1`) для виджетов и сторонних клиентов.

В проекте реализовано два режима работы:
1. **Стандартный (встроенный в виджет)** — поставляется в составе `AtlasMediaWidget` (сборка `integratedRelease`). Рантайм медиасервиса (`:media-runtime`) работает внутри процесса `:media` пакета `com.mmwtl.atlasmediawidget` как приватная неэкспортируемая служба. Диагностический экран и настройки доступны через отдельную иконку «Atlas Media API» в лаунчере.
2. **Автономный (отдельный APK)** — собирается из данного модуля (`:api-app`) в отдельный пакет `com.mmwtl.atlasmediaapi`. Экспортирует службу `MediaBridgeService` для взаимодействия с тонкими клиентами (сборка `plainRelease` виджета или другие приложения).

### [EN]
`Atlas Media API` is a unified media backend bridging native Geely OneOS sources (Radio, Bluetooth, USB, CPAA/CarPlay) and standard Android `MediaSession` players (Yandex Music, Spotify, VK, etc.). It exposes a single versioned Messenger IPC (`protocol v1`) for widgets and client applications.

The backend operates in two distribution modes:
1. **Standard (Integrated into Widget)** — Delivered inside `AtlasMediaWidget` (`integratedRelease` variant). The media runtime (`:media-runtime`) runs within a private `:media` process inside `com.mmwtl.atlasmediawidget` as a non-exported service. Settings and diagnostics are accessible via a separate "Atlas Media API" launcher icon.
2. **Standalone (Separate APK)** — Built from this module (`:api-app`) as `com.mmwtl.atlasmediaapi`. It exports `MediaBridgeService` over IPC for thin clients (`plainRelease` variant of the widget or external tools).

---

## 2. Модули репозитория / Repository Modules

### [RU]
В данном монорепозитории медиастек разбит на следующие модули:
- `:api-app` — сборка автономного приложения `AtlasMediaApi.apk` (`com.mmwtl.atlasmediaapi`);
- `:media-runtime` — основная реализация: служба `MediaBridgeService`, координатор `MediaBackendCoordinator`, наблюдатель сессий `MediaSessionObserver`, адаптер `OneOsMediaBridgeAdapter`, каталог радио и экран `DiagnosticActivity`;
- `:media-core` — чистые структуры данных, контракт `MediaBridgeContract`, модели снимка (`MediaSnapshot`), источников (`MediaSource`) и маски возможностей (`BridgeCapabilities`);
- `:vendor-oneos` — срез AIDL-интерфейсов и классов взаимодействия с сервисами Geely OneOS (`com.geely.lib.oneosapi`);
- `:vendor-ecarx-stub` — stubs для компиляции DIM-интерфейсов ECARX.

### [EN]
The media backend is organized into the following modules within this monorepo:
- `:api-app` — Standalone application APK builder for `AtlasMediaApi.apk` (`com.mmwtl.atlasmediaapi`);
- `:media-runtime` — Core service implementation: `MediaBridgeService`, `MediaBackendCoordinator`, `MediaSessionObserver`, `OneOsMediaBridgeAdapter`, radio catalog, and `DiagnosticActivity`;
- `:media-core` — Pure data structures, `MediaBridgeContract`, models (`MediaSnapshot`, `MediaSource`), and bitmasks (`BridgeCapabilities`);
- `:vendor-oneos` — Vendor AIDL interfaces and OneOS integration layer (`com.geely.lib.oneosapi`);
- `:vendor-ecarx-stub` — Compile-only stubs for ECARX DIM interaction.

---

## 3. Возможности / Features

### [RU]
- **Единый снимок состояния (`MediaSnapshot`)**: активный источник, метаданные трека (название, исполнитель, альбом), длительность, точная позиция и маска доступных действий.
- **Маршрутизация команд (`MediaCommandRouter`)**: единая обработка Play/Pause, Next/Previous, Seek, смены источника и прямой настройки радио без конфликтов между OneOS и Android `MediaSession`.
- **Централизованный каталог радио**: автоматическое сопоставление частоты со встроенным каталогом Пензы или пользовательскими архивами; передача названия станции и обложки через `FileProvider` URI.
- **Поддержка сохранённых станций**: запрос списка сохранённых и избранных станций OneOS (`GET_RADIO_STATIONS`) и прямая настройка на выбранную станцию (`TUNE_RADIO`) без повторного сканирования диапазона.
- **Безопасная передача обложек**: нормализация (макс. 512 px), кэширование и временная выдача прав чтения `FileProvider` URI для подключённых клиентов с версионированием (`artworkRevision`).
- **Бережный жизненный цикл**: подключение к OneOS Binder только при наличии активных IPC-клиентов; отложенное отключение при разрыве связи; экспоненциальный backoff при сбоях.
- **Demo backend**: полнофункциональная эмуляция состояния и команд для разработки и тестирования на обычном Android-эмуляторе без железа OneOS.
- **Диагностический экран**: мониторинг подключений OneOS, выбор источника по умолчанию, переключение демо-режима, импорт каталогов радио и экспорт отчёта.

### [EN]
- **Unified Media Snapshot (`MediaSnapshot`)**: Active source, track metadata (title, artist, album), duration, playback position, and available capabilities mask.
- **Centralized Command Routing (`MediaCommandRouter`)**: Unified handling for Play/Pause, Next/Previous, Seek, source switching, and radio tuning without collisions between OneOS and Android `MediaSession`.
- **Centralized Radio Catalog**: Automatic frequency resolution against built-in Penza catalog or user ZIP archives; provides station names and artwork via `FileProvider` URIs.
- **Preset & Favorite Radio Support**: Fetching saved and favorite OneOS stations (`GET_RADIO_STATIONS`) and direct tuning (`TUNE_RADIO`) without re-scanning the frequency band.
- **Secure Artwork Distribution**: Downscaling (max 512 px), caching, and temporary `FileProvider` read grants issued to active clients with monotonic revision tracking (`artworkRevision`).
- **Efficient Lifecycle**: Connects to OneOS Binder only when active IPC clients are registered; grace period on client disconnect; bounded exponential backoff on reconnection.
- **Demo Backend**: Full mock state and command pipeline allowing UI development and verification on standard Android emulators without OneOS hardware.
- **Diagnostic Activity**: Inspecting OneOS connection health, configuring default audio sources, toggling demo mode, importing radio ZIP archives, and exporting diagnostic reports.

---

## 4. Архитектура / Architecture

```text
┌────────────────────────────────────────────────────────┐
│                   AtlasMediaWidget                     │
│                (Client / UI Overlay)                   │
└───────────────────────────▲────────────────────────────┘
                            │
                            │ Messenger protocol v1
                            │ Action: com.mmwtl.atlasmediaapi.media.BIND
┌───────────────────────────▼────────────────────────────┐
│                    AtlasMediaApi                       │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │               MediaBridgeService                 │  │
│  │   - Client registration & death recipients       │  │
│  │   - FileProvider URI grants & revocations        │  │
│  │   - Serialized command handling (Mutex)          │  │
│  └──────────────────────────┬───────────────────────┘  │
│                             │                          │
│  ┌──────────────────────────▼───────────────────────┐  │
│  │              MediaBackendCoordinator             │  │
│  │   - Client reference count & 30s grace period    │  │
│  │   - OneOS connection state & reconnect backoff   │  │
│  └───────┬───────────────────────────────┬──────────┘  │
│          │                               │             │
│  ┌───────▼──────────────┐       ┌────────▼──────────┐  │
│  │  MediaStateRepository│       │ MediaCommandRouter│  │
│  │  (Atomic snapshot    │       │ (Source routing   │  │
│  │   state container)   │       │  & fallbacks)     │  │
│  └───────▲──────────────┘       └────────┬──────────┘  │
│          │                               │             │
│  ┌───────┴──────────────┐       ┌────────▼──────────┐  │
│  │     MediaStateHub    │       │AndroidMediaCommand│  │
│  │  (Event reducer,     │       │      Host         │  │
│  │   artwork processor) │       └────────┬──────────┘  │
│  └──▲────────────────▲──┘                │             │
│     │                │                   │             │
│  ┌──┴──────────┐  ┌──┴────────────┐      │             │
│  │OneOsMedia   │  │MediaSession   │◄─────┘             │
│  │BridgeAdapter│  │Observer       │                    │
│  └──┬──────────┘  └──┬────────────┘                    │
└─────┼────────────────┼─────────────────────────────────┘
      │                │
      ▼                ▼
┌──────────────┐ ┌──────────────┐
│ Geely OneOS  │ │Android Media │
│ MediaCenter  │ │Sessions      │
│ (Hardware)   │ │ (Players)    │
└──────────────┘ └──────────────┘
```

---

## 5. Протокол Media Bridge IPC / Media Bridge IPC Protocol

### [RU]
Связь клиента с бэкендом осуществляется через Android `Messenger` с версионированным протоколом `v1`.

| Параметр | Встроенный рантайм (`integrated`) | Автономный сервис (`api-app`) |
|---|---|---|
| **Package** | `com.mmwtl.atlasmediawidget` | `com.mmwtl.atlasmediaapi` |
| **Component** | `com.mmwtl.atlasmediawidget/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` | `com.mmwtl.atlasmediaapi/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` |
| **Action** | `com.mmwtl.atlasmediaapi.media.BIND` | `com.mmwtl.atlasmediaapi.media.BIND` |
| **Process** | `:media` | `:main` |
| **Exported** | `false` (приватный) | `true` (открытый) |
| **Authority**| `com.mmwtl.atlasmediawidget.fileprovider` | `com.mmwtl.atlasmediaapi.fileprovider` |

#### Клиентские команды (`Message.what`):
- `1` (`REGISTER`): подписка на обновления снимков (`replyTo: Messenger`).
- `2` (`UNREGISTER`): отписка и отзыв URI grants.
- `3` (`GET_SNAPSHOT`): запрос разового снимка состояния.
- `4` (`COMMAND`): отправка команды (`command`: `PLAY`, `PAUSE`, `TOGGLE`, `NEXT`, `PREVIOUS`, `SEEK_TO`, `SET_SOURCE`, `TUNE_RADIO`).
- `5` (`GET_RADIO_STATIONS`): запрос сохранённых и избранных станций.

Подробная спецификация протокола описана в [full-media-bridge.md](../docs/full-media-bridge.md).

### [EN]
The client communicates with the backend via Android `Messenger` using versioned protocol `v1`.

| Parameter | Integrated Runtime (`integrated`) | Standalone Service (`api-app`) |
|---|---|---|
| **Package** | `com.mmwtl.atlasmediawidget` | `com.mmwtl.atlasmediaapi` |
| **Component** | `com.mmwtl.atlasmediawidget/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` | `com.mmwtl.atlasmediaapi/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` |
| **Action** | `com.mmwtl.atlasmediaapi.media.BIND` | `com.mmwtl.atlasmediaapi.media.BIND` |
| **Process** | `:media` | `:main` |
| **Exported** | `false` (private) | `true` (open) |
| **Authority**| `com.mmwtl.atlasmediawidget.fileprovider` | `com.mmwtl.atlasmediaapi.fileprovider` |

#### Client Messages (`Message.what`):
- `1` (`REGISTER`): Subscribe for snapshot updates (`replyTo: Messenger`).
- `2` (`UNREGISTER`): Unsubscribe and revoke URI grants.
- `3` (`GET_SNAPSHOT`): Request current state snapshot.
- `4` (`COMMAND`): Send command (`command`: `PLAY`, `PAUSE`, `TOGGLE`, `NEXT`, `PREVIOUS`, `SEEK_TO`, `SET_SOURCE`, `TUNE_RADIO`).
- `5` (`GET_RADIO_STATIONS`): Request preset and favorite radio stations.

Complete wire specification is available in [full-media-bridge.md](../docs/full-media-bridge.md).

---

## 6. Сборка и тестирование / Build & Verification

### [RU]
Для сборки требуются JDK 17 и Android SDK 36.

#### Сборка автономного APK:
```sh
# Debug APK:
./gradlew :api-app:assembleDebug

# Release APK (подписанный при наличии secure.signing.gradle):
./gradlew :api-app:assembleRelease
```
Собранный APK сохраняется в `api-app/build/outputs/apk/release/` с именем вида `<versionName>[<versionCode>]AtlasMediaApi-release.apk`.

#### Запуск модульных тестов:
```sh
./gradlew :api-app:testReleaseUnitTest \
          :media-runtime:testReleaseUnitTest \
          :media-core:testReleaseUnitTest
```

### [EN]
Requires JDK 17 and Android SDK 36.

#### Building Standalone APK:
```sh
# Debug APK:
./gradlew :api-app:assembleDebug

# Release APK (signed when secure.signing.gradle is present):
./gradlew :api-app:assembleRelease
```
The output APK is generated under `api-app/build/outputs/apk/release/` named `<versionName>[<versionCode>]AtlasMediaApi-release.apk`.

#### Running Unit Tests:
```sh
./gradlew :api-app:testReleaseUnitTest \
          :media-runtime:testReleaseUnitTest \
          :media-core:testReleaseUnitTest
```

---

## 7. Разрешения и настройка / Permissions & Setup

### [RU]
Для корректной работы автономного или встроенного сервиса требуются следующие разрешения:
1. **Доступ к уведомлениям (`NotificationListenerService`)** — необходим для отслеживания `MediaSession` Android-плееров (Яндекс Музыка, Spotify и др.).
2. **Доступ к хранилищу (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`)** — требуется для чтения обложек с USB-накопителей и импорта каталогов радиостанций.
3. **Разрешение OneOS (`geely.oneos.permission.SERVICE`)** — для прямого подключения к системному `OneOSApiManager`.

### [EN]
The following permissions are required for proper operation in either standalone or integrated mode:
1. **Notification Access (`NotificationListenerService`)** — Required to observe `MediaSession` from Android media apps (Yandex Music, Spotify, etc.).
2. **Storage Access (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`)** — Required for reading USB audio artwork and importing custom radio catalog ZIPs.
3. **OneOS Permission (`geely.oneos.permission.SERVICE`)** — Required for binding to system `OneOSApiManager`.
