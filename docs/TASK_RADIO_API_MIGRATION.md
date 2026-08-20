# [RU] Задача: Переход на получение обложек и метаданных радио из AtlasMediaApi
# [EN] Task: Migration to Receiving Radio Artwork and Metadata from AtlasMediaApi

---

## 1. Контекст и цель / Context & Objective

### [RU] Контекст
В сервисе `AtlasMediaApi` (ветка `radiocover`) реализована централизованная поддержка каталогов радиостанций (встроенный каталог Пензы и импорт пользовательских ZIP-архивов). `AtlasMediaApi` теперь автоматически разрешает частоту радиостанции, подставляет её название в `snapshot.title`, диапазон/частоту в `snapshot.artist` и генерирует `FileProvider` URI обложки в `snapshot.artworkUri` с инкрементом `snapshot.artworkRevision`.

В текущей кодовой базе `AtlasMediaWidget` осталась локальная обработка радио (`RadioCatalog`, `RadioCatalogImporter`, локальные `assets/radio/`), из-за чего виджет игнорирует `snapshot.artworkUri` и пытается загружать обложки из собственных локальных файлов.

### [EN] Context
The `AtlasMediaApi` service (`radiocover` branch) now features centralized radio catalog management (built-in Penza catalog and custom ZIP archive imports). `AtlasMediaApi` automatically resolves the radio frequency, sets the station name in `snapshot.title`, band/frequency in `snapshot.artist`, and emits a `FileProvider` artwork URI in `snapshot.artworkUri` with `snapshot.artworkRevision` incrementation.

The current `AtlasMediaWidget` codebase retains legacy local radio handling (`RadioCatalog`, `RadioCatalogImporter`, local `assets/radio/`), which causes the widget to ignore `snapshot.artworkUri` and attempt loading artwork from its own local files.

---

## 2. Необходимые изменения / Required Changes

### [RU] 1. `OverlayService.java`
- В методе `loadArtwork(MediaSnapshot snapshot)` убрать зависимость от локального `RadioCatalog`.
- Загружать `ArtworkRef.mediaUri(snapshot.artworkUri)` для всех источников (включая RADIO), если `snapshot.artworkUri` не пустой.
- Если `snapshot.artworkUri` пустой, отдавать `ArtworkRef.NONE`.

```java
private void loadArtwork(MediaSnapshot snapshot) {
    ArtworkRef artwork = !snapshot.artworkUri.isBlank()
            ? ArtworkRef.mediaUri(snapshot.artworkUri)
            : ArtworkRef.NONE;
    String artworkKey = artwork.cacheKey();
    if (snapshot.artworkRevision == loadedArtworkRevision
            && artworkKey.equals(loadedArtworkKey)) return;
    loadedArtworkRevision = snapshot.artworkRevision;
    loadedArtworkKey = artworkKey;
    expectedArtworkToken = artworkLoader.load(
            artwork, snapshot.generation, snapshot.artworkRevision);
}
```

- В методе `renderSnapshot()` передавать чистый `MediaSnapshot` в карточку без локального `RadioDisplay`:
```java
private void renderSnapshot() {
    MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
    if (visible == null) card.renderDisconnected(stateDetail());
    else card.renderSnapshot(visible, reducer.isConnected());
}
```

### [EN] 1. `OverlayService.java`
- In `loadArtwork(MediaSnapshot snapshot)`, remove dependency on the local `RadioCatalog`.
- Load `ArtworkRef.mediaUri(snapshot.artworkUri)` for all sources (including RADIO) whenever `snapshot.artworkUri` is non-blank.
- If `snapshot.artworkUri` is blank, fallback to `ArtworkRef.NONE`.
- In `renderSnapshot()`, pass the pure `MediaSnapshot` to the card without the local `RadioDisplay`.

---

### [RU] 2. `MediaCardView.java`
- Упростить `renderSnapshot()`: отображать `value.title` и `value.artist` напрямую из `MediaSnapshot` (они уже содержат имя станции и частоту, отформатированные API).
- Убрать перегрузку `renderSnapshot(MediaSnapshot value, boolean bridgeConnected, RadioDisplay radioDisplay)` или сделать её устаревшей.

### [EN] 2. `MediaCardView.java`
- Simplify `renderSnapshot()`: render `value.title` and `value.artist` directly from `MediaSnapshot` (they already contain the station name and frequency formatted by the API).
- Deprecate or remove the `renderSnapshot(..., RadioDisplay radioDisplay)` overload.

---

### [RU] 3. Очистка дублирования / Cleanup of Duplicated Logic
- Удалить локальные assets `app/src/main/assets/radio/` (25 файлов обложек и `stations.csv`), так как они теперь поставляются через `AtlasMediaApi`.
- Удалить или упростить `RadioCatalog.java`, `RadioCatalogCsv.java`, `RadioCatalogImporter.java`.
- Убрать неактуальные настройки пользовательского каталога радио из `MainActivity.java` / `Prefs.java`, так как управление каталогом теперь вынесено в `AtlasMediaApi DiagnosticActivity`.

### [EN] 3. Cleanup of Duplicated Logic
- Remove local assets `app/src/main/assets/radio/` (25 cover files and `stations.csv`), as they are now provided by `AtlasMediaApi`.
- Remove or simplify `RadioCatalog.java`, `RadioCatalogCsv.java`, `RadioCatalogImporter.java`.
- Remove redundant custom radio catalog settings from `MainActivity.java` / `Prefs.java`, since catalog management has moved to `AtlasMediaApi DiagnosticActivity`.

---

## 3. Критерии приёмки / Acceptance Criteria

1. **[RU]** При воспроизведении радио в OneOS виджет отображает обложку, полученную через `snapshot.artworkUri` из `AtlasMediaApi`.
   **[EN]** When playing radio in OneOS, the widget displays the cover art received via `snapshot.artworkUri` from `AtlasMediaApi`.
2. **[RU]** При смене каталога в `AtlasMediaApi` (или выключении тумблера обложек) виджет мгновенно обновляет отображаемую обложку без перезапуска.
   **[EN]** When changing the catalog in `AtlasMediaApi` (or toggling the cover art switch), the widget immediately updates the displayed artwork without requiring a restart.
3. **[RU]** Размер APK виджета уменьшен за счёт удаления дублирующихся WebP-активов.
   **[EN]** The widget APK size is reduced by removing duplicate WebP assets.
