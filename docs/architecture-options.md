# Варианты архитектуры AtlasMediaWidget

## Подтверждённые факты

- OEM-карточка — стандартный Android `AppWidget` из пакета `com.geely.mediawidget`.
- Лаунчер сам закрепляет `SourceBigWidgetProvider` в своей конфигурации, выдаёт право на bind и
  запрещает редактирование карточки.
- OEM-карточка получает данные не через публичную `MediaSession`, а через OneOS/MediaCenter Binder.
- GInputBridge на этой платформе уже сочетает два канала:
  - публичные `MediaSessionManager`/`MediaController` для metadata и playback state;
  - OneOS `MediaCenterManager` для текущего audio source и нативных команд.
- В GInputBridge известны нативные session packages:
  `com.android.bluetooth`, `com.geely.usbservice`, `com.geely.radio.service`.

## Варианты

| Вариант | Что получаем | Главные минусы | Оценка |
|---|---|---|---|
| Overlay + собственный notification listener | Независимая UI-карточка, metadata и controls всех корректно опубликованных медиасессий | Нужны overlay, Usage Access, notification access и foreground service; без OneOS возможен неверный выбор среди нескольких сессий | Лучший первый прототип |
| Overlay + broadcast от GInputBridge | Быстрый доступ к уже собранным metadata, playback state и текущему source | Жёсткая зависимость от второго APK и его настроек `fullBroadcast`/runtime modules; собственное восстановление ограничено | Полезен как временный диагностический режим |
| Overlay + прямой OneOS adapter | Максимальная близость к OEM: source, radio frequency, BT/USB data и нативные controls | Непубличный firmware-specific API; совместимость после обновлений не гарантирована; большой `com_geely` модуль GInputBridge содержит около 491 файлов | Делать только минимальный адаптер после прототипа |
| Настоящий сторонний `AppWidgetProvider` | Нативный AppWidget lifecycle и отсутствие overlay-окна | Нет доказательств, что OEM launcher даст добавить/закрепить его; `RemoteViews` ограничивает UI; остаётся зависимость от host Binder | Эксперимент, не основной путь |
| Root/Magisk-модификация launcher config | Карточка в штатном слоте | Риск boot loop/несовместимости, подписи и обновления прошивки, сложное восстановление | Не рекомендуется для первой версии |
| Замена OEM APK тем же package/class | Теоретически полная подмена | Конфликт установленного пакета и signature mismatch; высокий риск сломать системный UI | Не делать |

## Что реально даёт MediaSession

При наличии включённого notification listener приложение вызывает
`MediaSessionManager.getActiveSessions(listenerComponent)` и получает `MediaController` для каждой
активной сессии. Через snapshot и callback доступны:

- `MediaMetadata`: display title/title, subtitle/artist, album, duration, media ID, media URI,
  album-art URI и иногда bitmap;
- `PlaybackState`: playing/paused/stopped/buffering, position, update time, speed, error и actions;
- owner package, session activity, playback route/volume и transport controls.

Ограничения данных принадлежат источнику. Плеер может не публиковать artist, duration или artwork;
URI обложки может быть недоступен чужому UID; session может оставаться active после остановки.
`MEDIA_CONTENT_CONTROL` имеет уровень `signature|privileged`, поэтому для обычной установки надо
использовать именно явно разрешённый notification listener, а не пытаться выдать permission через
обычный runtime prompt.

## Выбор правильной сессии

На ГУ недостаточно правила «первая сессия со state=PLAYING». Нужен детерминированный selector:

1. Если OneOS сообщает текущий source, сначала сопоставить source с допустимыми package/session.
2. Среди кандидатов выбрать `STATE_PLAYING`.
3. Если играющего нет, выбрать кандидата с максимальным `lastPositionUpdateTime`.
4. Если OneOS недоступен, применить тот же порядок ко всем сессиям и явно пометить source как
   неподтверждённый.
5. Не переключать UI на другой controller из-за запоздавшего artwork callback: каждый async result
   должен содержать generation/session ID.

## Надёжность против штатного виджета

### Где кастомный может быть лучше

- собственный snapshot сразу после connect/wake/показа overlay;
- повторная регистрация listeners с backoff;
- отдельный worker для Binder, не блокирующий UI;
- редкая reconcile-проверка во время видимости;
- срок годности состояния вместо вечного показа устаревшей карточки;
- отсутствие зависимости от `AppWidgetHost`/`RemoteViews` Binder для самой отрисовки.

### Где он объективно хуже

- обычное приложение не `persistent` и может быть остановлено OEM power manager;
- overlay требует больше пользовательских разрешений и постоянное уведомление foreground service;
- окно не является частью layout лаунчера и перекрывает свою прямоугольную область;
- публичная MediaSession содержит только то, что публикует источник;
- OneOS API непубличен и привязан к конкретной прошивке;
- после OTA могут поменяться Binder contract, package names или правила запуска фона.

Итог: по визуальной отзывчивости и восстановлению callback-цепочки кастомная реализация может быть
лучше. По системной интеграции и гарантии выживания процесса штатная карточка сильнее. По полноте
данных паритет достижим только гибридом MediaSession + OneOS current source, проверенным на реальной
ГУ.

## Минимальные проверки прототипа на ГУ

1. Выдать notification access и убедиться, что listener получает reconnect после перезапуска APK.
2. Записать dump всех controller packages и metadata для Radio, Bluetooth, USB, CPAA/CarPlay и
   минимум двух сторонних плееров.
3. Проверить play/pause/next/previous и наличие соответствующих `PlaybackState.actions`.
4. Переключать источники при одновременно активных sessions и проверять OneOS arbitration.
5. Выполнить cold boot, sleep/wake, restart launcher и restart только AtlasMediaWidget.
6. На каждом сценарии проверять, что карточка либо восстанавливается сама, либо показывает
   `disconnected`, но не остаётся бесконечно в старом состоянии.
7. Измерить CPU/RAM и частоту Binder/update вызовов; reconcile не должен превращаться в частый poll.
