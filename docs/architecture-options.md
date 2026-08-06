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
| Overlay + полный Media Bridge GInputBridge | Единый source-aware backend, metadata, progress, artwork, sources и controls без второй notification/OneOS подписки | Требует доработки и совместимого релиза второго APK; GInputBridge становится точкой отказа | Рекомендуемый вариант |
| Overlay + legacy broadcasts GInputBridge | Быстрый read-only прототип с metadata, coarse state и current-source events | Нет атомарности, controls, position/actions и гарантированно читаемой обложки | Только совместимость/диагностика |
| Overlay + собственный notification listener | Независимая UI-карточка, metadata и controls всех корректно опубликованных медиасессий | Нужны отдельный notification access и дублирующие подписки; без OneOS возможен неверный выбор среди нескольких сессий | Резервный вариант |
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

AtlasMediaWidget не должен получать этот доступ напрямую: установленный GInputBridge уже выполняет
роль брокера и содержит нативную маршрутизацию команд. Целевой контракт описан в
[full-media-bridge.md](full-media-bridge.md), текущий broadcast API — в
[ginputbridge-api.md](ginputbridge-api.md).

## Выбор правильной сессии

На ГУ недостаточно правила «первая сессия со state=PLAYING». Нужен детерминированный selector:

При использовании полного Media Bridge выбор controller выполняется внутри GInputBridge.
AtlasMediaWidget получает уже единый snapshot. Каждый снимок имеет монотонный generation ID;
поздняя обложка или результат команды не должны перезаписывать более новое состояние.

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
- при выбранной архитектуре сбой или отключённый runtime GInputBridge становится отдельной точкой
  отказа.

Итог: по визуальной отзывчивости и восстановлению callback-цепочки кастомная реализация может быть
лучше. По системной интеграции и гарантии выживания процесса штатная карточка сильнее. По полноте
данных паритет достижим только гибридом MediaSession + OneOS current source, проверенным на реальной
ГУ.

## Минимальные проверки прототипа на ГУ

1. Включить в GInputBridge Media runtime и убедиться, что его notification access активен.
2. Проверить initial snapshot после bind и восстановление после смерти любого из двух процессов.
3. Записать snapshots и command results для Radio, Bluetooth, USB, CPAA/CarPlay и
   минимум двух сторонних плееров.
4. Проверить GInputBridge-owned `content://` artwork URI и отзыв старых grants.
5. Переключать источники при одновременно активных sessions и проверять source list/selection.
6. Выполнить cold boot, sleep/wake, restart launcher, restart GInputBridge и restart только
   AtlasMediaWidget.
7. На каждом сценарии проверять, что карточка либо восстанавливается сама, либо показывает
   `disconnected`, но не остаётся бесконечно в старом состоянии.
8. Измерить CPU/RAM и частоту update вызовов; reconcile не должен превращаться в частый poll.
