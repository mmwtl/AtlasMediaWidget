# [RU] Устаревший контракт GInputBridge (Legacy Reference)
# [EN] Deprecated GInputBridge Contract (Legacy Reference)

> [!WARNING]
> **[RU]** Данный документ сохранён исключительно в качестве архивного справочника ранних прототипов. В актуальной версии `AtlasMediaWidget` используется нативный контракт `AtlasMediaApi Media Bridge v1` ([full-media-bridge.md](full-media-bridge.md)).
> 
> **[EN]** This document is retained solely as an archival reference from early prototype stages. The current `AtlasMediaWidget` implementation exclusively uses the native `AtlasMediaApi Media Bridge v1` protocol ([full-media-bridge.md](full-media-bridge.md)).

---

## 1. Обзор и ограничения / Overview & Limitations

### [RU]
Ранее для чтения медиа-состояния использовались широковещательные интенты (Broadcast Intents) стороннего приложения GInputBridge (`com.salat.gbinder`). Этот подход обладал существенными ограничениями:
- Отсутствие атомарности: метаданные, состояние воспроизведения и источник передавались разными асинхронными сообщениями;
- Отсутствие обратной связи при отправке команд;
- Невозможность гарантированной передачи прав на обложки между процессами;
- Отсутствие поддержки списков радиостанций и прямого переключения станций;
- Отсутствие схемы версионирования.

### [EN]
During early development, media state was received via Broadcast Intents dispatched by GInputBridge (`com.salat.gbinder`). This legacy mechanism had fundamental limitations:
- Lack of atomicity: metadata, playback state, and active audio source arrived in separate unsynchronized broadcasts;
- No command execution feedback;
- Inability to grant reliable, safe cross-process read access to album artwork;
- No support for preset/favorite radio lists or direct station tuning;
- Lack of a structured protocol versioning schema.

---

## 2. Формат широковещательных сообщений / Legacy Broadcast Events

### `com.salat.gbinder.PLAYBACK_STATE`
- `isPlaying` (`String`): `"1"` — воспроизведение активно, `"0"` — воспроизведение остановлено.

### `com.salat.gbinder.PLAYBACK_METADATA`
- `id` (`String`): ID медиасессии или вычисленный хэш;
- `packageName` (`String`): Имя пакета активного плеера;
- `appName` (`String`): Название приложения;
- `title` (`String`): Название трека;
- `artist` (`String`): Исполнитель;
- `album` (`String`): Альбом;
- `uri` (`String`): URI трека;
- `coverUri` (`String`): URI обложки.

### `com.salat.gbinder.AUDIO_SOURCE_CHANGED`
- `source` (`String`): `USB`, `BT`, `RADIO`, `CPAA`, `ONLINE`, `OTHER`, `YUNTING`, `UNKNOWN`.

---

## 3. Запрос состояния / State Polling

### [RU]
Для опроса состояния виджет отправлял явный Intent:
```text
action  = com.salat.gbinder.REQUEST_PLAYBACK_INFO
package = com.salat.gbinder
```

### [EN]
To poll state, the widget dispatched an explicit Intent:
```text
action  = com.salat.gbinder.REQUEST_PLAYBACK_INFO
package = com.salat.gbinder
```
