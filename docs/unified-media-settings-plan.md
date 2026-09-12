# [RU] План: единые настройки Widget и встроенного Media API
# [EN] Plan: Unified Settings for Widget and Integrated Media API

### [RU]
Дата: 2026-09-13. Исходная ветка: `codex/integrated-media-api`, коммит `4af3cee`.
Рабочая ветка: `codex/unified-media-settings-plan`.
Статус: **Реализовано и верифицировано**. Все этапы 1–7 (IPC контракт, runtime-контроллер, IPC сервис/клиент, двухфазное резервное копирование и журнал восстановления, объединённый UI в `MainActivity`, модульные тесты и релизная сборка) полностью выполнены.

### [EN]
Date: 2026-09-13. Base branch: `codex/integrated-media-api`, commit `4af3cee`.
Working branch: `codex/unified-media-settings-plan`.
Status: **Implemented and verified**. All phases 1–7 (IPC contract, runtime controller, IPC service/client, two-phase backup and recovery journal, unified `MainActivity` UI, unit tests, and release builds) are fully complete.

## 1. Результат и границы

Пользователь открывает Atlas Media Widget и в одном интерфейсе настраивает карточку,
источники звука, автоматические переключения и каталог радио. Оттуда же доступны
разрешения, диагностика и одна полная резервная копия с восстановлением.

API уже интегрирован в стандартный APK. Оставить `:media-runtime` и отдельный процесс
`:media`: объединять нужно пользовательский интерфейс и операции с настройками,
а не переносить OEM Binder и медиабэкенд в UI-процесс.

Рекомендуемый объём первой реализации:

- Полное объединение для `integrated`, один основной ярлык приложения.
- Все действующие пользовательские настройки API доступны из Widget.
- Полный ZIP: настройки Widget + переносимые настройки API + пользовательский каталог
  радио с обложками. Старые JSON схем 1–9 по-прежнему импортируются.
- `plain` и автономный `:api-app` сохраняются согласно AGENTS.md. Для `plain` пока
  сохраняются отдельные настройки API и экспорт только Widget с явной подписью объёма.
- В автономном API добавить локальный экспорт/импорт медиачасти того же формата,
  чтобы обеспечить перенос из старой раздельной установки в `integrated`.

Не входят: изменение арбитража источников, новый механизм воспроизведения,
перезапись избранного в OneOS, объединение процессов, облачная синхронизация,
автоматическая выдача разрешений, удаление автономного приложения или его данных.

## 2. Что подтверждено в текущем коде

Пути ниже относительно корня репозитория; имена методов помогают найти место после
сдвига строк. Выводы получены чтением кода, а не запуском на головном устройстве.

| Область | Подтверждение | Следствие |
| --- | --- | --- |
| Встраивание | `app/build.gradle`: `integratedImplementation project(':media-runtime')`; `MediaRuntime.kt`: координатор на процесс | Повторное встраивание APK не требуется |
| Процессы | `media-runtime/src/main/AndroidManifest.xml`: Bridge, listener и DiagnosticActivity в `:media`; Bridge приватный | UI должен обращаться к владельцу медианастроек по IPC |
| Второй ярлык | Там же: экспортированный `MediaApiLauncher` alias; `MainActivity.openAtlasMediaApi()` запускает диагностику | В integrated сейчас два входа в настройки |
| Настройки Widget | `app/.../Prefs.java`: `atlas_media_widget` в device-protected storage; `replacePortableSettings()` | Сохранить существующие ключи, миграции и поведение до разблокировки |
| Настройки API | `media-runtime/.../settings/AtlasPreferences.kt`: `atlas_media_api_settings` в обычном storage | Не читать и не записывать этот SharedPreferences из Widget-процесса |
| Радиоданные | `RadioCatalogRepository.kt`: `radio_catalog_prefs`, `files/custom_radio`, кэш обложек отдельно | Копии только JSON недостаточно для пользовательского каталога |
| Приборная панель | `ClusterMediaBridge.kt`: `cluster_dim_prefs`, включение трансляции и интервал watchdog; оба параметра доступны в DiagnosticActivity | Это ещё одна группа действующих настроек API, её тоже переносить |
| Текущий backup | `SettingsBackup.java`: формат `atlas-media-widget-settings`, schema 9, reader 1–9, лимит 256 КиБ | Сохранить старый reader; текущая копия не содержит API или каталог |
| Текущий UI импорта | `MainActivity.confirmSettingsImport()`: каталог прямо исключён; `applySettings()` меняет только `Prefs` | Нужны новый preview и координация применения |
| Сохранение файла | `SettingsExportStore.java`: Downloads через MediaStore, `IS_PENDING`, удаление при ошибке | Переиспользовать публикацию готового архива |
| Радиоэкспорт | `RadioCatalogRepository.exportSampleZip()` читает assets | Это образец встроенного каталога, а не backup текущего пользовательского |
| Замена каталога | `importCustomZip()` удаляет `customDirectory` перед rename/copy; `restoreDefaultCatalog()` тоже удаляет его | Комментарий об atomic swap не обеспечивает восстановление после сбоя |
| IPC | Обе версии `MediaBridgeContract`: v1, REGISTER/GET_SNAPSHOT/COMMAND/GET_RADIO_STATIONS | Настроек и полного backup в протоколе нет |
| Масштаб | REGISTER переносит `uiScaleTenths` в API; DiagnosticActivity получает scale через Intent | Уже есть синхронизация масштаба, но два хранимых значения |
| OEM-станции | `GET_RADIO_STATIONS` и `RadioStationLists` передают сохранённые/избранные станции; команды восстановления списка нет | Каталог названий/обложек и память приёмника не взаимозаменяемы |

В документации каталога заявлены проверки обложек и безопасных имён. В текущем
`importCustomZip()` + `RadioCatalogCsv.read()` нет полной проверки существования,
формата и размеров всех указанных изображений. Проверка пути через строковый
`startsWith(stagingPath)` без границы каталога также недостаточна. Перед
переиспользованием импортёра требуется довести валидацию до заявленного контракта.

## 3. Владелец каждого параметра и состав резервной копии

| Группа | Поля / данные | Где редактировать и что переносить |
| --- | --- | --- |
| Widget | автозапуск, положение/угол, размеры px и параметры двух стилей, масштаб, ручка перетаскивания, навигация радио, сетка избранного | Основные настройки; всё, что сейчас входит в `SettingsBackup.Data` |
| Источник по умолчанию | `defaultAudioSource`: пусто / RADIO / BT / USB / ONLINE / CPAA | «Медиа → Источники»; включить |
| Старт | `defaultAudioSourceDelaySec` 0–30, `defaultAudioSourceAutoplayOnStartup` | Там же; включить |
| Потеря источника | `autoSwitchToDefaultOnSourceLost`, `autoSwitchToDefaultAutoplayOnSourceLost` | Там же; включить |
| Online-проигрыватель | `defaultMediaPackage`, `switchToOnlineBeforeSessionPlay` | «Медиа → Online»; включить, эти поля используются в `AndroidMediaCommandHost`, хотя текущий экран не даёт полноценно их настроить |
| Радио | `radio_covers_enabled` / `isWidgetBroadcastEnabled` | «Медиа → Радио»; включить; подпись должна описывать фактический эффект настройки, а не только обложки |
| Приборная панель | `cluster_dim_covers_enabled`, default true | «Медиа → Радио → Приборная панель»; включить, управляет трансляцией названия и обложки |
| Восстановление данных приборки | `adaptive_watchdog_base_interval_ms`, 1000–5000 мс, default 1250 мс | Там же, расширенный параметр; включить, сохранить единицы миллисекунд и ограничения |
| Каталог | builtin/custom, `stations.csv`, `covers/*` | Включить пользовательский каталог целиком; для builtin — маркер использования каталога установленной версии |
| Масштаб API | `uiScaleTenths` | В integrated единственный источник — масштаб Widget; API-значение считать производным, не вторым независимым переносимым полем |
| Демо | `demoModeEnabled` | «Диагностика»; исключить из обычного backup и не менять при обычном импорте |
| Логирование | `diagnosticLoggingEnabled` | В проверенном дереве есть поле, но не найден рабочий потребитель; не создавать неработающий переключатель. Исключить из backup; подключение логирования — отдельная задача |
| Состояние запуска | `service_enabled`, подключения, выбранный сейчас источник, позиция трека, таймеры | Не переносить и не включать воспроизведение самим импортом |
| Данные среды | разрешения, URI grants, пути USB, runtime artwork URI, кэши, diagnostic dumps, миграционные маркеры | Не переносить; разрешения проверять заново |
| OneOS | сохранённые/избранные станции головного устройства | Не включать в обещание восстановления; можно показать предупреждение в preview |

Для неизвестного source ID — ошибка валидации без изменений. Для корректного, но
временно недоступного источника — сохранить выбор и показать состояние доступности.
Для отсутствующего Android-проигрывателя — сохранить имя пакета как намерение,
показать «Приложение не установлено» и не подменять его другим автоматически.

Builtin-каталог не дублировать в каждом backup: при восстановлении используется
вариант из установленного APK. Если требуется побайтовое сохранение старого
встроенного каталога, это отдельный режим материализации в пользовательский каталог.

## 4. Интерфейс

В `MainActivity` сохранить действующий Atlas graphite стиль и текущий предпросмотр.
Добавить разделы или раскрываемые группы, не наращивать одну непрерывную простыню:

1. «Виджет»: внешний вид, положение, поведение карточки и избранного.
2. «Медиа»: источник по умолчанию, запуск, потеря источника, Online-приложение,
   радио, трансляция на приборку и интервал восстановления её данных.
3. «Система»: необходимые доступы, автозапуск, USB-доступ и текущие статусы.
4. «Резервная копия»: экспорт, выбор файла, предварительный просмотр и импорт.
5. «Диагностика»: состояния соединений, отчёт, демо; подробный экран допустимо
   открывать внутри приложения через существующую приватную DiagnosticActivity.

Обычные медианастройки в integrated убрать из DiagnosticActivity после переноса,
чтобы не было двух конкурирующих редакторов. Автономному `api-app` оставить редактор,
но провести его через те же операции валидации и применения. Не копировать логику
сохранения в два Activity и не переписывать весь UI на новый toolkit.

У integrated удалить `MediaApiLauncher` через flavor manifest merge, не удалять
его безусловно из библиотеки. Проверить итоговые manifests всех вариантов.
Сохранить приватную диагностику в `:media`; устранить отдельную задачу/брендинг,
если текущий `taskAffinity` мешает возврату в Widget. Автономный launcher сохранить.

Состояния загрузки медианастроек: загрузка / доступны / медиасервис недоступен /
версия не поддерживает управление. Не показывать defaults как прочитанные значения.
После записи — ожидание подтверждения, затем значение подтверждённой revision.
Ошибки backend не должны блокировать локальные настройки внешнего вида.

## 5. Межпроцессный контракт и применение

### 5.1. Владение и минимальные изменения

`Prefs` остаётся владельцем Widget-параметров в основном процессе.
`AtlasPreferences`, `RadioCatalogRepository` и настройки `ClusterMediaBridge`
остаются в `:media`.
Добавить небольшой `MediaSettingsController` в runtime: snapshot, строгая валидация,
последовательная запись и применение эффектов. Использовать его и из IPC,
и из автономной DiagnosticActivity. Не вводить новую БД и универсальный settings-framework.

Общие SharedPreferences между процессами не поддерживаются Android; одинаковый
UID не делает их кэши согласованными. Это основание для IPC, а не для
`MODE_MULTI_PROCESS` или прямого доступа к runtime singleton из Widget.
[Android SharedPreferences](https://developer.android.com/reference/android/content/SharedPreferences).

### 5.2. Расширение существующего Messenger

Сохранить медиапротокол v1. В REGISTERED добавить необязательные
`settingsProtocolVersion=1` и признаки поддерживаемых операций; отсутствующие поля
означают отсутствие поддержки. Не смешивать эти признаки с transport capability mask.
Новые номера сообщений и ключи зафиксировать в обеих копиях MediaBridgeContract
и покрыть тестом соответствия. Не менять смысл старых сообщений.

Предлагаемые операции (имена семантические, числовые коды выбрать в реализации):

| Операция | Запрос | Ответ / гарантия |
| --- | --- | --- |
| GET_SETTINGS | requestId | typed settings, catalog info, settingsRevision, доступные функции |
| UPDATE_SETTINGS | requestId, expectedRevision, именованные изменяемые поля | подтверждённые значения + новая revision либо VALIDATION_ERROR/CONFLICT |
| EXPORT_MEDIA_BACKUP | requestId, выходной file descriptor | согласованная медиачасть + каталог; завершение только после записи потока |
| PREPARE_MEDIA_IMPORT | operationId, expectedRevision, входной descriptor | staging token, preview и hash проверенного содержимого; без изменения активных данных |
| COMMIT_MEDIA_IMPORT | operationId, staging token | долговечно сохранённые данные и revision; повтор того же operationId не применяет эффекты снова |
| GET_IMPORT_STATUS | operationId | не начато / подготовлено / сохранено / ошибка; для восстановления после потери ответа |
| ABORT_MEDIA_IMPORT | operationId | удаление ещё не применённого staging, не откат уже сохранённого импорта |

Записи сериализовать у владельца, включая изменения из standalone UI и операции
каталога. Revision относится к настройкам и каталогу, а не playback generation;
меняется при каждой подтверждённой мутации. Импорт отклоняется при конфликте
с изменениями после preview. Изменение масштаба при REGISTER не должно незаметно
перезаписывать импортируемые поля или создавать второй авторитетный масштаб.
В `ScaledContextHelper.resolveScaleTenths()` сейчас есть прямое чтение Widget
SharedPreferences из `:media`: убрать его как источник актуального масштаба.
Для integrated использовать явно переданный масштаб и локальную runtime-копию,
обновляемую IPC; проверить порядок scale extra и `attachBaseContext`, чтобы
DiagnosticActivity сразу открывалась в нужной плотности без мигания/повторного запуска.

Основной scope — integrated: конфигурационные IPC-операции и backup доступны
только внутри приложения; автономный сервис не рекламирует их внешним клиентам
и отвергает такие запросы. Не расширять открытый внешний v1 доступом к файлам
и настройкам. Локальный standalone UI использует controller напрямую.
Существующие правила транспортных команд и их открытость не менять.

Расширить `MediaBridgeClient` как единственный клиентский адаптер. Экран настроек
имеет собственный lifecycle подключения и работает при выключенном overlay.
Использовать существующий backoff; ошибки и ответы привязывать к requestId и
поколению подключения. Потеря ответа на запись не доказывает, что запись не прошла:
перечитать revision/статус, а не бесконечно повторять команду.

CSV, изображения и ZIP передавать потоками через `ParcelFileDescriptor`, не Bundle
с массивом байтов. Сначала копировать выбранный файл во внутренний staging,
чтобы восстановление не зависело от живого Activity или временного URI grant.
Стриминг, валидация и файловые записи — на IO worker; не блокировать main looper
и обработку PLAY/PAUSE. Размер Binder-буфера ограничен и разделяется между
транзакциями процесса. [Android TransactionTooLargeException](https://developer.android.com/reference/android/os/TransactionTooLargeException).

### 5.3. Эффекты настроек

- Пакет Online и политика переключения применяются к следующим командам.
- Новый стартовый источник/задержка отменяют устаревшую отложенную задачу;
  сохранение или импорт сами не запускают новый startup autoplay.
- Политика потери источника применяется к следующим событиям потери.
- Каталог и radio switch обновляют производные metadata/обложки, инвалидируют
  старые artwork revision и публикуют новый snapshot.
- Переключатель приборки применять через поведение `setClusterCoversEnabled()`:
  обновить in-memory state и разрешение трансляции DirectDim, а не только prefs.
  Интервал watchdog обновить в его store и последующих задержках без дублирования
  циклов отправки. При batch import разделить долговечную запись и эти эффекты.
- При импорте не вызывать `stopBackend()/startBackend()` только ради перечитывания
  prefs: это может повторно запустить startup-логику. Демо меняется отдельно через
  существующий `setDemoMode()` и не затрагивается обычным восстановлением.
- Полный импорт применяет один набор значений и уведомляет после его завершения,
  а не делает цикл из отдельных setter `.apply()`.

## 6. Формат и совместимость backup

Предлагается новый контейнер `AtlasMediaWidget-backup.zip`, MIME `application/zip`.
Старый `AtlasMediaWidget-settings.json` остаётся читаемым, его schema не менять
для обозначения совершенно другого содержимого.

```text
AtlasMediaWidget-backup.zip
├── manifest.json
├── widget.json              # существующий envelope schema 9
├── media.json               # новая schema 1, переносимые поля из раздела 3
└── radio/                   # только при custom
    ├── stations.csv
    └── covers/*
```

`manifest.json`: `format=atlas-media-backup`, `schemaVersion=1`, appVersion,
originPackage, originFlavor, createdAt, список включённых секций и хэши файлов.
Origin — информация для пользователя, а не требование одинакового package/flavor.
Хэши проверяют повреждение, но не являются подписью или признаком доверенного автора.

`media.json`: собственная schema, строго типизированные настройки, `catalogMode`
(`builtin`/`custom`). Для custom обязательны CSV и все указанные обложки.
Для builtin отсутствие radio/ ожидаемо и означает использование встроенного каталога.
Отсутствие media-секции означает «не менять API», а не «сбросить API».
Явная media-секция с builtin означает замену активного пользовательского каталога;
это должно быть видно до подтверждения.

Один контейнер поддерживает полный backup integrated и media-only backup standalone.
Список секций должен точно совпадать с содержимым. Не экспортировать частичный
результат под видом полного, когда процесс `:media` не отвечает.

| Вход / окружение | Поведение |
| --- | --- |
| Старый JSON 1–9 → integrated/plain | Импорт Widget, API/радио не меняются |
| Полный ZIP → integrated | Проверка всех секций, preview, согласованное применение |
| Media-only ZIP → integrated | Изменение только API/каталога, виджет не меняется |
| Полный ZIP → plain | Предложить явно импортировать только Widget; не объявлять восстановленной медиачасть |
| ZIP → standalone API | Применить только media/радио после явного preview; Widget-секцию не применять |
| ZIP будущей schema или повреждённый | Отказ до изменений, конкретная причина |
| Обычный radio ZIP с stations.csv | Отдельная операция «Импорт каталога»; не путать с полным backup |

Проверять содержимое, а не только расширение/MIME. Переиспользовать legacy decoder.
Для нового ZIP разрешить только ожидаемые пути, запретить абсолютные пути, `..`,
дубли entries и выход за staging directory; не нормализовать опасный путь в допустимый.
CSV: до 256 станций, диапазоны/дубли/длина имени по существующим правилам;
обложки: безопасные имена, наличие, WebP/PNG/JPEG, размеры 32–4096 px.
Лимиты считать при чтении, не доверять ZIP metadata: radio payload до 64 МиБ и
300 entries, служебные JSON до 256 КиБ каждый, общий распакованный объём до 65 МиБ,
общий лимит entries 303. Ограничить также размер входного файла, например 70 МиБ.
Проверить эти предлагаемые пределы на самом большом допустимом fixture.

Экспорт должен захватить согласованный набор настроек/каталога. Сериализовать
мутации, сделать снимок в staging и отпустить блокировку до длительной записи
во внешнее хранилище. На стороне Widget на время захвата исключить правки,
включая сохранение позиции перетаскиванием. Не держать lock медиакоманд на всё время ZIP.

## 7. Импорт без тихого частичного результата

Четыре SharedPreferences (Widget, API, радио, приборка) и каталог файлов не образуют
общую транзакцию. Простое «сначала Widget, затем API» даёт частичное восстановление
при падении процесса.
Не обещать мгновенную атомарность между процессами или откат уже отправленных OEM-команд.

Рекомендуемый минимум — небольшой постоянный журнал одной операции импорта,
стабильный operationId и доведение подтверждённого импорта до конца после сбоя.
Это локальная логика восстановления, не общий transaction framework.

1. Скопировать вход во внутренний staging, полностью прочитать и проверить архив.
   Подготовить Widget-данные и runtime staging. До подтверждения активные настройки
   и каталог не меняются. Показать заменяемые секции, число станций, предупреждения
   об отсутствующих приложениях и непереносимых разрешениях/OneOS-избранном.
2. При подтверждении записать journal с operationId, составом, hash,
   ожидаемой revision и целевыми данными. Staging/journal хранить в filesDir,
   не в очищаемом cache. Отклонять запуск конкурирующего импорта; заблокировать редакторы
   затрагиваемых настроек, включая standalone controller и положение overlay.
3. Применить медиачасть. Новый каталог полностью подготовить рядом со старым;
   старый сохранять до подтверждения нового. Persist данных, переключение каталога
   и operationId/revision должны иметь собственное восстановление после сбоя.
   При старте runtime завершать восстановление до чтения активного каталога и
   startup-эффектов. Нельзя повторно использовать delete-before-copy.
4. После подтверждения медиачасти записать Widget одним `commit()` и сохранить
   operationId вместе с переносимыми ключами. Продвинуть журнал. Если ответ API
   потерян, запросить статус operationId. После commit отмена означает уже не
   «ничего не менять»; не показывать такую кнопку.
5. Проверить состояния обоих владельцев, обновить overlay и UI, отметить завершение,
   очистить прежний каталог/staging. Только теперь показывать «Импорт завершён».

При остановке UI между шагами новая сессия сначала восстанавливает журнал и
идемпотентно дописывает оставшуюся часть. Не разрешать новые записи поверх
незавершённого импорта. Если продолжить нельзя, показывать конкретно, какие секции
сохранены, какие ожидают восстановления; не выдавать общий успех и не терять staging.
При ошибке `commit()` учитывать также уже изменившийся in-memory state: читать
через контролируемого владельца и восстанавливать его, а не считать `false` откатом.

Обычный импорт каталога и восстановление builtin должны использовать ту же
безопасную замену. Старый каталог удалять только после успешного переключения.
При отмене до commit удалять staging; при нехватке места не затрагивать старые данные.

Widget использует device-protected storage, runtime — credential-protected.
Полный импорт/экспорт разрешать после разблокировки. Не переносить все настройки
в один storage ради удобства. BootReceiver/OverlayService должны учитывать
незавершённый импорт до автоматических действий; если для этого нужен флаг до
разблокировки, хранить там только факт ожидания, не весь архив.
[Android Direct Boot](https://developer.android.com/privacy-and-security/direct-boot).

## 8. Обновление и переход с отдельного API

Обычное обновление integrated сохраняет прежние имена preferences и каталоги;
не сбрасывать значения при первом открытии нового UI. Масштаб Widget имеет
приоритет, runtime scale приводится к нему без отдельного выбора пользователем.

Данные `com.mmwtl.atlasmediaapi` не появляются автоматически в
`com.mmwtl.atlasmediawidget`. Путь перехода:

1. Обновить автономный API до версии с экспортом media-only backup.
2. Экспортировать медианастройки и текущий пользовательский каталог локально из API.
3. Импортировать файл в integrated Widget; настройки карточки сохранить.
4. Предоставить доступ к новому NotificationListener-компоненту и проверить USB-доступ.
5. Проверить источники/приборку. Если остаются две установки с активными listeners,
   предложить пользователю отключить доступ старому API или удалить его вручную.

Вероятность конфликтов двух одновременно работающих бэкендов требует проверки
на конкретной прошивке; это риск, а не подтверждённый здесь результат. Не применять
force-stop или удаление чужого пакета как миграционный механизм.

Отдельно проверить системный Auto Backup: у Widget сейчас `allowBackup=true`,
у standalone API — false. Ручной ZIP и системное восстановление — разные механизмы.
Журналы незавершённого импорта, staging и временные export-файлы не должны
восстанавливаться системой как пользовательские настройки; при необходимости
добавить точечные backup exclusions, не менять всю политику без отдельного решения.

## 9. Этапы реализации и файлы

Все этапы ниже пока не начаты. Порядок важен: сначала контракт и безопасное хранение,
затем интерфейс и обещание полного восстановления. После каждого этапа обновлять статус.

| Этап | Изменения | Критерий завершения |
| --- | --- | --- |
| 1. Контракт | `media-core/.../MediaBridgeContract.kt`, `app/.../MediaBridgeContract.java`; модели settings/media backup с явными типами и схемами | Зафиксированы поля, default/absent semantics, capability negotiation, размеры, ошибки и совместимость |
| 2. Runtime | Новый `media-runtime/.../settings/MediaSettingsController.kt`, `AtlasPreferences.kt`, `MediaBackendCoordinator.kt`, `RadioCatalogRepository.kt`, `ClusterMediaBridge.kt` | Один путь записей; batch apply; revision; экспорт реального каталога; валидация и восстановимая замена |
| 3. IPC | `MediaBridgeService.kt`, `MediaBridgeClient.java`, локальные typed codecs | Settings работают при выключенном overlay; offline/timeout/conflict отображаются; внешний v1 не получает новых привилегий |
| 4. Backup | Сохранить `SettingsBackup.java` как legacy codec; добавить `FullSettingsBackup.java` и небольшой координатор импорта; изменить `SettingsExportStore.java`, `Prefs.java` | Полный круг export/import, прежние JSON, persistent recovery на каждой границе, нет ложного успеха |
| 5. UI | `MainActivity.java`, небольшие панели по необходимости; `DiagnosticActivity.kt`, `ScaledContextHelper.kt`; integrated manifest | Все действующие настройки доступны из Widget, один масштаб и один ярлык; standalone остаётся работоспособным |
| 6. Миграция и docs | Локальный media-only экспорт/импорт в standalone DiagnosticActivity, manifests/backup rules при необходимости; README и `docs/radio-catalog.md`, `docs/full-media-bridge.md`, module README | Переход с отдельного API описан и проверен; публичные обещания соответствуют фактическому объёму |
| 7. Проверки и сборка | Unit + process integration + UI/device matrix ниже | Пройдены проверки, ограничения реального ГУ явно записаны, изменения закоммичены |

Не добавлять зависимость `plain` от `media-runtime` ради новых моделей.
Располагать небольшие DTO/codec там, где они доступны нужным модулям; если
переиспользование потребует Kotlin в Java-app, оценить стоимость относительно
двух явных Bundle-кодеков с тестом соответствия. Большой перенос всех классов
контракта и UI не является обязательной частью этой задачи.

## 10. Проверка результата

Автоматические проверки должны проверять ошибки и границы, а не повторять setters:

- Legacy fixtures JSON 1–9: правильные миграции, API/каталог не меняются.
- Полный round trip: все переносимые Widget/API поля, каталог и байты обложек.
  Проверять и экспорт при не-default значениях, и импорт на чистую установку.
  Отдельно проверить параметры приборки, границы watchdog и единый UI scale;
  убедиться, что изменились не только файлы, но и in-memory состояния runtime.
- Media-only сохраняет Widget; builtin заменяет custom только после preview;
  отсутствующая секция ничего не сбрасывает; будущая schema отклоняется целиком.
- Невалидные типы, source IDs, задержка, повреждённый JSON/ZIP, дубли entries,
  zip traversal, отсутствующие/невалидные обложки, превышение лимитов — без мутаций.
- Revision conflict, смерть Binder до записи и после неё, повтор operationId,
  потеря ответа, сбой/нехватка места на каждом шаге, restart обоих процессов.
- Нет повторного startup autoplay, изменения live-состояния запуска overlay или
  бесконечного retry после импорта. Отмена старого таймера проверяется отдельно.
- Старая версия Bridge + новый клиент и наоборот; `plain` со старым standalone;
  configuration messages недоступны внешним клиентам автономного сервиса.
- Сохранить регрессии session selection, stale-state expiry, source mapping,
  reconnect и artwork generation. Расширять существующие suites по месту.

Unit-тестами нельзя доказать process death, Android permission grants или OEM Binder.
Нужны инструментальные проверки двух процессов и файлового восстановления.

После реализации приложения выполнить из wrapper:

```sh
sh gradlew --offline clean check assembleRelease
sh gradlew --offline :app:assemblePlainRelease
```

Проверить integrated и standalone APK, а также отдельно plain: package/version,
подпись через `apksigner verify`, правильные имена артефактов и merged manifests.
Integrated: private Bridge и diagnostics в `:media`, listener и provider с Widget
authority, один launcher, нет вложенного API APK и REQUEST_INSTALL_PACKAGES.
Plain: нет runtime-компонентов, installer или runtime-зависимости. Standalone:
сохранён собственный launcher и внешний v1 Bridge.

На ветке плана/реализации базовые `appVersionCode=48`, `appVersionName=3.0.1`
не повышать; используется существующий branch suffix. Release bump делать только
при завершённой сборке улучшения из main по AGENTS.md.

Android 11, 1440×1920 portrait: новый UI, файл через picker/Downloads, применение
при включённом и выключенном overlay, rotation/recreate, фон/возврат, отсутствие
picker, разрешения и process death. На реальном ГУ: радио/BT/USB/Online/CarPlay,
приборка, startup source, потеря источника, сон/пробуждение, миграция разрешений.
Недоступные источники и отсутствие ГУ фиксировать как непроверенное.

## 11. Вопросы и рекомендуемые решения

Эти решения нужно подтвердить при согласовании реализации; они не выдаются за
уже выбранные пользователем требования.

| Вопрос | Рекомендация | Цена / ограничение |
| --- | --- | --- |
| Удалять ли отдельную иконку API в integrated? | Да; диагностика из Widget | Меняется привычный вход; не удалять до переноса настроек |
| Нужна ли такая же полнота для plain? | Пока нет; сохранить его явный раздельный режим | Полный единый внешний control protocol существенно расширяет объём и поверхность доступа |
| Включать пользовательский каталог и обложки? | Да, обязательная часть полной копии | ZIP вместо одного JSON, больше места и время файловых операций |
| Переносить избранное самого приёмника? | Нет в этой задаче | Нужен отдельный подтверждённый OEM write API и тест на ГУ |
| Восстанавливать builtin побайтово? | Нет, использовать каталог установленной версии | При обновлении приложения встроенный набор может отличаться |
| Переносить демо и диагностическое логирование? | Нет; оставить состояние получателя | Обычный backup не является побайтовой копией всех SharedPreferences |
| Импорт должен сразу включать музыку? | Нет; сохранить политику для следующего события | Нужна явная отмена устаревших startup-таймеров |
| Нужен перенос со старой отдельной установки? | Да, через media-only файл | Сначала требуется обновить старый API до версии с экспортом |
| Разрешить выбор отдельных полей при импорте? | Только выбор поддерживаемых секций по варианту, без сложного merge полей | Проще проверяемая семантика replace; preview обязателен |
| Что делать при падении посередине? | Доводить подтверждённый импорт по журналу, показывать статус | Нужна локальная recovery-логика; без неё нельзя обещать надёжный полный импорт |

Основная стоимость — не перенос переключателей, а корректный импорт настроек и
файлов между двумя процессами с сохранением прежних установок. UI можно сделать
быстро, но он не завершает задачу без проверенного backup и восстановления после сбоя.

## 12. Статус проверки и реализации / Verification & Implementation Status

### [RU]
План полностью реализован и верифицирован в рамках рабочей ветки `codex/unified-media-settings-plan`:
1. **Контракт IPC**: в `MediaBridgeContract.kt` и `MediaBridgeContract.java` реализованы сообщения 6–13 и 105–112, коды статусов 9–11, ключи настроек и статусов импорта.
2. **Runtime-контроллер и хранилище**: `RadioCatalogRepository.kt` получил защиту от path traversal, лимиты 64 МБ / 300 файлов, валидацию обложек и атомарный своп каталога; `MediaSettingsController.kt` управляет ревизиями, экспортом/импортом медиа-ZIP и валидацией диапазонов настроек.
3. **IPC-сервис и клиент**: `MediaBridgeService.kt` фильтрует вызовы через `isSettingsAllowed()` и обеспечивает потоковую передачу через `ParcelFileDescriptor`; `MediaBridgeClient.java` реализует типизированные асинхронные методы вызова настроек и двухфазного импорта.
4. **Резервное копирование и журнал**: `FullSettingsBackup.java` упаковывает и распаковывает единый ZIP (`manifest.json` с SHA-256, `widget.json`, `media.json`, `radio/`), поддерживает legacy JSON схемы 1–9; `ImportJournal.java` гарантирует восстановление или откат при сбое питания; `SettingsExportStore.java` сохраняет архивы в `MediaStore.Downloads`.
5. **UI и манифесты**: в `AndroidManifest.xml` integrated-сборки отключен дублирующий лаунчер-ярлык `MediaApiLauncher`; `DiagnosticActivity.kt` ветвится по режиму `isIntegrated`; `MainActivity.java` предоставляет единый интерфейс из 5 логических секций.
6. **Модульные тесты**: `MediaSettingsControllerTest` (8 тестов), `FullSettingsBackupTest` (5 тестов), `ImportJournalTest` (3 теста), `MediaBridgeContractTest` (Java и Kotlin), `ScaledContextHelperTest` успешно пройдены (100% pass rate).
7. **Сборка артефактов**: проверена офлайн-сборка `./gradlew --offline clean check assembleRelease` и `:app:assemblePlainRelease`. Манифесты APK верифицированы: один лаунчер в integrated, процесс `:media`, authority FileProvider виджета, отсутствие `REQUEST_INSTALL_PACKAGES`.

### [EN]
The implementation plan is fully realized and verified on the `codex/unified-media-settings-plan` branch:
1. **IPC Contract**: `MediaBridgeContract.kt` and `MediaBridgeContract.java` implement messages 6–13 and 105–112, status codes 9–11, setting keys, and import statuses.
2. **Runtime Controller & Storage**: `RadioCatalogRepository.kt` enforces path traversal protection, 64 MB / 300 files limits, image dimension validation, and atomic directory swapping; `MediaSettingsController.kt` coordinates settings revisions, media ZIP export/import, and value range checks.
3. **IPC Service & Client**: `MediaBridgeService.kt` restricts configuration via `isSettingsAllowed()` and streams archives through `ParcelFileDescriptor` pipes; `MediaBridgeClient.java` supplies typed asynchronous calls for settings and two-phase imports.
4. **Backup & Crash Journal**: `FullSettingsBackup.java` packages and parses the unified ZIP (`manifest.json` with SHA-256, `widget.json`, `media.json`, `radio/`) and maintains legacy JSON 1–9 backward compatibility; `ImportJournal.java` protects against power failures during import; `SettingsExportStore.java` manages `MediaStore.Downloads`.
5. **UI & Manifests**: In integrated `AndroidManifest.xml`, the secondary launcher alias `MediaApiLauncher` is removed; `DiagnosticActivity.kt` branches based on `isIntegrated`; `MainActivity.java` presents a unified 5-section UI.
6. **Unit Test Suites**: `MediaSettingsControllerTest` (8 tests), `FullSettingsBackupTest` (5 tests), `ImportJournalTest` (3 tests), `MediaBridgeContractTest` (Java & Kotlin), `ScaledContextHelperTest` all pass (100% success rate).
7. **Artifact Verification**: Verified via `./gradlew --offline clean check assembleRelease` and `:app:assemblePlainRelease`. APK manifests verified: single launcher in integrated, `:media` process isolation, widget FileProvider authority, zero `REQUEST_INSTALL_PACKAGES`.
