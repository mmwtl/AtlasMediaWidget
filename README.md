# Atlas Media Widget

Исследовательский репозиторий кастомной медиакарточки для Android 11 ГУ. На этом этапе здесь
зафиксирована архитектура и правила разработки; Android-приложение ещё не сгенерировано.

## Короткий вывод

Сделать кастомную карточку можно. Практичный первый вариант — не настоящий `AppWidget`, а overlay
по модели AtlasAppWidget. Системный лаунчер ГУ жёстко закрепляет OEM-провайдер
`com.geely.mediawidget.customwidget.SourceBigWidgetProvider`, поэтому нет подтверждения, что он
позволит штатно разместить сторонний `AppWidgetProvider` в той же области.

Метаданные публичных медиасессий доступны обычному приложению через включённый пользователем
`NotificationListenerService`:

- пакет и имя проигрывателя;
- title, artist, album и media ID;
- duration, position, speed и playback state;
- поддерживаемые transport actions;
- URI/bitmap обложки, если источник их действительно публикует и разрешает чтение.

Схема уже используется в соседнем GInputBridge: `MediaSessionManager` даёт активные
`MediaController`, а `MediaController.Callback` сообщает изменения metadata/playback state.
Отдельный `OneOS MediaCenterManager` сообщает текущий аппаратный источник. Последняя часть важна:
радио, Bluetooth и сторонний стриминг могут одновременно держать живые сессии, и выбор просто
«первой playing session» иногда будет неверным.

## Рекомендуемая схема

```text
NotificationListenerService
        │
        ▼
MediaSessionManager ── active sessions / reconnect
        │
        ▼
MediaController callbacks ── metadata / playback / artwork
        │
        ├──────────────┐
        ▼              ▼
OneOS source       session selector
(optional)         + state reducer
        │              │
        └──────┬───────┘
               ▼
      foreground overlay service
               ▼
          custom media view
```

Рекомендуемый порядок прототипа:

1. Поднять overlay и собственный `NotificationListenerService`.
2. Показать все активные сессии и проверить реальные пакеты/поля на ГУ для Radio, Bluetooth,
   USB, проекции и сторонних плееров.
3. Добавить OneOS source arbitration минимальным адаптером либо временно получать
   `AUDIO_SOURCE_CHANGED` от GInputBridge.
4. Только после измерений решать, нужен ли прямой OneOS Binder в этом APK.

Подробное сравнение вариантов и рисков: [docs/architecture-options.md](docs/architecture-options.md).

## Почему это может быть стабильнее OEM-карточки

В исследованной OEM-реализации состояние терялось после смерти Binder/callback-цепочки, а
периодического восстановления почти не было. Своя реализация может делать немедленный snapshot,
повторную идемпотентную подписку и редкую сверку состояния без убийства системных процессов.

Но обычный APK не имеет статуса `persistent` и OEM-привилегий штатного виджета. Поэтому по
интеграции с лаунчером и выживаемости процесса он изначально слабее; foreground service,
разрешённый автозапуск и корректный reconnect только уменьшают этот разрыв. По полноте данных он
будет не хуже лишь для тех источников, которые публикуют полноценную `MediaSession`, либо после
добавления OneOS-адаптера.

## Официальные Android API

- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)
- [MediaController](https://developer.android.com/reference/android/media/session/MediaController)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [App widgets](https://developer.android.com/develop/ui/views/appwidgets/overview)
