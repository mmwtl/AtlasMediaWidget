package com.mmwtl.atlasmediaapi.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.AtomicFile
import com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract
import com.mmwtl.atlasmediaapi.media.bridge.MediaSettingsSnapshot
import com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogRepository
import com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogType
import com.mmwtl.atlasmediaapi.media.cluster.ClusterMediaBridge
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MediaSettingsController(
    private val context: Context,
    val preferences: AtlasPreferences,
    val radioCatalogRepository: RadioCatalogRepository,
    val clusterMediaBridge: ClusterMediaBridge,
    private val onSettingsChanged: (() -> Unit)? = null,
) {
    companion object {
        private const val META_PREFS = "media_settings_meta"
        private const val KEY_REVISION = "revision"
        private const val KEY_LAST_COMMITTED_OPERATION = "last_committed_operation"
        private const val STAGING_METADATA = "operation.json"

        const val MAX_BACKUP_FILE_BYTES = 70L * 1024L * 1024L // 70 MB
        const val MAX_UNCOMPRESSED_BYTES = 65L * 1024L * 1024L // 65 MB
        const val MAX_ZIP_ENTRIES = 305

        val ALLOWED_AUDIO_SOURCES = setOf("", "RADIO", "BT", "USB", "ONLINE", "CPAA")
    }

    private val metaPrefs: SharedPreferences =
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var revision: Long = metaPrefs.getLong(KEY_REVISION, 1L).coerceAtLeast(1L)

    private val importLock = Any()
    private val stagedOperations = mutableMapOf<String, StagedImport>()

    data class StagedImport(
        val operationId: String,
        val stagingToken: String,
        val stagingDir: File,
        val mediaJson: JSONObject,
        val catalogMode: String,
        val stationCount: Int,
        val warnings: List<String>,
        var status: String, // "PREPARED", "COMMITTING", "COMMITTED", "FAILED"
    )

    data class UpdateResult(
        val snapshot: MediaSettingsSnapshot?,
        val status: Int,
        val errorMessage: String = "",
    )

    data class PrepareImportResult(
        val status: Int,
        val stagingToken: String = "",
        val catalogMode: String = "",
        val stationCount: Int = 0,
        val warnings: List<String> = emptyList(),
        val errorMessage: String = "",
    )

    data class CommitImportResult(
        val status: Int,
        val snapshot: MediaSettingsSnapshot? = null,
        val errorMessage: String = "",
    )

    init {
        synchronized(importLock) { loadStagedOperations() }
    }

    fun getRevision(): Long = revision

    @Synchronized
    private fun nextRevision(): Long {
        val nextRevision = revision + 1L
        check(metaPrefs.edit().putLong(KEY_REVISION, nextRevision).commit()) {
            "Не удалось сохранить ревизию настроек"
        }
        revision = nextRevision
        return nextRevision
    }

    fun getSnapshot(): MediaSettingsSnapshot = synchronized(importLock) {
        val catalogInfo = radioCatalogRepository.getCatalogInfo()
        return MediaSettingsSnapshot(
            revision = revision,
            defaultAudioSource = preferences.defaultAudioSource,
            defaultAudioSourceDelaySec = preferences.defaultAudioSourceDelaySec,
            defaultAudioSourceAutoplayOnStartup = preferences.defaultAudioSourceAutoplayOnStartup,
            autoSwitchToDefaultOnSourceLost = preferences.autoSwitchToDefaultOnSourceLost,
            autoSwitchToDefaultAutoplayOnSourceLost = preferences.autoSwitchToDefaultAutoplayOnSourceLost,
            defaultMediaPackage = preferences.defaultMediaPackage,
            switchToOnlineBeforeSessionPlay = preferences.switchToOnlineBeforeSessionPlay,
            radioWidgetBroadcastEnabled = radioCatalogRepository.isWidgetBroadcastEnabled,
            clusterCoversEnabled = clusterMediaBridge.isClusterCoversEnabled,
            clusterOnlineEnabled = clusterMediaBridge.isClusterOnlineEnabled,
            clusterWatchdogIntervalMs = clusterMediaBridge.reassertWatchdogIntervalMs,
            catalogType = catalogInfo.type.name,
            catalogStationCount = catalogInfo.stationCount,
            catalogDescription = catalogInfo.description,
            uiScaleTenths = preferences.uiScaleTenths,
        )
    }

    fun updateSettings(expectedRevision: Long?, update: Bundle): UpdateResult = synchronized(importLock) {
        if (expectedRevision != null && expectedRevision != revision) {
            return UpdateResult(
                snapshot = null,
                status = MediaBridgeContract.Status.CONFLICT,
                errorMessage = "Конфликт версий настроек: ожидалась $expectedRevision, актуальная $revision",
            )
        }

        // 1. Validation
        validateBundleTypes(update)?.let {
            return UpdateResult(null, MediaBridgeContract.Status.VALIDATION_ERROR, it)
        }
        if (update.containsKey(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE)) {
            val source = update.getString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE).orEmpty()
            if (source !in ALLOWED_AUDIO_SOURCES) {
                return UpdateResult(
                    snapshot = null,
                    status = MediaBridgeContract.Status.VALIDATION_ERROR,
                    errorMessage = "Недопустимый источник звука: '$source'",
                )
            }
        }
        if (update.containsKey(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC)) {
            val delay = update.getInt(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC)
            if (delay !in 0..30) {
                return UpdateResult(
                    snapshot = null,
                    status = MediaBridgeContract.Status.VALIDATION_ERROR,
                    errorMessage = "Задержка источника должна быть от 0 до 30 секунд: $delay",
                )
            }
        }
        if (update.containsKey(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS)) {
            val interval = update.getLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS)
            if (interval !in 1000L..5000L) {
                return UpdateResult(
                    snapshot = null,
                    status = MediaBridgeContract.Status.VALIDATION_ERROR,
                    errorMessage = "Интервал watchdog должен быть от 1000 до 5000 мс: $interval",
                )
            }
        }
        if (update.containsKey(MediaBridgeContract.Key.UI_SCALE_TENTHS)) {
            val scale = update.getInt(MediaBridgeContract.Key.UI_SCALE_TENTHS)
            if (scale !in 10..20) {
                return UpdateResult(
                    snapshot = null,
                    status = MediaBridgeContract.Status.VALIDATION_ERROR,
                    errorMessage = "Масштаб должен быть от 10 до 20 десятых: $scale",
                )
            }
        }
        if (update.containsKey(MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE)) {
            val pkg = update.getString(MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE).orEmpty()
            if (pkg.length > 128) {
                return UpdateResult(
                    snapshot = null,
                    status = MediaBridgeContract.Status.VALIDATION_ERROR,
                    errorMessage = "Слишком длинное имя пакета проигрывателя",
                )
            }
        }

        // 2. Application
        if (update.containsKey(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE)) {
            preferences.defaultAudioSource = update.getString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE).orEmpty()
        }
        if (update.containsKey(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC)) {
            preferences.defaultAudioSourceDelaySec = update.getInt(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC)
        }
        if (update.containsKey(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY)) {
            preferences.defaultAudioSourceAutoplayOnStartup = update.getBoolean(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY)
        }
        if (update.containsKey(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT)) {
            preferences.autoSwitchToDefaultOnSourceLost = update.getBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT)
        }
        if (update.containsKey(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT_AUTOPLAY)) {
            preferences.autoSwitchToDefaultAutoplayOnSourceLost = update.getBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT_AUTOPLAY)
        }
        if (update.containsKey(MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE)) {
            preferences.defaultMediaPackage = update.getString(MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE).orEmpty()
        }
        if (update.containsKey(MediaBridgeContract.Key.SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY)) {
            preferences.switchToOnlineBeforeSessionPlay = update.getBoolean(MediaBridgeContract.Key.SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY)
        }
        if (update.containsKey(MediaBridgeContract.Key.RADIO_WIDGET_BROADCAST_ENABLED)) {
            radioCatalogRepository.setWidgetBroadcastEnabled(update.getBoolean(MediaBridgeContract.Key.RADIO_WIDGET_BROADCAST_ENABLED))
        }
        if (update.containsKey(MediaBridgeContract.Key.CLUSTER_COVERS_ENABLED)) {
            clusterMediaBridge.setClusterCoversEnabled(update.getBoolean(MediaBridgeContract.Key.CLUSTER_COVERS_ENABLED))
        }
        if (update.containsKey(MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED)) {
            clusterMediaBridge.setClusterOnlineEnabled(update.getBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED))
        }
        if (update.containsKey(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS)) {
            clusterMediaBridge.setReassertWatchdogIntervalMs(update.getLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS))
        }
        if (update.containsKey(MediaBridgeContract.Key.UI_SCALE_TENTHS)) {
            preferences.uiScaleTenths = update.getInt(MediaBridgeContract.Key.UI_SCALE_TENTHS)
        }

        try {
            // Individual settings owners use apply(); drain all of their pending writes before
            // publishing a revision that tells clients this update is durable.
            drainPendingPreferenceWrites()
            nextRevision()
        } catch (error: Exception) {
            Timber.e(error, "updateSettings could not durably publish the new revision")
            return UpdateResult(
                snapshot = null,
                status = MediaBridgeContract.Status.FAILED,
                errorMessage = error.message ?: "Не удалось сохранить настройки",
            )
        }
        onSettingsChanged?.invoke()
        return UpdateResult(
            snapshot = getSnapshot(),
            status = MediaBridgeContract.Status.OK,
        )
    }

    fun restoreDefaultCatalog(): MediaSettingsSnapshot = synchronized(importLock) {
        radioCatalogRepository.restoreDefaultCatalog()
        nextRevision()
        onSettingsChanged?.invoke()
        return getSnapshot()
    }

    /** Publishes a radio catalog mutation to the coordinator and settings clients. */
    fun onRadioCatalogChanged(): MediaSettingsSnapshot = synchronized(importLock) {
        nextRevision()
        onSettingsChanged?.invoke()
        return getSnapshot()
    }

    fun exportMediaBackup(outputStream: OutputStream) = synchronized(importLock) {
        val snapshot = getSnapshot()

        val mediaJsonObj = JSONObject().apply {
            put("format", "atlas-media-settings")
            put("schemaVersion", 1)
            put("defaultAudioSource", snapshot.defaultAudioSource)
            put("defaultAudioSourceDelaySec", snapshot.defaultAudioSourceDelaySec)
            put("defaultAudioSourceAutoplayOnStartup", snapshot.defaultAudioSourceAutoplayOnStartup)
            put("autoSwitchToDefaultOnSourceLost", snapshot.autoSwitchToDefaultOnSourceLost)
            put("autoSwitchToDefaultAutoplayOnSourceLost", snapshot.autoSwitchToDefaultAutoplayOnSourceLost)
            put("defaultMediaPackage", snapshot.defaultMediaPackage)
            put("switchToOnlineBeforeSessionPlay", snapshot.switchToOnlineBeforeSessionPlay)
            put("radioWidgetBroadcastEnabled", snapshot.radioWidgetBroadcastEnabled)
            put("clusterCoversEnabled", snapshot.clusterCoversEnabled)
            put("clusterOnlineEnabled", snapshot.clusterOnlineEnabled)
            put("clusterWatchdogIntervalMs", snapshot.clusterWatchdogIntervalMs)
        }
        val mediaJsonBytes = mediaJsonObj.toString(2).toByteArray(StandardCharsets.UTF_8)

        val manifestObj = JSONObject().apply {
            put("format", "atlas-media-backup")
            put("schemaVersion", 1)
            put("originPackage", context.packageName)
            put("createdAt", System.currentTimeMillis())
            val sections = JSONArray().apply {
                put("media")
            }
            put("sections", sections)
            val hashes = JSONObject().apply {
                put("media.json", sha256(mediaJsonBytes))
            }
            put("hashes", hashes)
        }
        val manifestBytes = manifestObj.toString(2).toByteArray(StandardCharsets.UTF_8)

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("media.json"))
            zos.write(mediaJsonBytes)
            zos.closeEntry()

        }
    }

    fun prepareMediaImport(operationId: String, inputStream: InputStream): PrepareImportResult = synchronized(importLock) {
        if (!isValidOperationId(operationId)) {
            return PrepareImportResult(MediaBridgeContract.Status.INVALID_REQUEST, errorMessage = "operationId должен быть UUID")
        }
        val lastCommitted = metaPrefs.getString(KEY_LAST_COMMITTED_OPERATION, "")
        if (operationId == lastCommitted) {
            return PrepareImportResult(
                status = MediaBridgeContract.Status.OK,
                stagingToken = "already_committed",
                catalogMode = currentCatalogMode(),
                stationCount = radioCatalogRepository.getCatalogInfo().stationCount,
                warnings = listOf("Операция уже была применена"),
            )
        }

        val existing = stagedOperations[operationId] ?: loadStagedOperation(operationId)
        if (existing != null) {
            if (existing.status == "COMMITTING") {
                return PrepareImportResult(MediaBridgeContract.Status.CONFLICT, errorMessage = "Импорт операции уже выполняется")
            }
            if (existing.status == "PREPARED") {
                return PrepareImportResult(MediaBridgeContract.Status.OK, existing.stagingToken, existing.catalogMode, existing.stationCount, existing.warnings)
            }
        }

        val stagingDir = File(context.filesDir, "staging_media_import_$operationId")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        try {
            var totalExtractedBytes = 0L
            var totalEntries = 0
            val stagingCanonical = stagingDir.canonicalPath

            ZipInputStream(BufferedInputStream(LimitedInputStream(inputStream, MAX_BACKUP_FILE_BYTES))).use { zis ->
                val entries = HashSet<String>()
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > MAX_ZIP_ENTRIES) {
                        throw IllegalStateException("Превышено максимальное количество файлов в архиве ($MAX_ZIP_ENTRIES)")
                    }

                    if (entry.name.isEmpty() || entry.name.startsWith('/') || entry.name.contains('\\')) {
                        throw SecurityException("Небезопасный путь в ZIP архиве: ${entry.name}")
                    }
                    val normalizedName = entry.name
                    val targetFile = File(stagingDir, normalizedName)
                    val targetCanonical = targetFile.canonicalPath
                    val isSafe = targetCanonical == stagingCanonical ||
                            targetCanonical.startsWith(stagingCanonical + File.separator)
                    if (!isSafe) {
                        throw SecurityException("Небезопасный путь в ZIP архиве: ${entry.name}")
                    }
                    if (!entries.add(targetCanonical)) {
                        throw IllegalArgumentException("Дублирующийся путь в ZIP архиве: ${entry.name}")
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
                                if (totalExtractedBytes > MAX_UNCOMPRESSED_BYTES) {
                                    throw IllegalStateException("Превышен допустимый размер распакованного архива (65 МБ)")
                                }
                                fos.write(buffer, 0, read)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val mediaFile = File(stagingDir, "media.json")
            if (!mediaFile.isFile) {
                throw IllegalArgumentException("В архиве отсутствует файл media.json")
            }

            val mediaJsonStr = mediaFile.readText(StandardCharsets.UTF_8)
            val mediaJson = JSONObject(mediaJsonStr)
            val manifestFile = File(stagingDir, "manifest.json")
            if (!manifestFile.isFile) throw IllegalArgumentException("В архиве отсутствует manifest.json")
            val manifest = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))
            validateManifest(manifest, stagingDir)
            validateArchiveEntries(stagingDir)
            val catalogMode = validateMediaJson(mediaJson)
            val warnings = mutableListOf<String>()
            var stationCount = 0

            if (catalogMode == "custom") {
                val radioDir = File(stagingDir, "radio")
                stationCount = radioCatalogRepository.validateDirectory(radioDir).getOrThrow()
            } else if (File(stagingDir, "radio").exists()) {
                throw IllegalArgumentException("Каталог radio присутствует при catalogMode=builtin")
            }

            val defaultMediaPkg = mediaJson.optString("defaultMediaPackage", "")
            if (defaultMediaPkg.isNotBlank()) {
                val isInstalled = runCatching {
                    context.packageManager.getPackageInfo(defaultMediaPkg, 0)
                    true
                }.getOrDefault(false)
                if (!isInstalled) {
                    warnings.add("Проигрыватель '$defaultMediaPkg' не установлен на устройстве")
                }
            }

            val stagingToken = UUID.randomUUID().toString()
            val staged = StagedImport(
                operationId = operationId,
                stagingToken = stagingToken,
                stagingDir = stagingDir,
                mediaJson = mediaJson,
                catalogMode = catalogMode,
                stationCount = stationCount,
                warnings = warnings,
                status = "PREPARED",
            )
            stagedOperations[operationId] = staged
            persistStaged(staged)

            return PrepareImportResult(
                status = MediaBridgeContract.Status.OK,
                stagingToken = stagingToken,
                catalogMode = catalogMode,
                stationCount = stationCount,
                warnings = warnings,
            )
        } catch (e: Exception) {
            Timber.w(e, "prepareMediaImport failed for operation $operationId")
            stagingDir.deleteRecursively()
            return PrepareImportResult(
                status = MediaBridgeContract.Status.VALIDATION_ERROR,
                errorMessage = e.message ?: "Ошибка валидации медиаархива",
            )
        }
    }

    fun commitMediaImport(operationId: String, stagingToken: String): CommitImportResult = synchronized(importLock) {
        if (!isValidOperationId(operationId)) {
            return CommitImportResult(MediaBridgeContract.Status.INVALID_REQUEST, errorMessage = "operationId должен быть UUID")
        }
        val lastCommitted = metaPrefs.getString(KEY_LAST_COMMITTED_OPERATION, "")
        if (operationId == lastCommitted) {
            return CommitImportResult(
                status = MediaBridgeContract.Status.OK,
                snapshot = getSnapshot(),
            )
        }

        val staged = stagedOperations[operationId] ?: loadStagedOperation(operationId)
            ?: return CommitImportResult(
                status = MediaBridgeContract.Status.INVALID_REQUEST,
                errorMessage = "Операция импорта $operationId не найдена или не подготовлена",
            )

        if (staged.stagingToken != stagingToken) {
            return CommitImportResult(
                status = MediaBridgeContract.Status.INVALID_REQUEST,
                errorMessage = "Неверный токен подготовки импорта",
            )
        }
        if (staged.status == "COMMITTED") {
            return CommitImportResult(MediaBridgeContract.Status.OK, snapshot = getSnapshot())
        }

        var commitStarted = false
        try {
            val mediaJson = staged.mediaJson
            validateMediaJson(mediaJson)
            if (staged.catalogMode == "custom") {
                radioCatalogRepository.validateDirectory(File(staged.stagingDir, "radio")).getOrThrow()
            }
            staged.status = "COMMITTING"
            persistStaged(staged)
            commitStarted = true

            // Settings imports are full replacements. Missing portable fields therefore return
            // to their canonical defaults; the active radio catalog remains untouched.
            applyImportedSettings(mediaJson)

            // Advance the revision before publishing the durable marker. This keeps CAS updates
            // from accepting the pre-import revision after a process death.
            nextRevision()
            // Drain the asynchronous preference writes before publishing the durable marker.
            drainPendingPreferenceWrites()
            check(metaPrefs.edit().putString(KEY_LAST_COMMITTED_OPERATION, operationId).commit()) {
                "Не удалось сохранить маркер завершённого импорта"
            }
            staged.status = "COMMITTED"
            persistStaged(staged)
            staged.stagingDir.deleteRecursively()
            stagedOperations.remove(operationId)

            onSettingsChanged?.invoke()
            return CommitImportResult(
                status = MediaBridgeContract.Status.OK,
                snapshot = getSnapshot(),
            )
        } catch (e: Exception) {
            Timber.e(e, "commitMediaImport failed for operation $operationId")
            staged.status = if (commitStarted) "COMMITTING" else "PREPARED"
            runCatching { persistStaged(staged) }
            return CommitImportResult(
                status = MediaBridgeContract.Status.FAILED,
                errorMessage = e.message ?: "Не удалось применить импорт медиа",
            )
        }
    }

    fun abortMediaImport(operationId: String) = synchronized(importLock) {
        if (!isValidOperationId(operationId)) return@synchronized
        val staged = stagedOperations[operationId] ?: loadStagedOperation(operationId)
        if (staged?.status == "COMMITTING") return@synchronized
        stagedOperations.remove(operationId)
        staged?.stagingDir?.deleteRecursively()
    }

    fun getImportStatus(operationId: String): String = synchronized(importLock) {
        if (!isValidOperationId(operationId)) return@synchronized "IDLE"
        val lastCommitted = metaPrefs.getString(KEY_LAST_COMMITTED_OPERATION, "")
        if (operationId == lastCommitted) {
            return "COMMITTED"
        }
        val staged = stagedOperations[operationId] ?: loadStagedOperation(operationId)
        if (staged?.status == "COMMITTING") {
            val result = commitMediaImport(operationId, staged.stagingToken)
            return if (result.status == MediaBridgeContract.Status.OK) "COMMITTED" else staged.status
        }
        return staged?.status ?: "IDLE"
    }

    private fun applyImportedSettings(mediaJson: JSONObject) {
        preferences.defaultAudioSource = mediaJson.optString("defaultAudioSource", "")
        preferences.defaultAudioSourceDelaySec = if (mediaJson.has("defaultAudioSourceDelaySec")) {
            mediaJson.getInt("defaultAudioSourceDelaySec")
        } else 0
        preferences.defaultAudioSourceAutoplayOnStartup = mediaJson.optBoolean(
            "defaultAudioSourceAutoplayOnStartup",
            true,
        )
        preferences.autoSwitchToDefaultOnSourceLost = mediaJson.optBoolean(
            "autoSwitchToDefaultOnSourceLost",
            false,
        )
        preferences.autoSwitchToDefaultAutoplayOnSourceLost = mediaJson.optBoolean(
            "autoSwitchToDefaultAutoplayOnSourceLost",
            true,
        )
        preferences.defaultMediaPackage = mediaJson.optString("defaultMediaPackage", "")
        preferences.switchToOnlineBeforeSessionPlay = mediaJson.optBoolean(
            "switchToOnlineBeforeSessionPlay",
            false,
        )
        radioCatalogRepository.setWidgetBroadcastEnabled(
            mediaJson.optBoolean("radioWidgetBroadcastEnabled", true),
        )
        clusterMediaBridge.setClusterCoversEnabled(
            mediaJson.optBoolean("clusterCoversEnabled", true),
        )
        clusterMediaBridge.setClusterOnlineEnabled(
            mediaJson.optBoolean("clusterOnlineEnabled", false),
        )
        clusterMediaBridge.setReassertWatchdogIntervalMs(
            if (mediaJson.has("clusterWatchdogIntervalMs")) {
                mediaJson.getLong("clusterWatchdogIntervalMs")
            } else ClusterMediaBridge.DEFAULT_REASSERT_WATCHDOG_INTERVAL_MS,
        )
    }

    private fun currentCatalogMode(): String = when (radioCatalogRepository.getCatalogInfo().type) {
        RadioCatalogType.BUILT_IN -> "builtin"
        RadioCatalogType.CUSTOM -> "custom"
    }

    private fun drainPendingPreferenceWrites() {
        check(context.getSharedPreferences("atlas_media_api_settings", Context.MODE_PRIVATE)
            .edit().commit()) {
            "Не удалось сохранить медиа-настройки"
        }
        check(context.getSharedPreferences(RadioCatalogRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().commit()) {
            "Не удалось сохранить настройки каталога радио"
        }
        check(context.getSharedPreferences(ClusterMediaBridge.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().commit()) {
            "Не удалось сохранить настройки DIM"
        }
    }

    private fun validateBundleTypes(update: Bundle): String? {
        val expected = mapOf(
            MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE to String::class.java,
            MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC to Integer::class.java,
            MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY to java.lang.Boolean::class.java,
            MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT to java.lang.Boolean::class.java,
            MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT_AUTOPLAY to java.lang.Boolean::class.java,
            MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE to String::class.java,
            MediaBridgeContract.Key.SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY to java.lang.Boolean::class.java,
            MediaBridgeContract.Key.RADIO_WIDGET_BROADCAST_ENABLED to java.lang.Boolean::class.java,
            MediaBridgeContract.Key.CLUSTER_COVERS_ENABLED to java.lang.Boolean::class.java,
            MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED to java.lang.Boolean::class.java,
            MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS to java.lang.Long::class.java,
            MediaBridgeContract.Key.UI_SCALE_TENTHS to Integer::class.java,
        )
        expected.forEach { (key, type) ->
            if (update.containsKey(key) && !type.isInstance(update.get(key))) {
                return "Недопустимый тип поля $key"
            }
        }
        return null
    }

    private fun validateMediaJson(mediaJson: JSONObject): String {
        if (mediaJson.opt("format") !is String || mediaJson.getString("format") != "atlas-media-settings") {
            throw IllegalArgumentException("Неверный формат media.json")
        }
        if (mediaJson.opt("schemaVersion") !is Number || jsonInt(mediaJson, "schemaVersion") != 1) {
            throw IllegalArgumentException("Неподдерживаемая версия схемы media.json")
        }
        val catalogMode = if (mediaJson.has("catalogMode")) {
            if (mediaJson.opt("catalogMode") !is String) throw IllegalArgumentException("Недопустимый тип catalogMode")
            mediaJson.getString("catalogMode").lowercase(Locale.ROOT)
        } else "builtin"
        if (catalogMode != "builtin" && catalogMode != "custom") {
            throw IllegalArgumentException("Неверный режим каталога радио: $catalogMode")
        }
        val stringFields = listOf("defaultAudioSource", "defaultMediaPackage")
        val booleanFields = listOf(
            "defaultAudioSourceAutoplayOnStartup", "autoSwitchToDefaultOnSourceLost",
            "autoSwitchToDefaultAutoplayOnSourceLost", "switchToOnlineBeforeSessionPlay",
            "radioWidgetBroadcastEnabled", "clusterCoversEnabled", "clusterOnlineEnabled",
        )
        stringFields.forEach { if (mediaJson.has(it) && mediaJson.opt(it) !is String) throw IllegalArgumentException("Недопустимый тип поля $it") }
        booleanFields.forEach { if (mediaJson.has(it) && mediaJson.opt(it) !is Boolean) throw IllegalArgumentException("Недопустимый тип поля $it") }
        if (mediaJson.has("defaultAudioSource") && mediaJson.getString("defaultAudioSource") !in ALLOWED_AUDIO_SOURCES) {
            throw IllegalArgumentException("Недопустимый источник звука")
        }
        if (mediaJson.has("defaultAudioSourceDelaySec")) {
            val value = jsonInt(mediaJson, "defaultAudioSourceDelaySec")
            if (value !in 0..30) throw IllegalArgumentException("Задержка источника должна быть от 0 до 30 секунд: $value")
        }
        if (mediaJson.has("clusterWatchdogIntervalMs")) {
            val value = jsonLong(mediaJson, "clusterWatchdogIntervalMs")
            if (value !in 1000L..5000L) throw IllegalArgumentException("Интервал watchdog должен быть от 1000 до 5000 мс: $value")
        }
        if (mediaJson.has("uiScaleTenths")) {
            val value = jsonInt(mediaJson, "uiScaleTenths")
            if (value !in 10..20) throw IllegalArgumentException("Масштаб должен быть от 10 до 20 десятых: $value")
        }
        if (mediaJson.has("defaultMediaPackage") && mediaJson.getString("defaultMediaPackage").length > 128) {
            throw IllegalArgumentException("Слишком длинное имя пакета проигрывателя")
        }
        return catalogMode
    }

    private fun jsonInt(json: JSONObject, key: String): Int {
        val value = json.opt(key)
        if (value !is Number || value.toLong().toDouble() != value.toDouble() || value.toLong() !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw IllegalArgumentException("Недопустимый тип поля $key")
        }
        return value.toInt()
    }

    private fun jsonLong(json: JSONObject, key: String): Long {
        val value = json.opt(key)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) throw IllegalArgumentException("Недопустимый тип поля $key")
        return value.toLong()
    }

    private fun validateManifest(manifest: JSONObject, stagingDir: File) {
        if (manifest.opt("format") !is String || manifest.getString("format") != "atlas-media-backup") {
            throw IllegalArgumentException("Неверный формат manifest.json")
        }
        if (manifest.opt("schemaVersion") !is Number || jsonInt(manifest, "schemaVersion") != 1) {
            throw IllegalArgumentException("Неподдерживаемая версия схемы manifest.json")
        }
        val hasMedia = File(stagingDir, "media.json").isFile
        val hasWidget = File(stagingDir, "widget.json").isFile
        val hasRadio = File(stagingDir, "radio/stations.csv").isFile
        if (!hasMedia) throw IllegalArgumentException("В архиве отсутствует файл media.json")
        if (manifest.has("sections")) {
            val sections = manifest.opt("sections") as? JSONArray ?: throw IllegalArgumentException("Повреждённый список секций")
            val actual = linkedSetOf<String>()
            for (i in 0 until sections.length()) {
                val section = sections.opt(i)
                if (section !is String || !actual.add(section) || section !in setOf("widget", "media", "radio")) {
                    throw IllegalArgumentException("Неверный список секций архива")
                }
            }
            if (("widget" in actual) != hasWidget || ("media" in actual) != hasMedia || ("radio" in actual) != hasRadio) {
                throw IllegalArgumentException("Состав архива не соответствует manifest.json")
            }
        }
        if (manifest.has("hashes")) {
            val hashes = manifest.opt("hashes") as? JSONObject ?: throw IllegalArgumentException("Повреждённый список контрольных сумм")
            val keys = hashes.keys()
            while (keys.hasNext()) {
                val path = keys.next()
                val expected = hashes.opt(path)
                if (expected !is String || !expected.matches(Regex("[0-9a-fA-F]{64}"))) throw IllegalArgumentException("Повреждённая контрольная сумма $path")
                val target = safeStagingFile(stagingDir, path)
                if (!target.isFile || sha256(target) != expected.lowercase(Locale.ROOT)) throw IllegalArgumentException("Нарушена целостность файла $path")
            }
        }
    }

    private fun validateArchiveEntries(stagingDir: File) {
        fun visit(dir: File, relative: String) {
            dir.listFiles()?.forEach { file ->
                if (relative.isEmpty() && file.name == STAGING_METADATA) return@forEach
                val path = if (relative.isEmpty()) file.name else "$relative/${file.name}"
                if (file.isDirectory) {
                    if (relative.isEmpty() && file.name !in setOf("radio")) {
                        throw IllegalArgumentException("Недопустимый раздел архива: ${file.name}")
                    }
                    visit(file, path)
                } else if (relative.isEmpty() && file.name !in setOf("manifest.json", "media.json", "widget.json")) {
                    throw IllegalArgumentException("Недопустимый файл архива: $path")
                }
            }
        }
        visit(stagingDir, "")
    }

    private fun safeStagingFile(stagingDir: File, path: String): File {
        if (path.isEmpty() || path.startsWith('/') || path.contains('\\')) throw SecurityException("Небезопасный путь в manifest: $path")
        val target = File(stagingDir, path)
        if (!target.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) throw SecurityException("Небезопасный путь в manifest: $path")
        return target
    }

    private fun isValidOperationId(value: String): Boolean =
        value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"))

    private fun persistStaged(staged: StagedImport) {
        val json = JSONObject().apply {
            put("operationId", staged.operationId)
            put("stagingToken", staged.stagingToken)
            put("catalogMode", staged.catalogMode)
            put("stationCount", staged.stationCount)
            put("status", staged.status)
            put("mediaJson", staged.mediaJson.toString())
            put("warnings", JSONArray(staged.warnings))
        }
        val atomic = AtomicFile(File(staged.stagingDir, STAGING_METADATA))
        staged.stagingDir.mkdirs()
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString().toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun loadStagedOperations() {
        context.filesDir.listFiles()?.forEach { dir ->
            val name = dir.name
            if (!dir.isDirectory || !name.startsWith("staging_media_import_")) return@forEach
            val operationId = name.removePrefix("staging_media_import_")
            if (!isValidOperationId(operationId)) return@forEach
            loadStagedOperation(operationId)
        }
    }

    private fun loadStagedOperation(operationId: String): StagedImport? {
        val dir = File(context.filesDir, "staging_media_import_$operationId")
        val metadata = File(dir, STAGING_METADATA)
        if (!metadata.isFile && !File(metadata.path + ".bak").isFile) return null
        return runCatching {
            val json = JSONObject(String(AtomicFile(metadata).openRead().use { it.readBytes() }, StandardCharsets.UTF_8))
            val staged = StagedImport(
                operationId = json.getString("operationId"),
                stagingToken = json.getString("stagingToken"),
                stagingDir = dir,
                mediaJson = JSONObject(json.getString("mediaJson")),
                catalogMode = json.getString("catalogMode"),
                stationCount = json.getInt("stationCount"),
                warnings = buildList {
                    val array = json.optJSONArray("warnings") ?: JSONArray()
                    for (i in 0 until array.length()) add(array.getString(i))
                },
                status = json.getString("status"),
            )
            if (staged.status in setOf("PREPARED", "COMMITTING", "COMMITTED", "FAILED")) stagedOperations[operationId] = staged
            staged
        }.getOrNull()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class LimitedInputStream(
        private val delegate: InputStream,
        private val limit: Long,
    ) : InputStream() {
        private var count = 0L
        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0 && ++count > limit) throw IllegalStateException("Размер файла превышает допустимый лимит (70 МБ)")
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val value = delegate.read(buffer, offset, length)
            if (value > 0) {
                count += value
                if (count > limit) throw IllegalStateException("Размер файла превышает допустимый лимит (70 МБ)")
            }
            return value
        }
        override fun close() = delegate.close()
    }
}
