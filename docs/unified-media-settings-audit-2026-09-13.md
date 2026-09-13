# Проверка объединения настроек Widget и Media API — 2026-09-13

Ветка: `codex/unified-media-settings-plan`, коммит `728aa37`
(«Separate settings and radio catalog transfers with full replacement»).
Предыдущая проверка: `docs/unified-media-settings-review-2026-09-13.md`.
Это проверка кода и документации, а не тест на головном устройстве. OEM Binder,
реальный OneOS и отключение питания здесь не проверялись.

---

## 1. Что проверено

- `sh gradlew --offline clean check assembleRelease` — успешно, 632 теста, 0 падений.
- Артефакты: `3.0.1-codex-unified-media-settings-plan[48]AtlasMediaWidget-release.apk`,
  `1.1.0-codex-unified-media-settings-plan[43]AtlasMediaApi-release.apk`.
- Прочитаны целиком: `MediaBridgeContract.kt/.java`, `MediaBridgeService.kt`,
  `MediaSettingsController.kt`, `MediaBridgeClient.java`, `FullSettingsBackup.java`,
  `ImportJournal.java`, `MainActivity.java` (ключевые пути), `RadioCatalogRepository.kt`,
  `ScaledContextHelper.kt`, `SettingsExportStore.java`, `docs/*.md`, тесты.

## 2. Подтверждённо корректное

- Flavor `plain` и `EmbeddedApiInstaller`/`ApiInstallResultReceiver` удалены, второго
  ярлыка нет (`MediaApiLauncher` снят flavor-manifest), `DiagnosticActivity` живёт в
  `:media` с `taskAffinity=""`.
- Settings-сообщения обслуживаются вне main looper (`scope.launch(Dispatchers.IO)`),
  `isSettingsAllowed()` пускает только свой UID/пакет, приватный Bridge не экспортирован.
- Двухфазный импорт: `ImportJournal` на стороне Widget, `stagedOperations` +
  `KEY_LAST_COMMITTED_OPERATION` на стороне runtime, идемпотентный `operationId`,
  восстановление после пересоздания контроллера покрыто Robolectric.
- Legacy JSON схем 1–9 читается, `SettingsBackup` не менялся по смыслу.
- Каталог радио: безопасная замена `custom_radio_prev`/`custom_radio_next`,
  `validateDirectory`, проверка обложек (формат, размеры 32–4096 px, безопасные имена).

---

## 3. Дефекты

### 3.1. `docs/full-media-bridge.md` описывает другую реализацию, чем в коде

Это не стилистика: в документе прямо неверные факты.

| Место | Что написано | Что в коде |
| --- | --- | --- |
| §3, строки 65–70 | `8=RESTORE_DEFAULT_CATALOG`, `9=EXPORT_MEDIA_BACKUP`, `10=PREPARE`, `11=COMMIT`, `12=ABORT`, `13=GET_IMPORT_STATUS` | `8=EXPORT_MEDIA_BACKUP`, `9=PREPARE`, `10=COMMIT`, `11=GET_IMPORT_STATUS`, `12=ABORT`, `13=RESTORE_DEFAULT_CATALOG` |
| §3 | Сообщений 14/15 нет | `EXPORT_RADIO_CATALOG=14`, `IMPORT_RADIO_CATALOG=15` |
| §3, серверные номера | `107=DEFAULT_CATALOG_RESTORED`, `108=MEDIA_BACKUP_EXPORTED`, … | Фактические значения `ServerMessage` сдвинуты; `114`/`115` в доке отсутствуют |
| §8, строка 182 | «Сервис создаёт `ParcelFileDescriptor.createPipe()` и возвращает клиенту дескриптор на чтение» | Наоборот: клиент открывает файл и передаёт сервису write-дескриптор (`MediaBridgeClient.exportMediaBackup(File)` → `handleExportMediaBackup` пишет в `AutoCloseOutputStream`) |
| §8, строка 186 | «prepare распаковывает радио в `custom_radio_next`, commit активирует каталог» | `prepare` кладёт всё в `staging_media_import_<opId>/radio` и только валидирует; `commitMediaImport` каталог не трогает |
| §8, строка 179 | «standalone требует UID или привилегированных прав» | Привилегированного пути нет: `uid == myUid() \|\| packages.contains(packageName)` |

Дополнительно: `docs/architecture-options.md:31` до сих пор держит `plain` как
«Поддерживаемый модульный вариант», хотя flavor удалён. `docs/unified-media-settings-plan.md`
§12 в статусе «Implemented and verified» заявляет «атомарный своп каталога» внутри
`MediaSettingsController.kt` и радио в медиа-экспорте — ни того, ни другого там нет.

### 3.2. `catalogMode` в `media.json` — мёртвый контур

- `MediaSettingsController.kt:600` валидирует `catalogMode`.
- `FullSettingsBackup.createFullBackupZip` его вырезает из экспортируемого `media.json`.
- `commitMediaImport` каталог не применяет.

Итог: ветка «медиа-импорт заменяет радио» не существует, а документация и preview её
обещают. Либо убрать `catalogMode` и `radio/` из настроечного контура совсем, либо
реализовать применение каталога в commit.

### 3.3. Выдуманный `catalogMode` при повторном `prepare`

`MediaSettingsController.kt:297-302`: если `operationId == lastCommitted`, возвращается
`catalogMode = if (preferences.defaultAudioSource.isNotBlank()) "custom" else "builtin"`.
Источник звука не имеет отношения к типу каталога. Должно быть
`radioCatalogRepository.getCatalogInfo().type.name.lowercase()`.
Сейчас безвредно (клиент берёт только токен), но это скрытая мина и ложная ветка в
тесте на идемпотентность.

### 3.4. `updateSettings` не durable

`MediaSettingsController.kt:124-224`: значения пишутся через `.apply()` (строки 190+),
затем `nextRevision()` (строка 223) коммитит **только** `media_settings_meta`.
Смерть процесса между этими шагами оставляет revision выше фактического состояния.
В `commitMediaImport` этот риск специально закрыт (`check(...commit())` по трём файлам),
в обычном апдейте — нет. Нужно либо коммитить до инкремента ревизии, либо явно
задокументировать принятое окно.

### 3.5. Масштаб интерфейса по-прежнему в двух местах

`MediaBridgeService.kt:185-188` при `REGISTER` пишет `preferences.uiScaleTenths = clientScale`.
Виджет владеет `KEY_APP_UI_SCALE_TENTHS` в `Prefs`, и `uiScaleTenths` попадает в
`media.json`. План требовал «единственный источник — масштаб виджета, API-значение
производное, не второе независимое переносимое поле». Согласованность держится только
на том, что REGISTER пересинхронизирует значение. Требуется решение: убрать поле из
`media.json` или признать его переносимым осознанно.

### 3.6. Импорт настроек жёстко падает, если бридж не переподключился

`MainActivity.java:1270`: `applyFullImport` бросает
«Медиасервис недоступен. Импорт не выполнен.» при `!isSettingsSupported()`.
`MediaBridgeClient.stop()` сбрасывает `settingsSupported=false`, а после файлового пикера
идёт `onStop → onStart`. Обычно ре-регистрация успевает, но при быстром подтверждении
диалога staged-файл удаляется и файл приходится выбирать заново. Надёжнее ждать
регистрации с таймаутом.

### 3.7. Радио-импорт из UI идёт по менее строгому пути, чем заявлено

Кнопка радио использует `RadioCatalogRepository.importCustomZip`, а не `validateDirectory`:

- `RadioCatalogRepository.kt:294` — `entry.name.replace('\\', '/').trimStart('/')`
  нормализует потенциально опасный путь вместо отказа;
- нет проверки дублирующихся entries;
- нет отказа на лишние файлы в корне и в `covers/` (это есть только в `validateDirectory`,
  то есть на пути полного бэкапа);
- `staging_radio_*` в `filesDir` не подчищаются, если процесс умер посреди импорта
  (в отличие от `staging_media_import_*`, которые восстанавливаются через
  `loadStagedOperations()`).

### 3.8. Тест соответствия контрактов неполный

`media-core/.../MediaBridgeContractTest.kt` ассертит 14/15 и 114/115, а
`app/.../MediaBridgeContractTest.java` обрывается на `DEFAULT_CATALOG_RESTORED` и
`STATUS_IO_ERROR`. Расхождение Java-копии по радио-сообщениям тест не поймает — а именно
эти номера уже разъехались с документацией.

### 3.9. Мелочь: параллельный `GET_SETTINGS` во время восстановления

`MainActivity.onBridgeState(CONNECTED)` вызывает `checkPendingImportRecovery()`, а сразу
за ним `loadMediaSettings()`. `loadMediaSettings` (строка 1702) не проверяет
`recoveryInProgress`/`importInProgress`, в отличие от `setSettingsTransferEnabled`.
Во время восстановления может параллельно уйти `GET_SETTINGS`.

---

## 4. Открытые вопросы

1. **Полная копия без радио — так задумано?** ZIP называется
   `AtlasMediaWidget-backup.zip`, но радио не содержит, а preview прямо пишет
   «Каталог радио в архиве будет проигнорирован». Если да, стоит убрать разбор `radio/`
   и `catalogMode` из `FullSettingsBackup`, чтобы не было ложного обещания и мёртвого кода.
2. **Масштаб в `media.json`** — оставляем переносимым полем или только в `widget.json`?
3. **Радио-импорт из UI** — свести к строгости `validateDirectory` (отказ на дубли,
   лишние файлы, `..` вместо нормализации) или текущего достаточно?
4. **Импорт при недоступном бридже** — ждать с таймаутом или падать как сейчас?
5. **Документация** — править `full-media-bridge.md` под фактическую реализацию
   (номера 8–15/105–115, write-PFD, отсутствие радио в media-импорте), чистить
   `architecture-options.md` от `plain` и убирать из плана §12 завышенный статус?

## 5. Предлагаемый порядок исправлений

1. Документация под фактический контракт (§3.1) — до коммита, иначе вводит в заблуждение.
2. `catalogMode` и `radio/` в настроечном контуре: решить и либо удалить, либо реализовать
   (§3.2, §3.3).
3. `updateSettings` durability (§3.4).
4. Строгость радио-импорта и очистка `staging_radio_*` (§3.7).
5. Тест соответствия Java/Kotlin-контрактов до 15/115 (§3.8).
6. Решение по масштабу (§3.5) и по поведению при недоступном бридже (§3.6).

## 6. Границы доказательств

Здесь подтверждены чтение кода, статическая сборка и 632 unit/Robolectric-теста.
Не проверялись: реальный Android 11 на 1440×1920, OEM Binder OneOS, радио/BT/USB/Online/
CarPlay, приборная панель, сон/пробуждение, отключение питания и Android process death.

## 7. Повторная валидация и исправления

Исходные разделы выше сохранены как состояние проверки коммита `728aa37`.
Этот раздел уточняет выводы с учётом требований пользователя: настройки и радио
переносятся отдельно, каждый импорт полностью заменяет собственный раздел.

| Пункт | Результат валидации |
| --- | --- |
| 3.1 | Подтверждён. Исправлены таблицы и реальные Bundle-поля в `full-media-bridge.md`, направление write/read-PFD, авторизация, staging и статусы; `plain` помечен удалённым в сравнении архитектур. §12 плана больше не заявляет полное выполнение и гарантию восстановления при отключении питания. |
| 3.2 | Разбор `catalogMode`/`radio/` необходим для совместимости с прежними полными ZIP и проверки их целостности. Это legacy-метаданные, не инструкция заменить каталог. Текущий preview прямо сообщает о сохранении радио; утверждение, что он обещает применение радио, не подтверждено. Удалять reader или возвращать объединённый импорт противоречит уточнению пользователя. |
| 3.3 | Подтверждён и исправлен: повторный prepare сообщает фактический тип каталога. Звуковой источник не определяет каталог. Предложенное `type.name.lowercase()` тоже недостаточно: `BUILT_IN` превращается в `built_in`, а legacy-формат использует `builtin`. |
| 3.4 | Подтверждено отсутствие ожидания дисковой записи перед успешным update. Параметры теперь синхронно сохраняются до публикации ревизии; ошибки записи возвращают FAILED. Порядок синхронных записей исправлен; несколько SharedPreferences всё равно не являются одной дисковой транзакцией. |
| 3.5 | Подтверждён дублирующий перенос масштаба. Производное API-значение исключено из новых медиакопий и не применяется из старых; Widget передаёт свой масштаб при REGISTER. |
| 3.6 | Подтверждён ранний отказ при переподключении после picker. Оба импорта ждут регистрацию вне main с таймаутом 20 секунд до начала изменений. |
| 3.7 | Подтверждено: отдельный ZIP-путь самостоятельно проверял только упомянутые обложки, нормализовал absolute/backslash и не отслеживал дубли. Общий валидатор уже проверял все файлы `covers/`, включая неиспользуемые картинки: эта часть исходного утверждения неточна. Дополнительные валидные изображения допустимы для совместимости. ZIP-путь приведён к общей валидации до свопа; незавершённые `staging_radio_*` очищаются при запуске владельца каталога. |
| 3.8 | Подтверждён. Добавлен прямой Java/Kotlin тест сообщений 14/15, ответов 114/115 и ключа PFD. |
| 3.9 | Подтверждено отсутствие guard для GET_SETTINGS при восстановлении/импорте; guard добавлен. |

Проверки нового состояния:

- `sh gradlew --offline clean check assembleRelease` — успешно; 662 unit/Robolectric
  выполнения по debug/release-наборам, 0 failures/errors. Среди новых проверок —
  порядок дисковых commit и отказы записи, ожидание регистрации/таймаут/stop,
  Java/Kotlin радио-константы, опасные ZIP-пути/дубли/лишние файлы, сохранение
  активного каталога при отказе, полная замена, startup-cleanup и дополнительные
  валидные обложки старых архивов.
- Подписи Widget/API release APK проверены `apksigner verify`; версии на этой
  ветке, отличной от main не увеличивались.
- На эмуляторе Android 11 / 1440×1920 установлен Widget release: старый радио ZIP
  с обложкой импортирован через picker. ZIP с `unexpected.txt` отклонён с конкретной
  ошибкой; CSV и обложка совпали побайтно в экспортах до/после отказа.
- Экспорт настроек содержит widget/media без radio; `uiScaleTenths` присутствует
  только в `widget.json.settings`. Импорт этого ZIP через picker завершился,
  медиасервис подключился; экспорт радио подтвердил сохранение каталога и обложки.
  Тестовый каталог затем сброшен к встроенному.

Ожидание намеренно задержанной регистрации и отказы дискового commit проверены
unit-тестами, не инъекцией сбоя на устройстве. OEM Binder, головное устройство,
физическое отключение питания и смерть процесса в середине записи не проверялись.
Синхронный ACK не превращает несколько preference-файлов в общую транзакцию.
