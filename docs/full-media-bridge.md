# [RU] Спецификация Media Bridge между AtlasMediaApi и клиентами
# [EN] Media Bridge Specification between AtlasMediaApi and Clients

---

## 1. Назначение и разделение ответственности / Purpose & Responsibilities

### [RU]
`Media Bridge` — это версионированный IPC-контракт (`protocol v1`) на базе Android `Messenger`, связывающий медиабэкенд `AtlasMediaApi` и UI-клиенты (прежде всего `AtlasMediaWidget`).

- **AtlasMediaApi (Бэкенд)**: агрегирует источники OneOS (Radio, Bluetooth, USB, CPAA/CarPlay) и стандартные `MediaSession` Android-плееров; выбирает активную сессию; выполняет команды; нормализует метаданные и обложки; выдаёт временные права чтения на `FileProvider` URI.
- **AtlasMediaWidget (Клиент)**: принимает атомарный `MediaSnapshot`; локально интерполирует позицию воспроизведения без ежесекундного опроса Binder; отображает обложку и элементы управления согласно маске `capabilities`; автоматически переподключается при сбоях.

Исходный код контракта расположен в модуле `:media-core` ([`MediaBridgeContract.kt`](../media-core/src/main/kotlin/com/mmwtl/atlasmediaapi/core/MediaBridgeContract.kt)).

### [EN]
`Media Bridge` is a versioned Android `Messenger` IPC contract (`protocol v1`) connecting the `AtlasMediaApi` backend and UI clients (primarily `AtlasMediaWidget`).

- **AtlasMediaApi (Backend)**: Aggregates OneOS hardware sources (Radio, Bluetooth, USB, CPAA/CarPlay) and standard Android `MediaSession` instances; arbitrates active sessions; routes commands; normalizes metadata and artwork; manages temporary `FileProvider` URI grants.
- **AtlasMediaWidget (Client)**: Consumes atomic `MediaSnapshot` bundles; locally extrapolates progress without per-second IPC polling; renders UI and playback controls matching the capability bitmask; handles automatic reconnection upon Binder death.

The source of truth for the contract is maintained in `:media-core` ([`MediaBridgeContract.kt`](../media-core/src/main/kotlin/com/mmwtl/atlasmediaapi/core/MediaBridgeContract.kt)).

---

## 2. Точки подключения (Endpoints) / Connection Endpoints

### [RU]
Взаимодействие выполняется через explicit Intent для привязки к службе (`bindService`):

| Режим | Пакет (`Package`) | Компонент (`Component`) | Процесс | Экспортирован (`Exported`) | Authority FileProvider |
|---|---|---|---|---|---|
| **Встроенный (`integrated`)** | `com.mmwtl.atlasmediawidget` | `com.mmwtl.atlasmediawidget/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` | `:media` | `false` (приватный) | `com.mmwtl.atlasmediawidget.fileprovider` |
| **Автономный (`standalone`)** | `com.mmwtl.atlasmediaapi` | `com.mmwtl.atlasmediaapi/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` | `:main` | `true` (открытый) | `com.mmwtl.atlasmediaapi.fileprovider` |

- **Action**: `com.mmwtl.atlasmediaapi.media.BIND`
- **Версия протокола**: `1` (`MIN_PROTOCOL_VERSION = 1`, `MAX_PROTOCOL_VERSION = 1`).

### [EN]
Binding is established via explicit Intent (`bindService`):

| Mode | Package | Component | Process | Exported | FileProvider Authority |
|---|---|---|---|---|---|
| **Integrated (`integrated`)** | `com.mmwtl.atlasmediawidget` | `com.mmwtl.atlasmediawidget/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` | `:media` | `false` (private) | `com.mmwtl.atlasmediawidget.fileprovider` |
| **Standalone (`standalone`)** | `com.mmwtl.atlasmediaapi` | `com.mmwtl.atlasmediaapi/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` | `:main` | `true` (open) | `com.mmwtl.atlasmediaapi.fileprovider` |

- **Action**: `com.mmwtl.atlasmediaapi.media.BIND`
- **Protocol Version**: `1` (`MIN_PROTOCOL_VERSION = 1`, `MAX_PROTOCOL_VERSION = 1`).

---

## 3. Сообщения протокола / Protocol Messages

### Клиент → Сервис (Client → Service)

| `Message.what` | Имя / Name | Описание / Description | Поля / Fields |
|---:|---|---|---|
| `1` | `REGISTER` | Регистрация клиента для получения обновлений / Register client for live snapshots | `protocolVersion` (int), optional `requestId` (String), `replyTo` (Messenger) |
| `2` | `UNREGISTER` | Отписка клиента и отзыв выданных URI / Unregister client and revoke URI grants | `protocolVersion` (int), `replyTo` (Messenger) |
| `3` | `GET_SNAPSHOT` | Разовый запрос текущего снимка / One-shot request for current snapshot | `protocolVersion` (int), optional `requestId` (String), `replyTo` (Messenger) |
| `4` | `COMMAND` | Отправка команды воспроизведения или источника / Dispatch playback or source command | `protocolVersion` (int), `requestId` (String), `command` (String), payload, `replyTo` (Messenger) |
| `5` | `GET_RADIO_STATIONS` | Запрос сохранённых и избранных станций / Request saved and favorite radio stations | `protocolVersion` (int), optional `requestId` (String), `replyTo` (Messenger) |

### Сервис → Клиент (Service → Client)

| `Message.what` | Имя / Name | Описание / Description | Поля / Fields |
|---:|---|---|---|
| `100` | `REGISTERED` | Подтверждение успешной регистрации / Successful registration ACK | `protocolVersion`, `status` (0=OK), optional `requestId` |
| `101` | `SNAPSHOT` | Атомарный снимок медиасостояния / Atomic media state snapshot | Flat Bundle со всеми полями снимка (см. ниже) |
| `102` | `COMMAND_RESULT` | Асинхронный результат выполнения команды / Command execution result | `protocolVersion`, `requestId`, `status`, `message`, `generation` |
| `103` | `ERROR` | Ошибка протокола или неподдерживаемая версия / Protocol error or version mismatch | `protocolVersion`, `status`, `message`, optional `requestId` |
| `104` | `RADIO_STATIONS` | Списки радиостанций с обложками / Station lists with artwork | `protocolVersion`, `generation`, `radioSavedStations`, `radioFavoriteStations` |

---

## 4. Коды статусов / Status Codes

| Код / Code | Имя / Constant | Описание / Description |
|---:|---|---|
| `0` | `OK` | Успешно / Success |
| `1` | `INVALID_REQUEST` | Некорректный или повреждённый запрос / Malformed or invalid payload |
| `2` | `UNSUPPORTED_VERSION` | Версия протокола не поддерживается / Unsupported protocol version |
| `3` | `UNAUTHORIZED` | Нет прав доступа / Unauthorized caller |
| `4` | `UNKNOWN_COMMAND` | Неизвестная команда / Unknown command verb |
| `5` | `BACKEND_UNAVAILABLE` | Сервисы OneOS и сессии недоступны / Backend hardware or session unavailable |
| `6` | `NOT_SUPPORTED` | Действие не поддерживается источником / Action not supported by active source |
| `7` | `FAILED` | Сбой выполнения команды / Command execution failed |
| `8` | `NOT_REGISTERED` | Клиент не выполнил предварительный `REGISTER` / Client not registered |

---

## 5. Поля MediaSnapshot Bundle / MediaSnapshot Bundle Fields

`SNAPSHOT` передаётся в виде плоского `Bundle`:

| Поле / Key | Тип / Type | Описание / Description |
|---|---|---|
| `protocolVersion` | `int` | Версия протокола (`1`) |
| `generation` | `long` | Монотонно возрастающий счетчик состояния для защиты от race conditions |
| `timestamp` | `long` | Время публикации снимка (`System.currentTimeMillis()`) |
| `backendConnected` | `boolean` | `true`, если сервис OneOS MediaCenter доступен |
| `backendErrorCode` | `int` | Код ошибки бэкенда (`0` — ок, `1` — подключение, `2` — отключен) |
| `backendErrorMessage` | `String` | Описание ошибки бэкенда |
| `audioSource` | `String` | Активный источник: `UNKNOWN`, `USB`, `BT`, `RADIO`, `ONLINE`, `OTHER`, `YUNTING`, `CPAA` |
| `appSource` | `String` | Вендорный идентификатор источника |
| `sources` | `ArrayList<Bundle>` | Список доступных источников (`id`, `connected`, `available`, `selected`, `capabilities`) |
| `ownerPackage` | `String` | Имя пакета активного приложения/плеера |
| `ownerApp` | `String` | Читаемое название приложения или источника |
| `mediaId` | `String` | Идентификатор трека |
| `title` | `String` | Название трека или радиостанции |
| `artist` | `String` | Исполнитель или диапазон/частота радио |
| `album` | `String` | Название альбома |
| `duration` | `long` | Длительность трека в мс (`-1`, если неизвестна) |
| `position` | `long` | Позиция воспроизведения в мс на момент `updateElapsedRealtime` |
| `updateElapsedRealtime` | `long` | Временная метка `SystemClock.elapsedRealtime()` снятия позиции |
| `speed` | `float` | Скорость воспроизведения (`1.0f` при нормальном воспроизведении) |
| `playbackState` | `int` | Код состояния: `0` (None), `1` (Stopped), `2` (Paused), `3` (Playing), `4` (FastForward), `5` (Rewind), `6` (Buffering), `7` (Error) |
| `capabilities` | `int` | Битовая маска доступных действий источника |
| `artworkUri` | `String` | `FileProvider` URI обложки (`content://...`) |
| `artworkRevision` | `long` | Монотонный счетчик ревизии обложки |

---

## 6. Маска возможностей и команды / Capabilities & Commands

### Битовая маска возможностей (`capabilities`):
- `0x01` (`PLAY`) — возможность начать воспроизведение
- `0x02` (`PAUSE`) — возможность поставить на паузу
- `0x04` (`TOGGLE`) — переключение play/pause
- `0x08` (`NEXT`) — следующий трек или поиск следующей станции
- `0x10` (`PREVIOUS`) — предыдущий трек или поиск предыдущей станции
- `0x20` (`SEEK_TO`) — перемотка по таймлайну (требует аргумент `position: Long >= 0`)
- `0x40` (`SET_SOURCE`) — переключение источника (`source: String`, `autoplay: Boolean`)
- `0x80` (`TUNE_RADIO`) — прямая настройка частоты радио (`radioFrequencyKHz: Int`, `radioBand: String`)

### Локальный расчет прогресса (Local Progress Estimation):
Вместо ежесекундного Binder-опроса клиент вычисляет прогресс локально, если `playbackState == 3` (`PLAYING`):
```java
long elapsed = SystemClock.elapsedRealtime() - updateElapsedRealtime;
long estimatedPosition = position + (long)(elapsed * speed);
if (duration > 0) estimatedPosition = Math.min(estimatedPosition, duration);
```

---

## 7. Передача обложек / Artwork Sharing

1. Бэкенд сжимает изображение до максимального разрешения 512×512 px и сохраняет во внутреннем кэше.
2. URI обложки (`artworkUri`) формируется через `FileProvider` (`${applicationId}.fileprovider`).
3. При отправке клиенту бэкенд вызывает `context.grantUriPermission(...)` для пакета клиента с флагом `Intent.FLAG_GRANT_READ_URI_PERMISSION`.
4. Клиент связывает загрузку обложки с `artworkRevision` и `generation`, предотвращая наложение устаревших асинхронных ответов.
5. При отписке клиента (`UNREGISTER`) или Binder death все выданные URI permissions автоматически отзываются (`context.revokeUriPermission(...)`).
