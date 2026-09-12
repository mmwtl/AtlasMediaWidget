package com.mmwtl.atlasmediaapi.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
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
        var status: String, // "PREPARED", "COMMITTED", "FAILED"
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

    fun getRevision(): Long = revision

    @Synchronized
    private fun nextRevision(): Long {
        revision++
        metaPrefs.edit().putLong(KEY_REVISION, revision).apply()
        return revision
    }

    fun getSnapshot(): MediaSettingsSnapshot {
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
            clusterWatchdogIntervalMs = clusterMediaBridge.reassertWatchdogIntervalMs,
            catalogType = catalogInfo.type.name,
            catalogStationCount = catalogInfo.stationCount,
            catalogDescription = catalogInfo.description,
            uiScaleTenths = preferences.uiScaleTenths,
        )
    }

    @Synchronized
    fun updateSettings(expectedRevision: Long?, update: Bundle): UpdateResult {
        if (expectedRevision != null && expectedRevision != revision) {
            return UpdateResult(
                snapshot = null,
                status = MediaBridgeContract.Status.CONFLICT,
                errorMessage = "Конфликт версий настроек: ожидалась $expectedRevision, актуальная $revision",
            )
        }

        // 1. Validation
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
        if (update.containsKey(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS)) {
            clusterMediaBridge.setReassertWatchdogIntervalMs(update.getLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS))
        }
        if (update.containsKey(MediaBridgeContract.Key.UI_SCALE_TENTHS)) {
            preferences.uiScaleTenths = update.getInt(MediaBridgeContract.Key.UI_SCALE_TENTHS)
        }

        nextRevision()
        onSettingsChanged?.invoke()
        return UpdateResult(
            snapshot = getSnapshot(),
            status = MediaBridgeContract.Status.OK,
        )
    }

    @Synchronized
    fun restoreDefaultCatalog(): MediaSettingsSnapshot {
        radioCatalogRepository.restoreDefaultCatalog()
        nextRevision()
        onSettingsChanged?.invoke()
        return getSnapshot()
    }

    fun exportMediaBackup(outputStream: OutputStream) {
        val snapshot = getSnapshot()
        val isCustomCatalog = snapshot.catalogType == "CUSTOM"

        val mediaJsonObj = JSONObject().apply {
            put("format", "atlas-media-settings")
            put("schemaVersion", 1)
            put("catalogMode", if (isCustomCatalog) "custom" else "builtin")
            put("defaultAudioSource", snapshot.defaultAudioSource)
            put("defaultAudioSourceDelaySec", snapshot.defaultAudioSourceDelaySec)
            put("defaultAudioSourceAutoplayOnStartup", snapshot.defaultAudioSourceAutoplayOnStartup)
            put("autoSwitchToDefaultOnSourceLost", snapshot.autoSwitchToDefaultOnSourceLost)
            put("autoSwitchToDefaultAutoplayOnSourceLost", snapshot.autoSwitchToDefaultAutoplayOnSourceLost)
            put("defaultMediaPackage", snapshot.defaultMediaPackage)
            put("switchToOnlineBeforeSessionPlay", snapshot.switchToOnlineBeforeSessionPlay)
            put("radioWidgetBroadcastEnabled", snapshot.radioWidgetBroadcastEnabled)
            put("clusterCoversEnabled", snapshot.clusterCoversEnabled)
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
                if (isCustomCatalog) put("radio")
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

            if (isCustomCatalog) {
                radioCatalogRepository.writeRadioSectionToZip(zos, "radio/")
            }
        }
    }

    fun prepareMediaImport(operationId: String, inputStream: InputStream): PrepareImportResult = synchronized(importLock) {
        val lastCommitted = metaPrefs.getString(KEY_LAST_COMMITTED_OPERATION, "")
        if (operationId.isNotBlank() && operationId == lastCommitted) {
            return PrepareImportResult(
                status = MediaBridgeContract.Status.OK,
                stagingToken = "already_committed",
                catalogMode = if (preferences.defaultAudioSource.isNotBlank()) "custom" else "builtin",
                stationCount = radioCatalogRepository.getCatalogInfo().stationCount,
                warnings = listOf("Операция уже была применена"),
            )
        }

        val stagingDir = File(context.filesDir, "staging_media_import_$operationId")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        try {
            var totalExtractedBytes = 0L
            var totalEntries = 0
            val stagingCanonical = stagingDir.canonicalPath

            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > MAX_ZIP_ENTRIES) {
                        throw IllegalStateException("Превышено максимальное количество файлов в архиве ($MAX_ZIP_ENTRIES)")
                    }

                    val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                    val targetFile = File(stagingDir, normalizedName)
                    val targetCanonical = targetFile.canonicalPath
                    val isSafe = targetCanonical == stagingCanonical ||
                            targetCanonical.startsWith(stagingCanonical + File.separator)
                    if (!isSafe) {
                        throw SecurityException("Небезопасный путь в ZIP архиве: ${entry.name}")
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
            val format = mediaJson.optString("format", "")
            if (format != "atlas-media-settings") {
                throw IllegalArgumentException("Неверный формат media.json: $format")
            }
            val schemaVersion = mediaJson.optInt("schemaVersion", 0)
            if (schemaVersion != 1) {
                throw IllegalArgumentException("Неподдерживаемая версия схемы media.json: $schemaVersion")
            }

            val catalogMode = mediaJson.optString("catalogMode", "builtin")
            val warnings = mutableListOf<String>()
            var stationCount = 0

            if (catalogMode == "custom") {
                val radioDir = File(stagingDir, "radio")
                if (!radioDir.isDirectory) {
                    throw IllegalArgumentException("Указан catalogMode=custom, но каталог radio/ отсутствует")
                }
                val manifestFile = File(radioDir, "stations.csv")
                if (!manifestFile.isFile) {
                    throw IllegalArgumentException("В архиве отсутствует radio/stations.csv")
                }
                val parsedStations = manifestFile.reader(StandardCharsets.UTF_8).use {
                    com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogCsv.read(it)
                }
                stationCount = parsedStations.size
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
        val lastCommitted = metaPrefs.getString(KEY_LAST_COMMITTED_OPERATION, "")
        if (operationId.isNotBlank() && operationId == lastCommitted) {
            return CommitImportResult(
                status = MediaBridgeContract.Status.OK,
                snapshot = getSnapshot(),
            )
        }

        val staged = stagedOperations[operationId]
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

        try {
            val mediaJson = staged.mediaJson
            // Apply media settings
            if (mediaJson.has("defaultAudioSource")) {
                preferences.defaultAudioSource = mediaJson.optString("defaultAudioSource", "")
            }
            if (mediaJson.has("defaultAudioSourceDelaySec")) {
                preferences.defaultAudioSourceDelaySec = mediaJson.optInt("defaultAudioSourceDelaySec", 0)
            }
            if (mediaJson.has("defaultAudioSourceAutoplayOnStartup")) {
                preferences.defaultAudioSourceAutoplayOnStartup = mediaJson.optBoolean("defaultAudioSourceAutoplayOnStartup", true)
            }
            if (mediaJson.has("autoSwitchToDefaultOnSourceLost")) {
                preferences.autoSwitchToDefaultOnSourceLost = mediaJson.optBoolean("autoSwitchToDefaultOnSourceLost", false)
            }
            if (mediaJson.has("autoSwitchToDefaultAutoplayOnSourceLost")) {
                preferences.autoSwitchToDefaultAutoplayOnSourceLost = mediaJson.optBoolean("autoSwitchToDefaultAutoplayOnSourceLost", true)
            }
            if (mediaJson.has("defaultMediaPackage")) {
                preferences.defaultMediaPackage = mediaJson.optString("defaultMediaPackage", "")
            }
            if (mediaJson.has("switchToOnlineBeforeSessionPlay")) {
                preferences.switchToOnlineBeforeSessionPlay = mediaJson.optBoolean("switchToOnlineBeforeSessionPlay", false)
            }
            if (mediaJson.has("radioWidgetBroadcastEnabled")) {
                radioCatalogRepository.setWidgetBroadcastEnabled(mediaJson.optBoolean("radioWidgetBroadcastEnabled", true))
            }
            if (mediaJson.has("clusterCoversEnabled")) {
                clusterMediaBridge.setClusterCoversEnabled(mediaJson.optBoolean("clusterCoversEnabled", true))
            }
            if (mediaJson.has("clusterWatchdogIntervalMs")) {
                clusterMediaBridge.setReassertWatchdogIntervalMs(mediaJson.optLong("clusterWatchdogIntervalMs", 1250L))
            }

            // Radio catalog swap
            if (staged.catalogMode == "custom") {
                val radioDir = File(staged.stagingDir, "radio")
                val importedCount = radioCatalogRepository.importFromDirectory(radioDir).getOrThrow()
                Timber.i("Imported $importedCount custom radio stations from backup")
            } else {
                radioCatalogRepository.restoreDefaultCatalog()
            }

            staged.status = "COMMITTED"
            metaPrefs.edit().putString(KEY_LAST_COMMITTED_OPERATION, operationId).apply()
            staged.stagingDir.deleteRecursively()
            stagedOperations.remove(operationId)

            nextRevision()
            onSettingsChanged?.invoke()
            return CommitImportResult(
                status = MediaBridgeContract.Status.OK,
                snapshot = getSnapshot(),
            )
        } catch (e: Exception) {
            Timber.e(e, "commitMediaImport failed for operation $operationId")
            staged.status = "FAILED"
            return CommitImportResult(
                status = MediaBridgeContract.Status.FAILED,
                errorMessage = e.message ?: "Не удалось применить импорт медиа",
            )
        }
    }

    fun abortMediaImport(operationId: String) = synchronized(importLock) {
        val staged = stagedOperations.remove(operationId)
        staged?.stagingDir?.deleteRecursively()
    }

    fun getImportStatus(operationId: String): String = synchronized(importLock) {
        val lastCommitted = metaPrefs.getString(KEY_LAST_COMMITTED_OPERATION, "")
        if (operationId.isNotBlank() && operationId == lastCommitted) {
            return "COMMITTED"
        }
        return stagedOperations[operationId]?.status ?: "IDLE"
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
