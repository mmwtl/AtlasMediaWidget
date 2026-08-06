# Atlas Media Widget

Исследовательский репозиторий кастомной медиакарточки для Android 11 ГУ. На этом этапе здесь
зафиксирована архитектура и правила разработки; Android-приложение ещё не сгенерировано.

## Короткий вывод

Сделать кастомную карточку можно. Практичный первый вариант — не настоящий `AppWidget`, а overlay
по модели AtlasAppWidget, получающий данные от установленного GInputBridge. Системный лаунчер ГУ
жёстко закрепляет OEM-провайдер
`com.geely.mediawidget.customwidget.SourceBigWidgetProvider`, поэтому нет подтверждения, что он
позволит штатно разместить сторонний `AppWidgetProvider` в той же области.

Метаданные публичных медиасессий доступны обычному приложению через включённый пользователем
`NotificationListenerService`:

- пакет и имя проигрывателя;
- title, artist, album и media ID;
- duration, position, speed и playback state;
- поддерживаемые transport actions;
- URI/bitmap обложки, если источник их действительно публикует и разрешает чтение.

Эту работу уже выполняет GInputBridge: `MediaSessionManager` даёт ему активные `MediaController`,
`MediaController.Callback` сообщает metadata/playback state, а `OneOS MediaCenterManager` — текущий
аппаратный источник. Поэтому в AtlasMediaWidget не нужно дублировать notification listener и OneOS
Binder, пока хватает внешнего API GInputBridge.

## Рекомендуемая схема

```text
MediaSession + OneOS
        │
        ▼
   GInputBridge
        │ broadcast API
        ▼
GInputBridgeMediaSource
        │ snapshot reducer / stale timeout
        ▼
foreground overlay service
        │
        ▼
 custom media view
```

Рекомендуемый порядок прототипа:

1. Поднять overlay и `GInputBridgeMediaSource`, принимающий `PLAYBACK_METADATA`,
   `PLAYBACK_STATE` и `AUDIO_SOURCE_CHANGED`.
2. При старте/возврате из сна отправлять explicit broadcast `REQUEST_PLAYBACK_INFO` в пакет
   `com.salat.gbinder` и применять timeout к ответу.
3. Проверить реальные поля/обложки на ГУ для Radio, Bluetooth, USB, проекции и сторонних плееров.
4. Если не хватает source snapshot, duration/progress, controls или доступной обложки, расширить
   версионированный API GInputBridge; прямой OneOS Binder оставить последним вариантом.

Подробное сравнение вариантов и рисков: [docs/architecture-options.md](docs/architecture-options.md).
Текущий контракт GInputBridge: [docs/ginputbridge-api.md](docs/ginputbridge-api.md).

## Почему это может быть стабильнее OEM-карточки

В исследованной OEM-реализации состояние терялось после смерти Binder/callback-цепочки, а
периодического восстановления почти не было. Своя реализация может делать немедленный snapshot,
повторную идемпотентную подписку и редкую сверку состояния без убийства системных процессов.

Но обычный APK не имеет статуса `persistent` и OEM-привилегий штатного виджета. Кроме того,
AtlasMediaWidget становится зависим от живого процесса и настроек GInputBridge. Поэтому по
интеграции с лаунчером и выживаемости процесса он изначально слабее; foreground service,
разрешённый автозапуск и корректный reconnect только уменьшают этот разрыв. По полноте данных он
будет не хуже лишь в пределах данных, которые GInputBridge отдаёт наружу. Текущий API не передаёт
duration/position/actions и не гарантирует читаемость `coverUri`, поэтому для полного паритета его
придётся немного расширить.

## Официальные Android API

- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)
- [MediaController](https://developer.android.com/reference/android/media/session/MediaController)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [App widgets](https://developer.android.com/develop/ui/views/appwidgets/overview)
