package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class RadioCatalogType {
    BUILT_IN,
    CUSTOM,
}

/** One catalog the user can activate: the built-in one, an imported ZIP or a city of a pack. */
data class RadioCatalogEntry(
    val id: String,
    val name: String,
    val stationCount: Int,
    val builtIn: Boolean,
    /** The pack this catalog came from, or empty for a catalog imported on its own. */
    val packId: String = "",
    val packName: String = "",
    val region: String = "",
)

/** Catalogs created by one import: a single catalog, or every city of a pack. */
data class RadioCatalogImport(
    val catalogs: List<RadioCatalogEntry>,
    val packId: String = "",
    val packName: String = "",
) {
    val stationCount: Int get() = catalogs.sumOf(RadioCatalogEntry::stationCount)
}

data class RadioCatalogInfo(
    val type: RadioCatalogType,
    val stationCount: Int,
    val isWidgetBroadcastEnabled: Boolean,
    val description: String,
    val activeId: String = RadioCatalogRepository.BUILT_IN_ID,
    val catalogs: List<RadioCatalogEntry> = emptyList(),
)

/**
 * Keeps several named radio catalogs and serves the active one.
 *
 * Layout under filesDir:
 * - `radio_catalogs/index.json` (AtomicFile) names the catalogs, their order and the active one;
 * - `radio_catalogs/<id>/stations.csv` and `covers.json` (cover file name -> stored cover);
 * - `radio_covers/<sha256>.<ext>` holds every cover once, shared by all catalogs.
 *
 * A pack ZIP (`pack.json`, city CSV files, one shared `covers/`) becomes one catalog per city;
 * a pack is imported, updated and deleted as a whole.
 *
 * The index is the commit point: a catalog directory or cover that it does not reference is
 * removed at startup, so an interrupted import or delete leaves the previous state intact.
 */
class RadioCatalogRepository(
    private val context: Context,
) {
    companion object {
        const val PREFS_NAME = "radio_catalog_prefs"
        // Keep the legacy preference value so existing installations preserve the switch state.
        const val KEY_WIDGET_BROADCAST_ENABLED = "radio_covers_enabled"
        /** Legacy single-catalog flag, read once while migrating `custom_radio`. */
        const val KEY_CUSTOM_CATALOG_ACTIVE = "custom_catalog_active"

        const val BUILT_IN_ID = "builtin"
        const val BUILT_IN_NAME = "Встроенный (Пенза)"
        const val MIGRATED_CATALOG_NAME = "Мой каталог"
        const val DEFAULT_CATALOG_NAME = "Каталог"

        const val BUILT_IN_DIR = "radio"
        const val MANIFEST_NAME = "stations.csv"
        const val COVERS_DIR = "covers"
        const val CATALOGS_DIR = "radio_catalogs"
        const val COVER_STORE_DIR = "radio_covers"
        const val INDEX_NAME = "index.json"
        const val COVER_MAP_NAME = "covers.json"
        const val LEGACY_DIR = "custom_radio"

        const val PACK_MANIFEST_NAME = "pack.json"
        const val PACK_FORMAT = "atlas-radio-pack"
        const val PACK_VERSION = 1
        const val PACK_CITIES_DIR = "cities"

        const val MAX_CUSTOM_CATALOGS = 64
        const val MAX_CATALOG_NAME_LENGTH = 40
        const val MAX_COVER_STORE_BYTES = 128L * 1024L * 1024L // 128 MB
        const val MAX_ZIP_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L // 64 MB
        const val MAX_ZIP_ENTRIES = 600

        private val CATALOG_ID = Regex("^[0-9a-f]{32}$")
        private val STORED_COVER = Regex("^[0-9a-f]{64}\\.(png|jpg|webp)$")
        private val SAFE_COVER_NAME = Regex("^[a-zA-Z0-9._-]+$")
        private val PACK_KEY = Regex("^[a-z0-9][a-z0-9_-]{0,39}$")
        private val PACK_CSV_PATH = Regex("^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*\\.csv$")

        fun isValidCatalogId(id: String?): Boolean =
            id == BUILT_IN_ID || (id != null && CATALOG_ID.matches(id))

        /** Turns control characters into spaces, collapses spaces, trims and bounds the length. */
        fun normalizeName(raw: String?): String =
            raw.orEmpty()
                .map { if (it.isISOControl()) ' ' else it }
                .joinToString("")
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(MAX_CATALOG_NAME_LENGTH)
                .trim()

        /** Returns [base], or "[base] (n)" when another catalog already uses the name. */
        fun uniqueName(base: String, taken: Collection<String>): String {
            val used = taken.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
            if (base.lowercase(Locale.ROOT) !in used) return base
            var index = 2
            while (true) {
                val suffix = " ($index)"
                val candidate = base.take(MAX_CATALOG_NAME_LENGTH - suffix.length).trimEnd() + suffix
                if (candidate.lowercase(Locale.ROOT) !in used) return candidate
                index++
            }
        }
    }

    private data class IndexEntry(
        val id: String,
        val name: String,
        val stationCount: Int,
        val packId: String = "",
        val packName: String = "",
        val key: String = "",
        val region: String = "",
    ) {
        fun toEntry() = RadioCatalogEntry(id, name, stationCount, builtIn = false, packId, packName, region)
    }

    private class PackCatalog(
        val key: String,
        val name: String,
        val region: String,
        val manifest: File,
        val stations: List<RadioStation>,
    )

    private class RadioCatalogPack(val id: String, val name: String, val catalogs: List<PackCatalog>)

    private data class CatalogIndex(val activeId: String, val entries: List<IndexEntry>)

    private class ActiveCatalog(
        val id: String,
        val name: String,
        val type: RadioCatalogType,
        val stations: Map<Int, RadioStation>,
        /** Cover file name from stations.csv -> stored cover name; empty for the built-in catalog. */
        val storedCovers: Map<String, String>,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val catalogsDirectory: File = File(context.filesDir, CATALOGS_DIR)
    private val coverStore: File = File(context.filesDir, COVER_STORE_DIR)
    private val indexFile = AtomicFile(File(catalogsDirectory, INDEX_NAME))
    private val widgetCoverDirectory: File = File(context.cacheDir, "radio_station_covers")

    @Volatile
    private var index: CatalogIndex = CatalogIndex(BUILT_IN_ID, emptyList())

    @Volatile
    private var active: ActiveCatalog = ActiveCatalog(
        BUILT_IN_ID, BUILT_IN_NAME, RadioCatalogType.BUILT_IN, emptyMap(), emptyMap(),
    )

    /** Changes whenever another catalog (or another version of one) becomes active. */
    @Volatile
    var generation: Long = 0L
        private set

    @Volatile
    var isWidgetBroadcastEnabled: Boolean = prefs.getBoolean(KEY_WIDGET_BROADCAST_ENABLED, true)
        private set

    private val builtInStationCount: Int by lazy { loadBuiltIn().stations.size }

    private val _catalogInfoFlow = MutableStateFlow(buildCatalogInfo())
    val catalogInfoFlow: StateFlow<RadioCatalogInfo> = _catalogInfoFlow.asStateFlow()

    init {
        context.filesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("staging_radio_") }
            ?.forEach { it.deleteRecursively() }
        synchronized(this) {
            catalogsDirectory.mkdirs()
            coverStore.mkdirs()
            index = loadOrMigrateIndex()
            removeUnreferencedFiles()
            reloadCatalog()
        }
    }

    fun getCatalogInfo(): RadioCatalogInfo = _catalogInfoFlow.value

    fun setWidgetBroadcastEnabled(enabled: Boolean) {
        isWidgetBroadcastEnabled = enabled
        prefs.edit().putBoolean(KEY_WIDGET_BROADCAST_ENABLED, enabled).apply()
        _catalogInfoFlow.value = buildCatalogInfo()
    }

    @Synchronized
    fun reloadCatalog() {
        widgetCoverDirectory.deleteRecursively()
        val current = index
        val entry = current.entries.firstOrNull { it.id == current.activeId }
        active = entry
            ?.let { runCatching { loadCustom(it) }.onFailure(Timber::e).getOrNull() }
            ?: loadBuiltIn()
        generation++
        _catalogInfoFlow.value = buildCatalogInfo()
    }

    fun lookup(frequencyKHz: Int): RadioStation? = active.stations[frequencyKHz]

    /** Immutable active catalog for clients that need to present every imported station. */
    fun stations(): List<RadioStation> = active.stations.values.sortedBy(RadioStation::frequencyKHz)

    fun artworkUri(station: RadioStation): String {
        if (station.coverFileName.isBlank()) return ""
        val catalog = active
        val publishedName = when (catalog.type) {
            RadioCatalogType.BUILT_IN -> station.coverFileName
                .takeIf(SAFE_COVER_NAME::matches)
                ?.let { "builtin_$it" }
            // A stored cover is named by its content, so the same picture shared by several
            // catalogs keeps one URI and a different picture never reuses a stale one.
            RadioCatalogType.CUSTOM -> catalog.storedCovers[station.coverFileName]
        } ?: return ""
        val target = File(widgetCoverDirectory, publishedName)
        return runCatching {
            widgetCoverDirectory.mkdirs()
            if (!target.isFile || target.length() <= 0L) {
                val temporary = File.createTempFile("cover-", ".tmp", widgetCoverDirectory)
                try {
                    openCoverStream(station, catalog)?.use { input ->
                        FileOutputStream(temporary).use { output -> input.copyTo(output) }
                    } ?: return ""
                    check(temporary.length() > 0L) { "Пустая обложка ${station.coverFileName}" }
                    if (!temporary.renameTo(target)) {
                        check(target.isFile && target.length() > 0L) {
                            "Не удалось опубликовать обложку ${station.coverFileName}"
                        }
                    }
                } finally {
                    temporary.delete()
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            ).toString()
        }.onFailure(Timber::e).getOrDefault("")
    }

    fun openCoverStream(station: RadioStation): InputStream? = openCoverStream(station, active)

    private fun openCoverStream(station: RadioStation, catalog: ActiveCatalog): InputStream? {
        if (station.coverFileName.isBlank()) return null
        return when (catalog.type) {
            RadioCatalogType.BUILT_IN -> runCatching {
                context.assets.open("$BUILT_IN_DIR/$COVERS_DIR/${station.coverFileName}")
            }.getOrNull()
            RadioCatalogType.CUSTOM -> {
                val stored = catalog.storedCovers[station.coverFileName]
                    ?.takeIf(STORED_COVER::matches)
                    ?: return null
                val file = File(coverStore, stored)
                if (file.isFile && file.length() > 0L) {
                    runCatching { FileInputStream(file) }.getOrNull()
                } else null
            }
        }
    }

    /** Makes the built-in catalog active; imported catalogs stay available. */
    fun restoreDefaultCatalog() {
        activateCatalog(BUILT_IN_ID)
    }

    @Synchronized
    fun activateCatalog(id: String) {
        val current = index
        require(id == BUILT_IN_ID || current.entries.any { it.id == id }) { "Каталог не найден" }
        if (current.activeId == id && active.id == id) return
        commitIndex(current.copy(activeId = id))
        reloadCatalog()
    }

    @Synchronized
    fun renameCatalog(id: String, name: String) {
        require(id != BUILT_IN_ID) { "Встроенный каталог нельзя переименовать" }
        val current = index
        val entry = current.entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Каталог не найден")
        require(entry.packId.isEmpty()) { "Названия городов задаёт набор «${entry.packName}»" }
        val normalized = normalizeName(name)
        require(normalized.isNotEmpty()) { "Название каталога не может быть пустым" }
        val taken = singleNames(current, exceptId = id)
        require(taken.none { it.equals(normalized, ignoreCase = true) }) {
            "Каталог с названием «$normalized» уже есть"
        }
        if (entry.name == normalized) return
        commitIndex(current.copy(entries = current.entries.map {
            if (it.id == id) it.copy(name = normalized) else it
        }))
        if (active.id == id) {
            reloadCatalog()
        } else {
            _catalogInfoFlow.value = buildCatalogInfo()
        }
    }

    /** Deletes an imported catalog; deleting the active one makes the built-in catalog active. */
    @Synchronized
    fun deleteCatalog(id: String) {
        require(id != BUILT_IN_ID) { "Встроенный каталог нельзя удалить" }
        val entry = index.entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Каталог не найден")
        require(entry.packId.isEmpty()) { "Город удаляется вместе с набором «${entry.packName}»" }
        removeEntries { it.id == id }
    }

    /** Deletes the whole pack that the catalog [id] belongs to. */
    @Synchronized
    fun deletePackOf(id: String) {
        val entry = index.entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Каталог не найден")
        require(entry.packId.isNotEmpty()) { "Каталог не входит в набор" }
        removeEntries { it.packId == entry.packId }
    }

    private fun removeEntries(predicate: (IndexEntry) -> Boolean) {
        val current = index
        val wasActive = current.entries.any { it.id == current.activeId && predicate(it) }
        commitIndex(
            CatalogIndex(
                activeId = if (wasActive) BUILT_IN_ID else current.activeId,
                entries = current.entries.filterNot(predicate),
            ),
        )
        removeUnreferencedFiles()
        if (wasActive) reloadCatalog() else _catalogInfoFlow.value = buildCatalogInfo()
    }

    /** Names a catalog imported on its own must not repeat; pack cities keep their pack's names. */
    private fun singleNames(current: CatalogIndex, exceptId: String? = null): List<String> =
        current.entries.filter { it.packId.isEmpty() && it.id != exceptId }.map { it.name } + BUILT_IN_NAME

    private fun validateCoverFile(coverFile: File, coverName: String) {
        if (!SAFE_COVER_NAME.matches(coverName) || coverName == "." || coverName == "..") {
            throw IllegalArgumentException("Небезопасное имя файла обложки: $coverName")
        }
        val ext = coverFile.extension.lowercase(Locale.ROOT)
        if (ext !in listOf("png", "jpg", "jpeg", "webp")) {
            throw IllegalArgumentException("Неподдерживаемый формат обложки: $coverName ($ext)")
        }
        if (!coverFile.isFile || coverFile.length() <= 0L) {
            throw IllegalArgumentException("Файл обложки отсутствует или пуст: $coverName")
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        coverFile.inputStream().buffered().use { fis ->
            android.graphics.BitmapFactory.decodeStream(fis, null, bounds)
        }
        val width = bounds.outWidth
        val height = bounds.outHeight
        val mime = bounds.outMimeType?.lowercase(Locale.ROOT).orEmpty()
        if (width in 32..4096 && height in 32..4096) {
            if (mime.isNotEmpty() && mime !in listOf("image/png", "image/jpeg", "image/webp")) {
                throw IllegalArgumentException("Недопустимый MIME-тип обложки $coverName: $mime")
            }
            return
        }
        throw IllegalArgumentException("Недопустимый размер обложки $coverName: ${width}x${height} (требуется от 32 до 4096 px)")
    }

    /** Validates a catalog directory without changing the active catalog or preferences. */
    fun validateDirectory(sourceDir: File): Result<Int> = runCatching {
        if (!sourceDir.isDirectory) throw IllegalArgumentException("Каталог radio отсутствует")
        val manifestFile = File(sourceDir, MANIFEST_NAME)
        if (!manifestFile.isFile) throw IllegalArgumentException("В каталоге отсутствует $MANIFEST_NAME")
        val parsedStations = InputStreamReader(FileInputStream(manifestFile), StandardCharsets.UTF_8).use {
            RadioCatalogCsv.read(it)
        }
        val coversDir = File(sourceDir, COVERS_DIR)
        for (station in parsedStations) {
            val coverName = station.coverFileName
            if (coverName.isNotBlank()) {
                val coverFile = File(coversDir, coverName)
                if (coverFile.canonicalFile.parentFile != coversDir.canonicalFile) {
                    throw SecurityException("Небезопасный путь к обложке: $coverName")
                }
                validateCoverFile(coverFile, coverName)
            }
        }
        if (coversDir.exists() && !coversDir.isDirectory) {
            throw IllegalArgumentException("Путь covers не является каталогом")
        }
        coversDir.listFiles()?.forEach { file ->
            if (!file.isFile || file.canonicalFile.parentFile != coversDir.canonicalFile) {
                throw IllegalArgumentException("Недопустимый объект в каталоге обложек: ${file.name}")
            }
            validateCoverFile(file, file.name)
        }
        sourceDir.listFiles()?.forEach { child ->
            if (child.name != MANIFEST_NAME && child.name != COVERS_DIR) {
                throw IllegalArgumentException("Недопустимый файл в каталоге radio: ${child.name}")
            }
        }
        parsedStations.size
    }

    /** Imports a ZIP as a new active catalog (or a pack); kept for diagnostics. */
    fun importCustomZip(inputStream: InputStream): Result<Int> =
        importZip(inputStream).map(RadioCatalogImport::stationCount)

    /** Imports a single-catalog ZIP; see [importZip]. */
    fun importCatalogZip(
        inputStream: InputStream,
        name: String? = null,
        replaceId: String? = null,
        activate: Boolean = true,
    ): Result<RadioCatalogEntry> =
        importZip(inputStream, name, replaceId, activate).map { it.catalogs.first() }

    /**
     * Imports a catalog ZIP or a pack ZIP (one with `pack.json`).
     *
     * A single catalog without [replaceId] becomes a new catalog; with it, it replaces that
     * catalog's stations and covers and keeps its name unless [name] is given. It becomes active
     * when [activate] is set or when it replaces the active one.
     *
     * A pack adds one catalog per city, or replaces every city of the installed pack with the same
     * id. The active city stays active by its key; a new pack does not change the active catalog.
     */
    @Synchronized
    fun importZip(
        inputStream: InputStream,
        name: String? = null,
        replaceId: String? = null,
        activate: Boolean = true,
    ): Result<RadioCatalogImport> = runCatching {
        val stagingDir = File(context.filesDir, "staging_radio_${System.currentTimeMillis()}")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        try {
            extractZip(inputStream, stagingDir)
            if (File(stagingDir, PACK_MANIFEST_NAME).exists()) {
                require(replaceId == null) { "Набор нельзя загрузить вместо отдельного каталога" }
                importPack(stagingDir)
            } else {
                RadioCatalogImport(listOf(importSingle(stagingDir, name, replaceId, activate)))
            }
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun importSingle(
        stagingDir: File,
        name: String?,
        replaceId: String?,
        activate: Boolean,
    ): RadioCatalogEntry {
        val current = index
        val replaced = replaceId?.let { id ->
            require(id != BUILT_IN_ID) { "Встроенный каталог нельзя заменить" }
            val entry = current.entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Каталог не найден")
            require(entry.packId.isEmpty()) { "Город обновляется вместе с набором «${entry.packName}»" }
            entry
        }
        if (replaced == null && current.entries.size >= MAX_CUSTOM_CATALOGS) {
            throw IllegalStateException(catalogLimitMessage())
        }
        validateDirectory(stagingDir).getOrThrow()

        val otherNames = singleNames(current, exceptId = replaceId)
        val requested = normalizeName(name)
        val finalName = when {
            requested.isNotEmpty() -> uniqueName(requested, otherNames)
            replaced != null -> replaced.name
            else -> uniqueName(DEFAULT_CATALOG_NAME, otherNames)
        }
        val entry = storeCatalog(stagingDir, finalName)
        val entries = if (replaced != null) {
            current.entries.map { if (it.id == replaced.id) entry else it }
        } else {
            current.entries + entry
        }
        val activeId = when {
            activate -> entry.id
            replaced != null && current.activeId == replaced.id -> entry.id
            else -> current.activeId
        }
        commitImported(CatalogIndex(activeId, entries), listOf(entry), reload = replaced?.id == active.id)
        return entry.toEntry()
    }

    private fun importPack(stagingDir: File): RadioCatalogImport {
        val pack = validatePackDirectory(stagingDir)
        val current = index
        val firstOld = current.entries.indexOfFirst { it.packId == pack.id }
        val kept = current.entries.filter { it.packId != pack.id }
        if (kept.size + pack.catalogs.size > MAX_CUSTOM_CATALOGS) {
            throw IllegalStateException(catalogLimitMessage())
        }
        val coverMap = storeCovers(File(stagingDir, COVERS_DIR))
        val created = ArrayList<IndexEntry>(pack.catalogs.size)
        try {
            pack.catalogs.forEach { city ->
                val referenced = city.stations.mapNotNull { station ->
                    station.coverFileName.takeIf(String::isNotBlank)?.let { it to coverMap.getValue(it) }
                }.toMap()
                created += IndexEntry(
                    id = writeCatalogDirectory(city.manifest, referenced),
                    name = city.name,
                    stationCount = city.stations.size,
                    packId = pack.id,
                    packName = pack.name,
                    key = city.key,
                    region = city.region,
                )
            }
        } catch (error: Exception) {
            created.forEach { File(catalogsDirectory, it.id).deleteRecursively() }
            removeUnreferencedFiles()
            throw error
        }
        val entries = if (firstOld < 0) kept + created else {
            kept.subList(0, firstOld) + created + kept.subList(firstOld, kept.size)
        }
        val previousActive = current.entries.firstOrNull { it.id == current.activeId }
        val activeId = if (previousActive?.packId == pack.id) {
            created.firstOrNull { it.key == previousActive.key }?.id ?: BUILT_IN_ID
        } else current.activeId
        commitImported(CatalogIndex(activeId, entries), created, reload = previousActive?.packId == pack.id)
        return RadioCatalogImport(created.map(IndexEntry::toEntry), pack.id, pack.name)
    }

    /** Commits an index that references the freshly written [created] catalogs. */
    private fun commitImported(next: CatalogIndex, created: List<IndexEntry>, reload: Boolean) {
        try {
            commitIndex(next)
        } catch (error: Exception) {
            created.forEach { File(catalogsDirectory, it.id).deleteRecursively() }
            removeUnreferencedFiles()
            throw error
        }
        removeUnreferencedFiles()
        if (reload || active.id != next.activeId) {
            reloadCatalog()
        } else {
            _catalogInfoFlow.value = buildCatalogInfo()
        }
    }

    private fun catalogLimitMessage() =
        "Можно хранить не больше $MAX_CUSTOM_CATALOGS каталогов вместе с городами наборов. " +
            "Удалите ненужный каталог или набор."

    /** Validates an extracted pack: its manifest, every city CSV and every cover. */
    private fun validatePackDirectory(dir: File): RadioCatalogPack {
        val json = runCatching {
            JSONObject(File(dir, PACK_MANIFEST_NAME).readText(StandardCharsets.UTF_8))
        }.getOrElse { throw IllegalArgumentException("$PACK_MANIFEST_NAME повреждён: ${it.message}") }
        require(json.optString("format") == PACK_FORMAT) { "$PACK_MANIFEST_NAME: ожидается format \"$PACK_FORMAT\"" }
        require(json.optInt("version", -1) == PACK_VERSION) { "$PACK_MANIFEST_NAME: неподдерживаемая версия" }
        val id = json.optString("id")
        require(PACK_KEY.matches(id)) {
            "$PACK_MANIFEST_NAME: id должен состоять из строчных латинских букв, цифр, «-» и «_»"
        }
        val name = normalizeName(json.optString("name"))
        require(name.isNotEmpty()) { "$PACK_MANIFEST_NAME: не указано название набора" }
        val array = json.optJSONArray("catalogs")
        require(array != null && array.length() in 1..MAX_CUSTOM_CATALOGS) {
            "$PACK_MANIFEST_NAME: в наборе должно быть от 1 до $MAX_CUSTOM_CATALOGS городов"
        }

        val root = dir.canonicalFile
        val coversDir = File(dir, COVERS_DIR)
        val keys = HashSet<String>()
        val manifests = HashSet<File>()
        val catalogs = ArrayList<PackCatalog>(array.length())
        for (position in 0 until array.length()) {
            val item = array.optJSONObject(position)
                ?: throw IllegalArgumentException("$PACK_MANIFEST_NAME: город ${position + 1} описан неверно")
            val key = item.optString("key")
            require(PACK_KEY.matches(key)) {
                "$PACK_MANIFEST_NAME: key города ${position + 1} должен состоять из строчных латинских букв, цифр, «-» и «_»"
            }
            require(keys.add(key)) { "$PACK_MANIFEST_NAME: повторяется key «$key»" }
            val cityName = normalizeName(item.optString("name"))
            require(cityName.isNotEmpty()) { "$PACK_MANIFEST_NAME: у города «$key» нет названия" }
            val path = item.optString("file")
            require(PACK_CSV_PATH.matches(path)) { "$PACK_MANIFEST_NAME: недопустимый путь «$path» у города «$key»" }
            val manifest = File(dir, path).canonicalFile
            require(manifest.path.startsWith(root.path + File.separator) && manifest.isFile) {
                "В наборе нет файла $path"
            }
            require(manifests.add(manifest)) { "$PACK_MANIFEST_NAME: файл $path указан у нескольких городов" }
            val stations = runCatching {
                InputStreamReader(FileInputStream(manifest), StandardCharsets.UTF_8).use { RadioCatalogCsv.read(it) }
            }.getOrElse { throw IllegalArgumentException("$path: ${it.message}") }
            require(stations.isNotEmpty()) { "$path: нет станций" }
            stations.forEach { station ->
                val cover = station.coverFileName
                if (cover.isNotBlank()) {
                    require(SAFE_COVER_NAME.matches(cover) && File(coversDir, cover).isFile) {
                        "$path: нет обложки $cover в папке $COVERS_DIR"
                    }
                }
            }
            catalogs += PackCatalog(key, cityName, normalizeName(item.optString("region")), manifest, stations)
        }

        val canonicalCovers = coversDir.canonicalFile
        dir.walkTopDown().filter(File::isFile).forEach { file ->
            val canonical = file.canonicalFile
            when {
                canonical == File(root, PACK_MANIFEST_NAME) -> Unit
                canonical in manifests -> Unit
                canonical.parentFile == canonicalCovers -> validateCoverFile(canonical, canonical.name)
                else -> throw IllegalArgumentException(
                    "Недопустимый файл в наборе: ${canonical.relativeTo(root).path}",
                )
            }
        }
        return RadioCatalogPack(id, name, catalogs)
    }

    private fun extractZip(inputStream: InputStream, targetDir: File) {
        var totalExtractedBytes = 0L
        var totalEntries = 0
        val stagingCanonical = targetDir.canonicalPath
        val extractedCanonicalPaths = HashSet<String>()

        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                totalEntries++
                if (totalEntries > MAX_ZIP_ENTRIES) {
                    throw IllegalStateException("Превышено максимальное количество файлов в архиве ($MAX_ZIP_ENTRIES)")
                }

                val entryName = entry.name
                val pathSegments = entryName.split('/')
                if (entryName.contains('\\') || entryName.startsWith('/') ||
                    entryName.matches(Regex("^[A-Za-z]:(/.*)?$")) ||
                    pathSegments.any { it == "." || it == ".." }
                ) {
                    throw SecurityException("Небезопасный путь в ZIP архиве: $entryName")
                }

                val targetFile = File(targetDir, entryName)
                val targetCanonical = targetFile.canonicalPath
                val isSafe = targetCanonical == stagingCanonical ||
                    targetCanonical.startsWith(stagingCanonical + File.separator)
                if (!isSafe) {
                    throw SecurityException("Небезопасный путь в ZIP архиве: $entryName")
                }
                if (!extractedCanonicalPaths.add(targetCanonical)) {
                    throw IllegalArgumentException("Дублирующийся путь в ZIP архиве: $entryName")
                }

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            totalExtractedBytes += read
                            if (totalExtractedBytes > MAX_ZIP_UNCOMPRESSED_BYTES) {
                                throw IllegalStateException("Превышен допустимый размер распакованного архива (64 МБ)")
                            }
                            fos.write(buffer, 0, read)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Stores a single-catalog directory (stations.csv and covers/) under a new catalog id. */
    private fun storeCatalog(sourceDir: File, name: String): IndexEntry {
        val manifest = File(sourceDir, MANIFEST_NAME)
        val stations = InputStreamReader(FileInputStream(manifest), StandardCharsets.UTF_8)
            .use { RadioCatalogCsv.read(it) }
        require(stations.isNotEmpty()) { "Каталог не содержит станций" }
        val coverMap = storeCovers(File(sourceDir, COVERS_DIR))
        return IndexEntry(writeCatalogDirectory(manifest, coverMap), name, stations.size)
    }

    /**
     * Copies every cover of [coversDir] to the shared store by content hash and returns the map
     * cover file name -> stored name. A cover another catalog already uses is not stored again.
     */
    private fun storeCovers(coversDir: File): Map<String, String> {
        val covers = coversDir.listFiles()
            .orEmpty()
            .filter { it.isFile && SAFE_COVER_NAME.matches(it.name) && it.length() > 0L }
            .sortedBy(File::getName)
            .mapNotNull { file ->
                val extension = storedExtension(file.name) ?: return@mapNotNull null
                Triple(file, "${sha256(file)}.$extension", file.length())
            }
        val storedBytes = coverStore.listFiles().orEmpty().filter(File::isFile).sumOf(File::length)
        val addedBytes = covers.distinctBy { it.second }
            .filterNot { File(coverStore, it.second).isFile }
            .sumOf { it.third }
        if (storedBytes + addedBytes > MAX_COVER_STORE_BYTES) {
            throw IllegalStateException(
                "Недостаточно места для обложек: все каталоги вместе могут занимать до " +
                    "${MAX_COVER_STORE_BYTES / (1024L * 1024L)} МБ. Удалите ненужный каталог.",
            )
        }
        val coverMap = linkedMapOf<String, String>()
        covers.forEach { (file, stored, _) ->
            storeCover(file, stored)
            coverMap[file.name] = stored
        }
        return coverMap
    }

    /** Writes a catalog directory under a new id and returns the id. */
    private fun writeCatalogDirectory(manifest: File, coverMap: Map<String, String>): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val temporary = File(catalogsDirectory, ".$id.tmp")
        temporary.deleteRecursively()
        check(temporary.mkdirs()) { "Не удалось подготовить каталог" }
        try {
            manifest.copyTo(File(temporary, MANIFEST_NAME))
            val map = JSONObject()
            coverMap.forEach { (coverName, stored) -> map.put(coverName, stored) }
            File(temporary, COVER_MAP_NAME).writeText(map.toString(), StandardCharsets.UTF_8)
            check(temporary.renameTo(File(catalogsDirectory, id))) { "Не удалось сохранить каталог" }
        } catch (error: Exception) {
            temporary.deleteRecursively()
            throw error
        }
        return id
    }

    private fun storeCover(source: File, storedName: String) {
        val target = File(coverStore, storedName)
        if (target.isFile && target.length() == source.length()) return
        coverStore.mkdirs()
        val temporary = File.createTempFile(".cover-", ".tmp", coverStore)
        try {
            source.copyTo(temporary, overwrite = true)
            if (!temporary.renameTo(target)) {
                check(target.isFile && target.length() == source.length()) {
                    "Не удалось сохранить обложку ${source.name}"
                }
            }
        } finally {
            temporary.delete()
        }
    }

    private fun storedExtension(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> "png"
            "jpg", "jpeg" -> "jpg"
            "webp" -> "webp"
            else -> null
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Exports [id] (the active catalog by default) in the original ZIP layout. */
    @Synchronized
    fun exportCatalogZip(outputStream: OutputStream, id: String = active.id) {
        if (id == BUILT_IN_ID) {
            exportSampleZip(outputStream)
            return
        }
        val entry = index.entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Каталог не найден")
        if (entry.packId.isNotEmpty()) {
            exportPackZip(outputStream, entry.packId)
            return
        }
        val directory = File(catalogsDirectory, entry.id)
        val storedCovers = readCoverMap(directory)
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            File(directory, MANIFEST_NAME).inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            storedCovers.forEach { (coverName, stored) ->
                val file = File(coverStore, stored)
                if (STORED_COVER.matches(stored) && file.isFile && file.length() > 0L) {
                    zos.putNextEntry(ZipEntry("$COVERS_DIR/$coverName"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    /** Rebuilds the pack ZIP: pack.json, one CSV per city and the covers they use. */
    private fun exportPackZip(outputStream: OutputStream, packId: String) {
        val cities = index.entries.filter { it.packId == packId }
        val covers = linkedMapOf<String, String>()
        cities.forEach { city ->
            readCoverMap(File(catalogsDirectory, city.id)).forEach { (name, stored) -> covers.putIfAbsent(name, stored) }
        }
        val manifest = JSONObject().apply {
            put("format", PACK_FORMAT)
            put("version", PACK_VERSION)
            put("id", packId)
            put("name", cities.first().packName)
            put("catalogs", JSONArray().apply {
                cities.forEach { city ->
                    put(JSONObject().apply {
                        put("key", city.key)
                        put("name", city.name)
                        if (city.region.isNotEmpty()) put("region", city.region)
                        put("file", "$PACK_CITIES_DIR/${city.key}.csv")
                    })
                }
            })
        }
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            zos.putNextEntry(ZipEntry(PACK_MANIFEST_NAME))
            zos.write(manifest.toString(2).toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            cities.forEach { city ->
                zos.putNextEntry(ZipEntry("$PACK_CITIES_DIR/${city.key}.csv"))
                File(catalogsDirectory, "${city.id}/$MANIFEST_NAME").inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            covers.forEach { (coverName, stored) ->
                val file = File(coverStore, stored)
                if (STORED_COVER.matches(stored) && file.isFile && file.length() > 0L) {
                    zos.putNextEntry(ZipEntry("$COVERS_DIR/$coverName"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun exportSampleZip(outputStream: OutputStream) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            val manifestBytes = context.assets.open("$BUILT_IN_DIR/$MANIFEST_NAME").use { it.readBytes() }
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            zos.write(manifestBytes)
            zos.closeEntry()

            val coverFiles = runCatching { context.assets.list("$BUILT_IN_DIR/$COVERS_DIR") }
                .getOrNull().orEmpty()

            for (coverName in coverFiles) {
                runCatching {
                    val coverBytes = context.assets.open("$BUILT_IN_DIR/$COVERS_DIR/$coverName").use { it.readBytes() }
                    zos.putNextEntry(ZipEntry("$COVERS_DIR/$coverName"))
                    zos.write(coverBytes)
                    zos.closeEntry()
                }
            }
        }
    }

    fun createCatalogZipFile(): File {
        val exportDir = File(context.cacheDir, "exported_catalogs")
        exportDir.mkdirs()
        val catalogZip = File(exportDir, "radio-catalog.zip")
        FileOutputStream(catalogZip).use { exportCatalogZip(it) }
        return catalogZip
    }

    private fun loadBuiltIn(): ActiveCatalog {
        val builtIn = runCatching {
            InputStreamReader(context.assets.open("$BUILT_IN_DIR/$MANIFEST_NAME"), StandardCharsets.UTF_8).use {
                RadioCatalogCsv.read(it)
            }
        }.onFailure(Timber::e).getOrDefault(emptyList())
        return ActiveCatalog(
            BUILT_IN_ID,
            BUILT_IN_NAME,
            RadioCatalogType.BUILT_IN,
            builtIn.associateBy { it.frequencyKHz },
            emptyMap(),
        )
    }

    private fun loadCustom(entry: IndexEntry): ActiveCatalog? {
        val directory = File(catalogsDirectory, entry.id)
        val stations = InputStreamReader(FileInputStream(File(directory, MANIFEST_NAME)), StandardCharsets.UTF_8)
            .use { RadioCatalogCsv.read(it) }
        if (stations.isEmpty()) return null
        return ActiveCatalog(
            entry.id,
            entry.name,
            RadioCatalogType.CUSTOM,
            stations.associateBy { it.frequencyKHz },
            readCoverMap(directory),
        )
    }

    private fun readCoverMap(directory: File): Map<String, String> {
        val file = File(directory, COVER_MAP_NAME)
        if (!file.isFile) return emptyMap()
        val json = JSONObject(file.readText(StandardCharsets.UTF_8))
        val result = linkedMapOf<String, String>()
        json.keys().forEach { key ->
            val stored = json.optString(key)
            if (SAFE_COVER_NAME.matches(key) && STORED_COVER.matches(stored)) result[key] = stored
        }
        return result
    }

    private fun loadOrMigrateIndex(): CatalogIndex {
        val stored = runCatching { parseIndex(String(indexFile.readFully(), StandardCharsets.UTF_8)) }
        stored.getOrNull()?.let { return it }
        if (indexFile.baseFile.exists()) {
            // A damaged index must not drop the catalogs it described: recover them unnamed.
            Timber.e(stored.exceptionOrNull(), "Radio catalog index is unreadable; rebuilding")
            val recovered = catalogsDirectory.listFiles().orEmpty()
                .filter { it.isDirectory && CATALOG_ID.matches(it.name) && File(it, MANIFEST_NAME).isFile }
                .sortedBy(File::lastModified)
                .take(MAX_CUSTOM_CATALOGS)
                .mapIndexedNotNull { position, directory ->
                    runCatching {
                        val count = InputStreamReader(FileInputStream(File(directory, MANIFEST_NAME)), StandardCharsets.UTF_8)
                            .use { RadioCatalogCsv.read(it).size }
                        IndexEntry(directory.name, "$DEFAULT_CATALOG_NAME ${position + 1}", count)
                    }.getOrNull()
                }
            return CatalogIndex(BUILT_IN_ID, recovered).also(::commitIndex)
        }

        // No index yet: this install predates multiple catalogs, or its migration was interrupted
        // before the index was written. Catalog directories without an index are partial.
        catalogsDirectory.listFiles().orEmpty()
            .filter { it.isDirectory }
            .forEach { it.deleteRecursively() }
        val legacy = recoverLegacyDirectory()
        val migrated = legacy?.let {
            runCatching { storeCatalog(it, MIGRATED_CATALOG_NAME) }
                .onFailure { error -> Timber.e(error, "Cannot migrate the custom radio catalog") }
                .getOrNull()
        }
        val legacyActive = prefs.getBoolean(KEY_CUSTOM_CATALOG_ACTIVE, false)
        val migratedIndex = CatalogIndex(
            activeId = if (migrated != null && legacyActive) migrated.id else BUILT_IN_ID,
            entries = listOfNotNull(migrated),
        )
        commitIndex(migratedIndex)
        // An unreadable legacy catalog was never shown either; keep it for diagnostics.
        if (legacy == null || migrated != null) {
            listOf(LEGACY_DIR, "${LEGACY_DIR}_prev", "${LEGACY_DIR}_next").forEach {
                File(context.filesDir, it).deleteRecursively()
            }
        }
        prefs.edit().remove(KEY_CUSTOM_CATALOG_ACTIVE).apply()
        return migratedIndex
    }

    /** Finishes an interrupted legacy swap and returns the single custom catalog, if any. */
    private fun recoverLegacyDirectory(): File? {
        val current = File(context.filesDir, LEGACY_DIR)
        val previous = File(context.filesDir, "${LEGACY_DIR}_prev")
        val next = File(context.filesDir, "${LEGACY_DIR}_next")
        if (!current.exists()) {
            when {
                // The previous tree is known-good if a swap stopped before activation.
                next.isDirectory && previous.isDirectory -> previous.renameTo(current)
                next.isDirectory && validateDirectory(next).isSuccess -> next.renameTo(current)
                previous.isDirectory -> previous.renameTo(current)
            }
        }
        return current.takeIf { File(it, MANIFEST_NAME).isFile }
    }

    private fun parseIndex(text: String): CatalogIndex {
        val json = JSONObject(text)
        val catalogs = json.getJSONArray("catalogs")
        val entries = ArrayList<IndexEntry>(catalogs.length())
        for (position in 0 until catalogs.length()) {
            val item = catalogs.getJSONObject(position)
            val id = item.getString("id")
            if (!CATALOG_ID.matches(id) || entries.any { it.id == id }) continue
            val packId = item.optString("pack").takeIf(PACK_KEY::matches).orEmpty()
            entries += IndexEntry(
                id = id,
                name = normalizeName(item.optString("name")).ifEmpty { DEFAULT_CATALOG_NAME },
                stationCount = item.optInt("stations", 0),
                packId = packId,
                packName = if (packId.isEmpty()) "" else normalizeName(item.optString("packName")).ifEmpty { packId },
                key = if (packId.isEmpty()) "" else item.optString("key"),
                region = if (packId.isEmpty()) "" else normalizeName(item.optString("region")),
            )
        }
        val activeId = json.optString("active", BUILT_IN_ID)
            .takeIf { id -> id == BUILT_IN_ID || entries.any { it.id == id } }
            ?: BUILT_IN_ID
        return CatalogIndex(activeId, entries)
    }

    /** Writes [next] atomically and makes it current; the write is the commit point. */
    private fun commitIndex(next: CatalogIndex) {
        val json = JSONObject().apply {
            put("version", 1)
            put("active", next.activeId)
            put("catalogs", JSONArray().apply {
                next.entries.forEach { entry ->
                    put(JSONObject().apply {
                        put("id", entry.id)
                        put("name", entry.name)
                        put("stations", entry.stationCount)
                        if (entry.packId.isNotEmpty()) {
                            put("pack", entry.packId)
                            put("packName", entry.packName)
                            put("key", entry.key)
                            put("region", entry.region)
                        }
                    })
                }
            })
        }
        catalogsDirectory.mkdirs()
        val stream = indexFile.startWrite()
        try {
            stream.write(json.toString().toByteArray(StandardCharsets.UTF_8))
            indexFile.finishWrite(stream)
        } catch (error: Exception) {
            indexFile.failWrite(stream)
            throw error
        }
        index = next
    }

    /** Removes catalog directories and covers that the index no longer references. */
    private fun removeUnreferencedFiles() {
        val ids = index.entries.mapTo(HashSet()) { it.id }
        catalogsDirectory.listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in ids }
            .forEach { it.deleteRecursively() }
        val referenced = HashSet<String>()
        ids.forEach { id ->
            runCatching { referenced += readCoverMap(File(catalogsDirectory, id)).values }
                .onFailure { error ->
                    // An unreadable map must not let its covers be collected.
                    Timber.e(error, "Cannot read covers of radio catalog $id")
                    return
                }
        }
        coverStore.listFiles().orEmpty()
            .filter { it.name !in referenced }
            .forEach { it.deleteRecursively() }
    }

    private fun buildCatalogInfo(): RadioCatalogInfo {
        val catalog = active
        val count = catalog.stations.size
        val description = when (catalog.type) {
            RadioCatalogType.BUILT_IN -> "Встроенный каталог (Пенза, $count станций)"
            RadioCatalogType.CUSTOM -> index.entries.firstOrNull { it.id == catalog.id }
                ?.takeIf { it.packId.isNotEmpty() }
                ?.let { "«${it.name}» (${it.packName}, $count станций)" }
                ?: "«${catalog.name}» ($count станций)"
        }
        val builtInCount = if (catalog.type == RadioCatalogType.BUILT_IN) count else builtInStationCount
        val catalogs = buildList {
            add(RadioCatalogEntry(BUILT_IN_ID, BUILT_IN_NAME, builtInCount, builtIn = true))
            index.entries.forEach { add(it.toEntry()) }
        }
        return RadioCatalogInfo(
            type = catalog.type,
            stationCount = count,
            isWidgetBroadcastEnabled = isWidgetBroadcastEnabled,
            description = description,
            activeId = catalog.id,
            catalogs = catalogs,
        )
    }
}
