# Atlas Media Widget

Кастомная медиакарточка для Android 11 ГУ. Приложение рисует `TYPE_APPLICATION_OVERLAY` по
проверенной lifecycle-схеме AtlasAppWidget и использует GInputBridge `mediaapi` protocol v1 как
единственный медиабэкенд.

## Короткий вывод

Сделать кастомную карточку с источниками, обложкой, прогрессом и transport controls можно.
Практичный вариант — не настоящий `AppWidget`, а overlay по модели AtlasAppWidget, получающий
атомарное состояние и выполняющий команды через установленный GInputBridge. Системный лаунчер ГУ
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
аппаратный источник. В GInputBridge уже есть source-aware маршрутизация play/pause/next/previous и
переключения источников. Поэтому backend расширяется там, а AtlasMediaWidget остаётся UI-клиентом.

## Рекомендуемая схема

```text
MediaSession + OneOS
        │
        ▼
   GInputBridge
 MediaStateHub + CommandRouter
        │ open versioned bound service
        ▼
GInputBridgeMediaSource
        │ snapshot reducer / stale timeout
        ▼
foreground overlay service
        │
        ▼
 custom media view
```

GInputBridge backend уже реализован в ветке `mediaapi`. Следующий порядок относится к клиенту:

1. Реализовать `GInputBridgeMediaSource`: Messenger protocol v1, reconnect и stale-state reducer.
2. Добавить локальную экстраполяцию progress и generation-aware загрузку artwork.
3. Поднять overlay, source selector и capability-driven controls.
4. Проверить Radio, Bluetooth, USB, online и сторонние плееры на реальной ГУ.

Все четыре клиентских пункта реализованы в версии `1.0.5 (6)`. Проверки firmware-specific частей
на реальной ГУ всё ещё обязательны.

Подробное сравнение вариантов и рисков: [docs/architecture-options.md](docs/architecture-options.md).
Целевой полный контракт: [docs/full-media-bridge.md](docs/full-media-bridge.md).
Текущий legacy-контракт GInputBridge: [docs/ginputbridge-api.md](docs/ginputbridge-api.md).

## Почему это может быть стабильнее OEM-карточки

В исследованной OEM-реализации состояние терялось после смерти Binder/callback-цепочки, а
периодического восстановления почти не было. Своя реализация может делать немедленный snapshot,
повторную идемпотентную подписку и редкую сверку состояния без убийства системных процессов.

Но обычный APK не имеет статуса `persistent` и OEM-привилегий штатного виджета. Кроме того,
AtlasMediaWidget становится зависим от живого процесса GInputBridge. Поэтому по интеграции с
лаунчером и выживаемости процесса он изначально слабее; foreground service, разрешённый автозапуск
и корректный reconnect только уменьшают этот разрыв. По управлению и восстановлению состояния он
может быть лучше OEM-карточки, если GInputBridge отдаёт цельный снимок и остаётся единственным
владельцем OneOS/MediaSession callback-цепочки.

## Официальные Android API

- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)
- [MediaController](https://developer.android.com/reference/android/media/session/MediaController)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [App widgets](https://developer.android.com/develop/ui/views/appwidgets/overview)

## Возможности приложения

- atomic metadata/playback/source snapshots через Messenger;
- обложка через временно разрешённый FileProvider URI GInputBridge;
- полноразмерная обложка как фон карточки с градиентом для читаемости текста;
- пресеты 500×300 и 500×500, отдельная настройка ширины/высоты и адаптивное заполнение карточки;
- переданная звуковая волна в lossless WebP как крупная заглушка справа, когда обложка отсутствует;
- source/status pills, отдельные пустое и playing-состояния в обоих форматах;
- локальный плавный progress без секундного Binder polling;
- seek только при capability `SEEK_TO`;
- крупные previous/play-pause/next без подложки с source-aware маршрутизацией в GInputBridge;
- крупная сетка выбора только USB, Bluetooth, Radio и Online с отдельными пиктограммами;
- открытие активного online-приложения или штатного OEM media UI по нажатию на свободную область;
- radio fallback в клиенте: выбранный источник не выглядит пустым, даже если старая версия
  GInputBridge ещё не передала частоту и RDS-название;
- reconnect с bounded exponential backoff и очистка stale-состояния;
- показ только поверх HOME, сохранение позиции, foreground service и отложенный boot start.

## Требования на ГУ

1. Установить GInputBridge из совместимой ветки `mediaapi` и включить его Media runtime.
2. Оставить включённым notification access GInputBridge для публичных MediaSession-плееров.
3. Разрешить AtlasMediaWidget отображение поверх окон и доступ к истории использования.
4. Запустить виджет из настроек приложения; автозапуск включается отдельно.

API `mediaapi` v1 намеренно открыт всем установленным APK. Это приемлемо только при контролируемой
установке ПО на изолированной ГУ.

## Сборка

```sh
ANDROID_HOME=/path/to/android-sdk sh gradlew --offline clean check assembleRelease
```

Артефакт создаётся в `app/build/outputs/apk/release/` с базовым именем
`1.0.5[6]AtlasMediaWidget`. Без локального `secure.signing.gradle` release APK остаётся unsigned.
