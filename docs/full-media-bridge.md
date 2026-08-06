# Полный Media Bridge между GInputBridge и AtlasMediaWidget

Документ синхронизирован с реализацией GInputBridge в ветке `mediaapi`, protocol v1. Источник
истины для wire-формата — `GInputBridge/docs/atlas-media-bridge.md` и
`MediaBridgeContract.kt` той же ветки.

## Разделение ответственности

GInputBridge — единственный медиабэкенд: выбирает активную сессию, объединяет `MediaController` с
OneOS MediaCenter, определяет источники, выполняет команды и нормализует обложки.
AtlasMediaWidget — UI-клиент: показывает atomic snapshot, экстраполирует progress, управляет
overlay, переподключается после Binder death и отправляет команды.

OneOS Binder, `NotificationListenerService` и вторая логика выбора сессии в AtlasMediaWidget не
нужны. Они создали бы две конкурирующие callback-цепочки с разным понятием активного источника.

## Binding и модель доверия

Используется explicit bind:

```text
action    = com.salat.gbinder.media.BIND
package   = com.salat.gbinder
component = com.salat.gbinder/com.salat.gbinder.media.bridge.MediaBridgeService
```

Service exported и намеренно открыт: permission, package allowlist и проверка signing certificate
отсутствуют. Любой установленный APK может читать snapshots/artwork и отправлять команды. Это
приемлемо только при принятом условии, что ГУ изолирована и владелец контролирует все установки.

`Message.sendingUid` используется GInputBridge только для получения package names, которым
выдаётся временный read grant на artwork URI. Он не является проверкой доступа.

Оставлять explicit component всё равно нужно: он исключает implicit resolution не того сервиса.
Проверка `protocolVersion` также обязательна: это контроль совместимости, а не безопасность.

## Messenger protocol v1

Каждое client message содержит `protocolVersion: Int = 1`. `replyTo` обязателен для register,
snapshot request и command. Поддерживаемый диапазон GInputBridge сейчас `[1,1]`.

Client → GInputBridge:

| `what` | Имя | Поля |
|---:|---|---|
| 1 | `REGISTER` | `protocolVersion`, optional `requestId`, `replyTo` |
| 2 | `UNREGISTER` | `protocolVersion`, `replyTo` |
| 3 | `GET_SNAPSHOT` | `protocolVersion`, optional `requestId`, `replyTo` |
| 4 | `COMMAND` | `protocolVersion`, непустой `requestId`, `command`, аргументы, `replyTo` |

GInputBridge → client:

| `what` | Имя | Назначение |
|---:|---|---|
| 100 | `REGISTERED` | версия принята; сразу после него приходит initial snapshot |
| 101 | `SNAPSHOT` | initial, push или ответ на `GET_SNAPSHOT` |
| 102 | `COMMAND_RESULT` | результат передачи команды backend |
| 103 | `ERROR` | malformed request или несовместимая версия |

Регистрация идемпотентна для одного reply Binder. Binder death автоматически снимает подписку.

## Atomic snapshot

`SNAPSHOT` — один плоский Bundle, а не несколько независимо обновляемых блоков:

- `protocolVersion`, монотонный `generation`, Unix `timestamp`, optional `requestId`;
- `backendConnected`, `backendErrorCode`, `backendErrorMessage`;
- `audioSource`, `appSource`, `sources: ArrayList<Bundle>`;
- `ownerPackage`, `ownerApp`, `mediaId`, `title`, `artist`, `album`;
- `duration`, `position`, `updateElapsedRealtime`, `speed`;
- `playbackState`, `playbackErrorCode`, `playbackErrorMessage`, `playbackActions`;
- нормализованный `capabilities`;
- GInputBridge-owned `artworkUri` и `artworkRevision`.

Неизвестные `duration` и `position` равны `-1`, а не `0`. `timestamp` использует wall clock;
позиция привязана к монотонному `SystemClock.elapsedRealtime()`.

Каждый source descriptor содержит `id`, `connected`, `available`, `selected`, `capabilities`.
Список v1 всегда содержит `UNKNOWN`, `USB`, `BT`, `RADIO`, `ONLINE`, `OTHER`, `YUNTING`, `CPAA`.
USB/BT/CPAA обновляются OneOS callbacks, а не polling.

## Capabilities и команды

| Bit | Hex | Команда |
|---:|---:|---|
| 0 | `0x01` | `PLAY` |
| 1 | `0x02` | `PAUSE` |
| 2 | `0x04` | `TOGGLE` |
| 3 | `0x08` | `NEXT` |
| 4 | `0x10` | `PREVIOUS` |
| 5 | `0x20` | `SEEK_TO` |
| 6 | `0x40` | `SET_SOURCE` |

Команды без аргументов: `PLAY`, `PAUSE`, `TOGGLE`, `NEXT`, `PREVIOUS`.

`SEEK_TO` передаёт `position: Long >= 0`. Наличие duration не означает наличие capability seek.

`SET_SOURCE` передаёт `source`, optional `appSource` и `autoplay` (default `true`). Повторный выбор
уже активного source идемпотентен. Для CPAA playback-команды возвращают `NOT_SUPPORTED`, потому что
CarPlay владеет media keys; переключение source разрешено.

Для Radio next/previous означают поиск станции, для media — смену трека. AtlasMediaWidget не должен
делать собственный fallback через `MediaController`: hardware keys и IPC уже используют единый
`MediaCommandRouter` GInputBridge.

`COMMAND_RESULT` содержит `requestId`, `status`, `message`, `generation`. Статусы v1:

| Код | Имя |
|---:|---|
| 0 | `OK` |
| 1 | `INVALID_REQUEST` |
| 2 | `UNSUPPORTED_VERSION` |
| 3 | `UNAUTHORIZED` — зарезервирован, открытый v1 не возвращает |
| 4 | `UNKNOWN_COMMAND` |
| 5 | `BACKEND_UNAVAILABLE` |
| 6 | `NOT_SUPPORTED` |
| 7 | `FAILED` |
| 8 | `NOT_REGISTERED` |

`OK` подтверждает передачу команды выбранному backend, но не изменение устройства. UI держит
команду pending до подтверждающего snapshot либо timeout.

## Progress

GInputBridge не отправляет position каждую секунду. Только при `PLAYING` клиент вычисляет:

```text
estimated = position
  + (SystemClock.elapsedRealtime() - updateElapsedRealtime) * speed
```

Если duration известна, результат ограничивается `0..duration`. На pause, buffering и error
позиция не движется. После seek/state/track change приходит новая база; редкий `GET_SNAPSHOT`
возвращает последний OneOS position tick без секундного IPC polling.

## Artwork

GInputBridge читает доступный bitmap/URI, уменьшает максимальную сторону до 512 px, сохраняет JPEG
в private cache и публикует собственный `content://` FileProvider URI. Bitmap через Messenger не
передаётся. При unregister/Binder death URI grant отзывается.

AtlasMediaWidget связывает decode с `artworkRevision` и `generation`: поздний результат старого
трека не должен перезаписать новую или уже очищенную обложку.

## Что проверять на ГУ

1. Open bind/register/read/command и корректный отказ только при несовместимом protocol/формате.
2. Reconnect после рестарта обоих процессов и sleep/wake без двойных callbacks.
3. USB/BT/CPAA connect/disconnect и все source/appSource transitions.
4. Radio/BT/USB/ONLINE/MediaSession transport commands и CPAA `NOT_SUPPORTED`.
5. `SEEK_TO` с capability и без неё.
6. Artwork URI, быструю смену трека и отзыв grant после disconnect.
7. Progress после play/pause/seek и отсутствие ежесекундного Binder traffic.
8. Очистку stale snapshot после OneOS disconnect.

## Этапы AtlasMediaWidget

1. Реализовать Messenger client, protocol parser, generation reducer и reconnect policy.
2. Добавить artwork loader и локальный progress estimator с unit tests.
3. Реализовать overlay, source selector и capability-driven controls.
4. Проверить Android 11 ГУ; legacy broadcasts оставить только как диагностический fallback.
