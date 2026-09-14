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

- **Action**: `com.mmwtl.atlasmediaapi.media.BIND`
- **Версия протокола**: `1` (`MIN_PROTOCOL_VERSION = 1`, `MAX_PROTOCOL_VERSION = 1`).

### [EN]
Binding is established via explicit Intent (`bindService`):

| Mode | Package | Component | Process | Exported | FileProvider Authority |
|---|---|---|---|---|---|
| **Integrated (`integrated`)** | `com.mmwtl.atlasmediawidget` | `com.mmwtl.atlasmediawidget/com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService` | `:media` | `false` (private) | `com.mmwtl.atlasmediawidget.fileprovider` |

- **Action**: `com.mmwtl.atlasmediaapi.media.BIND`
- **Protocol Version**: `1` (`MIN_PROTOCOL_VERSION = 1`, `MAX_PROTOCOL_VERSION = 1`).

> **Историческая справка / Historical:** до удаления standalone `:api-app` существовал отдельный
> открытый endpoint `com.mmwtl.atlasmediaapi/.../MediaBridgeService` в процессе `:main`.
> Он больше не собирается и не является поддерживаемой точкой подключения.

---

## 3. Сообщения протокола / Protocol Messages

### Клиент → Сервис (Client → Service)

| `Message.what` | Имя / Name | Описание / Description | Поля / Fields |
|---:|---|---|---|
| `1` | `REGISTER` | Регистрация клиента для получения обновлений / Register client for live snapshots | `protocolVersion` (int), optional `requestId` (String), `replyTo` (Messenger), optional `uiScaleTenths` (int) |
| `2` | `UNREGISTER` | Отписка клиента и отзыв выданных URI / Unregister client and revoke URI grants | `protocolVersion` (int), `replyTo` (Messenger) |
| `3` | `GET_SNAPSHOT` | Разовый запрос текущего снимка / One-shot request for current snapshot | `protocolVersion` (int), optional `requestId` (String), `replyTo` (Messenger) |
| `4` | `COMMAND` | Отправка команды воспроизведения или источника / Dispatch playback or source command | `protocolVersion` (int), `requestId` (String), `command` (String), payload, `replyTo` (Messenger) |
| `5` | `GET_RADIO_STATIONS` | Запрос сохранённых и избранных станций / Request saved and favorite radio stations | `protocolVersion` (int), optional `requestId` (String), `replyTo` (Messenger) |
| `6` | `GET_SETTINGS` | Снимок медианастроек / Media settings snapshot | `protocolVersion`, `requestId`, `replyTo` |
| `7` | `UPDATE_SETTINGS` | Обновить параметры / Update settings | `protocolVersion`, optional `expectedRevision`, поля настроек в плоском Bundle / flat settings fields, `requestId`, `replyTo` |
| `8` | `EXPORT_MEDIA_BACKUP` | Экспорт настроек без радио / Export settings without radio | `protocolVersion`, `fileDescriptor` (write PFD), `requestId`, `replyTo` |
| `9` | `PREPARE_MEDIA_IMPORT` | Проверить и подготовить настройки / Validate and stage settings | `protocolVersion`, `operationId` (UUID), `fileDescriptor` (read PFD), `requestId`, `replyTo` |
| `10` | `COMMIT_MEDIA_IMPORT` | Применить подготовленные настройки / Apply staged settings | `protocolVersion`, `operationId`, `stagingToken`, `requestId`, `replyTo` |
| `11` | `GET_IMPORT_STATUS` | Статус операции / Operation status | `protocolVersion`, `operationId`, `requestId`, `replyTo` |
| `12` | `ABORT_MEDIA_IMPORT` | Удалить подготовленный импорт / Discard staged import | `protocolVersion`, `operationId`, `requestId`, `replyTo` |
| `13` | `RESTORE_DEFAULT_CATALOG` | Восстановить встроенное радио / Restore builtin catalog | `protocolVersion`, `requestId`, `replyTo` |
| `14` | `EXPORT_RADIO_CATALOG` | Экспорт текущих станций и обложек / Export active radio catalog | `protocolVersion`, `fileDescriptor` (write PFD), `requestId`, `replyTo` |
| `15` | `IMPORT_RADIO_CATALOG` | Полная замена радио / Replace radio catalog | `protocolVersion`, `fileDescriptor` (read PFD), `requestId`, `replyTo` |

### Сервис → Клиент (Service → Client)

| `Message.what` | Имя / Name | Описание / Description | Поля / Fields |
|---:|---|---|---|
| `100` | `REGISTERED` | Подтверждение успешной регистрации / Successful registration ACK | `protocolVersion`, `status` (0=OK), optional `requestId` |
| `101` | `SNAPSHOT` | Атомарный снимок медиасостояния / Atomic media state snapshot | Flat Bundle со всеми полями снимка (см. ниже) |
| `102` | `COMMAND_RESULT` | Асинхронный результат выполнения команды / Command execution result | `protocolVersion`, `requestId`, `status`, `message`, `generation` |
| `103` | `ERROR` | Ошибка протокола или неподдерживаемая версия / Protocol error or version mismatch | `protocolVersion`, `status`, `message`, optional `requestId` |
| `104` | `RADIO_STATIONS` | Списки радиостанций с обложками / Station lists with artwork | `protocolVersion`, `generation`, `radioSavedStations`, `radioFavoriteStations` |
| `105` | `SETTINGS` | Снимок настроек / Settings snapshot | `protocolVersion`, `status`, `requestId`, плоские поля снимка / flat snapshot, `settingsRevision` |
| `106` | `SETTINGS_UPDATED` | Настройки сохранены / Settings saved | `protocolVersion`, `status`, `requestId`, flat snapshot with `settingsRevision` |
| `107` | `MEDIA_BACKUP_EXPORTED` | ZIP записан в дескриптор клиента / Client file written | `protocolVersion`, `status`, `requestId` |
| `108` | `MEDIA_IMPORT_PREPARED` | Настройки проверены / Settings staged | `protocolVersion`, `status`, `requestId`, `operationId`, `stagingToken`, `catalogType`, `catalogStationCount`, `importPreview` (warnings) |
| `109` | `MEDIA_IMPORT_COMMITTED` | Настройки применены / Settings applied | `protocolVersion`, `status`, `requestId`, `operationId`, flat snapshot with `settingsRevision` |
| `110` | `MEDIA_IMPORT_STATUS` | Статус операции / Operation state | `protocolVersion`, `status`, `requestId`, `operationId`, `importStatus` |
| `111` | `MEDIA_IMPORT_ABORTED` | Отмена обработана / Abort handled | `protocolVersion`, `status`, `requestId`, `operationId` |
| `112` | `DEFAULT_CATALOG_RESTORED` | Встроенный каталог восстановлен / Builtin catalog restored | `protocolVersion`, `status`, `requestId`, flat snapshot with `settingsRevision` |
| `114` | `RADIO_CATALOG_EXPORTED` | Радио ZIP записан / Radio ZIP written | `protocolVersion`, `status`, `requestId`, `catalogStationCount` |
| `115` | `RADIO_CATALOG_IMPORTED` | Радиокаталог заменён / Radio catalog replaced | `protocolVersion`, `status`, `requestId`, `catalogStationCount`, `settingsRevision` |

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
| `9` | `VALIDATION_ERROR` | Недопустимые диапазоны или форматы данных / Invalid configuration or data ranges |
| `10` | `CONFLICT` | Конфликт версий настроек (устаревшая ревизия) / Settings revision conflict |
| `11` | `IO_ERROR` | Ошибка ввода-вывода при передаче или сохранении файлов / I/O error during file streaming or storage |

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

---

## 8. Настройки и резервное копирование по IPC / IPC Settings & Backup

### [RU] Авторизация и передача файлов

В integrated сервис не экспортирован. Настройки и файловые операции допускают
`message.sendingUid == Process.myUid()` либо пакет приложения среди пакетов UID.

Клиент открывает файл и передаёт `fileDescriptor`: write-PFD при экспорте,
read-PFD при импорте. Сервис обрабатывает поток в IO и закрывает свой дескриптор;
клиент закрывает свою копию после отправки. Ответ экспорта подтверждает запись,
не возвращает pipe. Все ответы содержат `protocolVersion`.

### [RU] Раздельный перенос и восстановление

- Настройки: ZIP `manifest.json` + `media.json`; Widget добавляет `widget.json`.
  Радио экспортируется отдельно в прежнем ZIP `stations.csv` + `covers/`.
- `PREPARE_MEDIA_IMPORT` проверяет архив и сохраняет его в
  `staging_media_import_<operationId>` с AtomicFile-метаданными и токеном.
  `catalogMode`/`radio/` читаются только для совместимости и проверки старых полных
  копий; их наличие не меняет текущий каталог.
- `COMMIT_MEDIA_IMPORT` полностью заменяет переносимые медиапараметры, сбрасывая
  отсутствующие поля к стандартным значениям. Радио и производный масштаб API
  не меняются. Масштаб Widget переносится только через `widget.json` и передаётся
  API при регистрации.
- `GET_IMPORT_STATUS`: `IDLE`, `PREPARED`, `COMMITTING`, `COMMITTED`, `FAILED`.
  `COMMITTING` повторно завершает сохранённую операцию. Abort не откатывает начатый
  commit. Widget хранит отдельный журнал для согласования своей части импорта.
- `IMPORT_RADIO_CATALOG` валидирует отдельный архив до замены всего каталога.
  Старые станции и обложки не объединяются с новыми; настройки сохраняются.

Успешный update подтверждается после синхронного сохранения параметров и ревизии.
Несколько SharedPreferences не являются общей дисковой транзакцией. Журнал импорта
и повтор commit обеспечивают восстановление известных этапов, но не доказывают
атомарность при физическом отключении питания.

### [EN] Authorization, streams and recovery

Settings/file operations require the host UID or host package in the sender UID's
package list in both distributions; no privileged-permission bypass exists.
The client supplies a write PFD for export or read PFD for import, and both sides
close their own descriptor copies. Export replies acknowledge a completed write.
Settings and radio use separate archives. Legacy full backups remain readable and
validated, but their radio section is not applied by settings import. Settings
replace portable fields and default missing fields; Widget owns the exported UI
scale and derives the API scale during registration. Radio import replaces the
entire station/artwork catalog after validation. Staged settings imports use UUIDs,
tokens and persisted metadata; states are `IDLE`, `PREPARED`, `COMMITTING`,
`COMMITTED`, `FAILED`. A successful settings update follows synchronous preference
and revision writes; multiple preference files are not a single disk transaction.
