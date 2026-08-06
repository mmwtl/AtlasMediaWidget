# Текущий legacy-контракт GInputBridge

Контракт проверен по текущему исходному коду соседнего проекта GInputBridge. Он остаётся полезным
для обратной совместимости и диагностики, но недостаточен для полного интерактивного виджета.
Целевой API описан в [full-media-bridge.md](full-media-bridge.md).

## Предварительные настройки GInputBridge

Должны быть включены:

- notification access для `MediaNotificationListenerService`;
- модуль `Медиа runtime`;
- модуль `Runtime внешнего API`;
- `Отправка данных медиасессии`;
- `Широковещательные события`.

Последний переключатель критичен: когда `fullBroadcast=false`, GInputBridge адресует исходящие
media broadcasts пакету MacroDroid, и AtlasMediaWidget их не получит.

## События от GInputBridge

### `com.salat.gbinder.PLAYBACK_STATE`

| Extra | Тип | Значение |
|---|---|---|
| `isPlaying` | `String` | `"1"` — что-то играет, `"0"` — ничего не играет |

Это coarse boolean, а не полный Android `PlaybackState`: нет buffering, position, speed, actions
или error.

### `com.salat.gbinder.PLAYBACK_METADATA`

Все extras передаются как `String`:

| Extra | Назначение |
|---|---|
| `id` | Media ID или вычисленный идентификатор title/artist |
| `packageName` | Пакет выбранной медиасессии |
| `appName` | Отображаемое имя приложения |
| `title` | Название трека/передачи |
| `artist` | Исполнитель |
| `album` | Альбом |
| `uri` | URI трека |
| `coverUri` | URI обложки |

GInputBridge вычисляет `duration`, но текущий broadcast его не передаёт. `coverUri` нельзя считать
читаемым: право GInputBridge/владельца session на URI автоматически не означает право
AtlasMediaWidget.

### `com.salat.gbinder.AUDIO_SOURCE_CHANGED`

| Extra | Тип | Возможные значения |
|---|---|---|
| `source` | `String` | `USB`, `BT`, `RADIO`, `CPAA`, `ONLINE`, `OTHER`, `YUNTING`, `UNKNOWN` |

## Запрос текущего состояния

AtlasMediaWidget отправляет explicit broadcast:

```text
action  = com.salat.gbinder.REQUEST_PLAYBACK_INFO
package = com.salat.gbinder
```

GInputBridge отвечает отдельными `PLAYBACK_STATE` и, если metadata уже известны,
`PLAYBACK_METADATA`. Текущая реализация не отвечает `AUDIO_SOURCE_CHANGED`, поэтому новый процесс
AtlasMediaWidget может не знать source до следующего реального переключения.

Запрос надо делать после регистрации runtime receiver:

- при старте процесса/сервиса;
- после wake;
- при возврате overlay на HOME;
- после обнаруженного восстановления GInputBridge.

Если ответ не пришёл за ограниченный timeout, UI показывает `GInputBridge недоступен`, а не старую
карточку.

## Пробелы текущего API

- Нет version/schema number и атомарного snapshot.
- Нет current source в ответе на `REQUEST_PLAYBACK_INFO`.
- Нет duration, position, speed, Android playback state и supported actions.
- Нет команды play/pause/next/previous для кнопок собственного виджета.
- Нет гарантированно читаемого artwork payload/URI.
- Исходящие broadcasts либо глобальны, либо адресованы MacroDroid; отдельного package-targeted ответа
  AtlasMediaWidget нет.
- `BackgroundTaskReceiver` экспортирован без permission, поэтому команды GInputBridge может
  отправить любое установленное приложение. На закрытой ГУ риск ниже, но контракт всё равно слабый.

## Почему не надо продолжать расширять broadcasts

Добавление ещё одного `MEDIA_SNAPSHOT` broadcast решило бы только часть проблемы. Для полного UI
нужны двусторонние команды, подтверждения, подписка, Binder-death/reconnect и проверка вызывающего
UID. Поэтому целевой транспорт — explicit bound service, а не глобальные broadcasts.

Release APK проверенных проектов подписаны разными сертификатами: GInputBridge имеет SHA-256
`8499551068ecabd0b4af86ce6f95234bd5abf3efc150d8f4f7e6fb0f2f2f6443`, а AtlasAppWidget —
`eaf9f1b2dc55db196b41c2c947964d6809a6c954d8aa7b45ad6d72133af0217e`. Поэтому
`signature`-permission сама по себе не подходит. GInputBridge должен проверять `Message.sendingUid`,
точный package клиента и allowlist SHA-256 сертификатов при регистрации и каждой команде.

Эти digest — не секреты, но production allowlist должен задаваться явно. Debug-сертификат можно
разрешать только в debug-сборке GInputBridge.
