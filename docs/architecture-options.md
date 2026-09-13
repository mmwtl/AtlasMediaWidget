# [RU] Архитектурные варианты AtlasMediaWidget
# [EN] Architecture Options for AtlasMediaWidget

---

## 1. Подтверждённые факты / Confirmed Platform Facts

### [RU]
- Штатная карточка на автомобилях Geely — стандартный Android `AppWidget` из пакета `com.geely.mediawidget`.
- Лаунчер сам закрепляет `SourceBigWidgetProvider` в своей конфигурации, выдаёт право на bind и запрещает пользовательское редактирование карточки.
- Штатная карточка получает данные не через публичную `MediaSession`, а через закрытый OneOS/MediaCenter Binder.
- Для полноценной работы с медиа на платформе необходимо сочетание двух каналов:
  - Публичный `MediaSessionManager` / `MediaController` для Android-плееров (Яндекс Музыка, Spotify и др.);
  - Системный OneOS `MediaCenterManager` для нативных источников (Radio, Bluetooth, USB, CPAA/CarPlay).

### [EN]
- The stock media widget on Geely vehicles is a standard Android `AppWidget` from `com.geely.mediawidget`.
- The OEM launcher pins `SourceBigWidgetProvider` statically in its config, binds to it, and prevents user widget replacement.
- The stock widget fetches media data through private OneOS/MediaCenter Binder rather than standard Android `MediaSession`.
- Full-featured media support on this head unit requires combining two communication channels:
  - Public `MediaSessionManager` / `MediaController` for Android players (Yandex Music, Spotify, etc.);
  - System OneOS `MediaCenterManager` for hardware sources (Radio, Bluetooth, USB, CPAA/CarPlay).

---

## 2. Сравнение архитектурных вариантов / Comparison of Architecture Options

| Вариант / Option | Преимущества / Pros | Недостатки / Cons | Статус / Status |
|---|---|---|---|
| **1. Overlay + встроенный рантайм (`integrated`)** | Единый all-in-one APK; медиабэкенд изолирован в фоновом процессе `:media`; сохраняется чистый Messenger-контракт; независимый перезапуск при сбоях | Увеличивает размер одного APK | **Основной стандартный вариант / Primary Standard** |
| **2. Overlay + автономный сервис (`plain` + `:api-app`)** | Модульность; независимое обновление UI-оверлея и бэкенда; доступность бэкенда другим приложениям | Требует установки двух отдельных APK и настройки двух наборов разрешений | **Исторический вариант: plain удалён; :api-app доступен отдельно / Historical: plain removed; standalone API still builds** |
| **3. Overlay + legacy broadcasts GInputBridge** | Простая миграция со старых прототипов | Нет атомарности, управления воспроизведением, перемотки и безопасной передачи обложек | **Устаревший / Deprecated Legacy** |
| **4. Overlay + прямой notification listener в виджете** | Нет зависимости от отдельного сервиса | Необходимость дублирования логики сессий; без OneOS невозможно надежно определить активный аппаратный источник | **Не используется / Rejected** |
| **5. Сторонний `AppWidgetProvider`** | Штатный жизненный цикл `AppWidget` без overlay-окна | OEM-лаунчер жестко фиксирует свой виджет и не позволяет добавлять сторонние провайдеры на главный экран | **Технически невозможно на штатном лаунчере / Not Supported by OEM** |

---

## 3. Выбор целевой модели / Architectural Decisions

### [RU]
1. **Изоляция процесса `:media`**: В варианте `integrated` медиасервис вынесен в отдельный процесс `android:process=":media"`. Это предотвращает влияние возможных задержек в системных вызовах OneOS Binder на плавность отрисовки и анимации UI-оверлея.
2. **Атомарный снимок (`MediaSnapshot`)**: Клиент никогда не собирает состояние по кусочкам из разрозненных callback-вызовов. Сервис публикует монолитный объект `MediaSnapshot` с монотонным счетчиком `generation`.
3. **Локальная экстраполяция прогресса**: Клиент локально вычисляет текущую позицию воспроизведения на основе `position`, `speed` и `updateElapsedRealtime`, исключая высокочастотный IPC-трафик между процессами.
4. **Управление обложками через `FileProvider`**: Обложки нормализуются до 512 px, сохраняются во внутреннем кэше и предоставляются клиенту через URI с временными правами доступа, которые автоматически отзываются при разрыве соединения.

### [EN]
1. **Isolated `:media` Process**: In the `integrated` flavor, the media service runs in a dedicated `android:process=":media"`. This isolates any potential Binder latency or delays in OEM calls from affecting UI rendering smoothness.
2. **Atomic `MediaSnapshot`**: Clients never assemble media state from disparate callbacks. The backend emits an atomic `MediaSnapshot` payload stamped with a monotonic `generation` counter.
3. **Local Progress Extrapolation**: Clients extrapolate current playback position locally using `position`, `speed`, and `updateElapsedRealtime`, eliminating continuous 1 Hz IPC chatter over Binder.
4. **`FileProvider` Artwork Pipeline**: Artwork is downscaled to 512 px, stored in private cache, and shared with clients via URIs accompanied by temporary read grants that are cleanly revoked upon client unregistration or crash.
