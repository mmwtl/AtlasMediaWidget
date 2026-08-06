# Контракт GInputBridge для AtlasMediaWidget

Контракт проверен по текущему исходному коду соседнего проекта GInputBridge. Он не версионирован,
поэтому изменения actions/extras в GInputBridge должны сопровождаться обновлением этого документа и
contract-тестов AtlasMediaWidget.

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

## Рекомендуемое расширение GInputBridge

До реализации progress/controls лучше добавить один versioned `MEDIA_SNAPSHOT` с:

- `schemaVersion`, `requestId`, `generatedAtElapsedRealtime`;
- audio source, package/app name, media ID, title, artist, album;
- playback state, position, duration, speed и actions;
- artwork через собственный read-granted `content://` URI либо небольшой безопасно ограниченный
  bitmap;
- explicit target package `com.mmwtl.atlasmediawidget`.

Запрос должен возвращать source, playback и metadata одним логическим снимком. Если Atlas-приложения
подписываются одним ключом, broadcast/service желательно защитить signature permission. Иначе нужен
явный allowlist пакета и документированная модель доверия.
