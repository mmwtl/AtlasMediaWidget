# Полный Media Bridge между GInputBridge и AtlasMediaWidget

## Решение

GInputBridge — единственный медиабэкенд. Он выбирает активную сессию, объединяет публичный
`MediaController` с OneOS MediaCenter, определяет доступность источников, исполняет команды и
нормализует обложки. AtlasMediaWidget — тонкий UI-клиент: показывает snapshot, экстраполирует
прогресс, управляет overlay и отправляет команды.

Размещать OneOS Binder, `NotificationListenerService` или вторую логику выбора сессии в
AtlasMediaWidget не надо. Это создаст две конкурирующие callback-цепочки и разные ответы на вопрос,
какой источник сейчас главный.

## Компоненты

### GInputBridge

- `MediaStateHub` собирает MediaSession metadata/playback и состояние OneOS источников в один
  immutable snapshot.
- `MediaCommandRouter` содержит единственную source-aware реализацию transport/source команд. Её
  используют и существующие физические действия GInputBridge, и новый IPC.
- `ArtworkRepository` читает доступные GInputBridge bitmap/URI, уменьшает изображение, кэширует его
  и публикует через `com.salat.gbinder.fileprovider` с временным read grant клиенту.
- `MediaBridgeService` предоставляет аутентифицированный Messenger API, немедленный initial
  snapshot и push-обновления.

### AtlasMediaWidget

- `GInputBridgeMediaSource` явно bind'ится к service, регистрирует reply Messenger и переподключается
  после Binder death с bounded backoff.
- reducer принимает только совместимые snapshots с generation не меньше уже применённого.
- UI включает/отключает кнопки по capability mask; принятая команда остаётся pending до нового
  подтверждающего snapshot либо timeout.
- progress ticker работает только локально и только когда карточка видима и состояние движется.
- overlay service, HOME visibility, положение/размер и настройки принадлежат AtlasMediaWidget.

## Почему Messenger, а не broadcasts или общий AAR

Messenger даёт двусторонний канал, `replyTo`, `Message.sendingUid`, упорядоченную обработку и
Binder-death без общего скомпилированного интерфейса между двумя репозиториями. Payload состоит из
primitive/String/Bundle/ArrayList<Bundle>, а `protocolVersion` защищает от несовместимых релизов.

AIDL также возможен, но потребует синхронно распространять общий contract-модуль либо дублировать
Parcelable/AIDL. Для одного клиента и невысокой частоты событий это лишняя связность. Broadcast API
остаётся для старых интеграций, но не используется для команд полного виджета.

## Wire protocol v1

Клиент делает explicit bind к компоненту GInputBridge. Точное имя service фиксируется после его
реализации; поиск implicit intent запрещён.

Сообщения клиент → service:

| Код | Назначение | Обязательные поля |
|---|---|---|
| `REGISTER_CLIENT` | договориться о версии и подписаться | `minProtocolVersion`, `maxProtocolVersion`, `replyTo` |
| `UNREGISTER_CLIENT` | снять подписку | — |
| `GET_SNAPSHOT` | запросить сверку | `requestId` |
| `EXECUTE_COMMAND` | выполнить действие | `requestId`, `command`, аргументы команды |

Сообщения service → клиент:

| Код | Назначение | Обязательные поля |
|---|---|---|
| `SNAPSHOT` | initial/reconcile/push состояние | snapshot ниже |
| `COMMAND_RESULT` | результат приёма команды | `requestId`, `status`, `message?`, `generation` |
| `PROTOCOL_ERROR` | несовместимая версия или malformed request | `requestId?`, `status`, `message` |

`COMMAND_RESULT=ACCEPTED` означает, что backend принял действие, а не что устройство уже перешло в
желаемое состояние. Источник истины — следующий snapshot/callback. Остальные статусы:
`UNSUPPORTED`, `INVALID_ARGUMENT`, `NOT_READY`, `DENIED`, `FAILED`.

## Snapshot

Верхний уровень:

- `protocolVersion`, монотонный `generation`, `createdAtElapsedRealtime`;
- `backendStatus`: `READY`, `DEGRADED`, `DISCONNECTED`, `ERROR`; `backendError?`;
- `currentAudioSource`, `currentAppSource?`;
- `sources`: `ArrayList<Bundle>`;
- `session`, `playback` и `artwork` как вложенные Bundle.

Source descriptor:

- стабильный `id`: `USB`, `BT`, `RADIO`, `CPAA`, `ONLINE`, `YUNTING`, `OTHER`, `UNKNOWN`;
- `label`, `available`, `connected`, `selected`, `capabilities`;
- optional `reasonUnavailable` для диагностики, но не для постоянного показа в основном UI.

Session:

- `ownerPackage`, `ownerAppName`, `mediaId?`, `title?`, `artist?`, `album?`, `mediaUri?`.

Playback:

- полный Android-compatible `state`, а не boolean;
- `positionMs?`, `durationMs?`, `positionUpdatedAtElapsedRealtime?`, `speed`;
- capability bitmask: `PLAY`, `PAUSE`, `TOGGLE`, `NEXT`, `PREVIOUS`, `SEEK_TO`, `SET_SOURCE`,
  optional `OPEN_PLAYER`.

Artwork:

- `uri?`, `revision`, `width?`, `height?`, `fallbackKind`;
- URI принадлежит FileProvider GInputBridge; Atlas получает `FLAG_GRANT_READ_URI_PERMISSION`;
- полноразмерный Bitmap через Messenger не передаётся из-за Binder transaction limits;
- ответ старой artwork-задачи применяется только при совпадении generation/media ID.

Поля, которых источник не публикует, остаются отсутствующими. `0` не используется как замена
неизвестной duration/position.

## Прогресс

GInputBridge присылает базовую позицию при смене track/state/speed, после seek и при редкой
reconcile-проверке. Во время `PLAYING` AtlasMediaWidget вычисляет:

```text
displayPosition = basePosition
  + (SystemClock.elapsedRealtime() - positionUpdatedAtElapsedRealtime) * speed
```

Результат ограничивается диапазоном `0..duration`. При pause/buffering/error позиция не движется.
Так UI остаётся плавным без IPC или broadcast каждую секунду.

## Команды и маршрутизация

- `PLAY`, `PAUSE`, `TOGGLE`, `NEXT`, `PREVIOUS` идут в существующий source-aware router.
- Для Radio next/previous означают seek следующей/предыдущей станции; для media — смену трека.
- `SEEK_TO(positionMs)` доступен только при соответствующем capability активного backend. Нельзя
  показывать seek только потому, что duration известна.
- `SET_SOURCE(sourceId)` использует OneOS `requestAudioSource`; callback нового current source
  подтверждает завершение.
- UI не пытается fallback'иться на собственный `MediaController`, если команда GInputBridge не
  удалась: это нарушило бы единый выбор backend.

## Безопасность

Проверенные release APK GInputBridge и AtlasAppWidget подписаны разными сертификатами, поэтому
`signature`-permission не защищает эту пару. Для каждого `REGISTER_CLIENT`, `GET_SNAPSHOT` и
`EXECUTE_COMMAND` service обязан:

1. взять реальный `Message.sendingUid`, не UID из Bundle;
2. получить все package этого UID и потребовать точный `com.mmwtl.atlasmediawidget`;
3. проверить SHA-256 signing certificate по production allowlist;
4. отклонить запрос при любой неоднозначности; rate-limit malformed/denied logging.

Production не принимает debug certificate. Если в будущем оба APK будут намеренно подписываться
одним ключом, поверх проверки можно добавить `signature`-permission, но менять ради этого ключ
GInputBridge не требуется.

## Что проверять на ГУ

1. Initial snapshot после cold boot, wake и рестарта каждого приложения по отдельности.
2. Radio, BT, USB, CPAA, online и два сторонних MediaSession-плеера.
3. Наличие/отсутствие каждой transport capability и корректный результат команды.
4. Source availability при подключении/отключении USB, телефона и проекции.
5. Обложку, замену трека, отзыв grants и отсутствие старой картинки после быстрых переключений.
6. Progress до/после pause, seek, buffering и смены speed.
7. Смерть Binder во время команды и восстановление без старого бесконечного состояния.
8. CPU/RAM и отсутствие секундного IPC polling.

## Этапы внедрения

1. GInputBridge: чистые модели, state hub и command router с unit tests.
2. GInputBridge: artwork cache/FileProvider и authenticated Messenger service.
3. AtlasMediaWidget: protocol client, reducer, stale/reconnect и progress tests.
4. AtlasMediaWidget: overlay UI, source selector и controls.
5. Интеграционные тесты на реальной ГУ; legacy broadcasts оставить до успешного soak-test.
