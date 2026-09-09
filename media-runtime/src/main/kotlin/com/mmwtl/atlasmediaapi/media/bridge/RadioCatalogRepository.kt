package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class RadioCatalogType {
    BUILT_IN,
    CUSTOM,
}

data class RadioCatalogInfo(
    val type: RadioCatalogType,
    val stationCount: Int,
    val isWidgetBroadcastEnabled: Boolean,
    val description: String,
)

class RadioCatalogRepository(
    private val context: Context,
) {
    companion object {
        const val PREFS_NAME = "radio_catalog_prefs"
        // Keep the legacy preference value so existing installations preserve the switch state.
        const val KEY_WIDGET_BROADCAST_ENABLED = "radio_covers_enabled"
        const val KEY_CUSTOM_CATALOG_ACTIVE = "custom_catalog_active"

        const val BUILT_IN_DIR = "radio"
        const val MANIFEST_NAME = "stations.csv"
        const val COVERS_DIR = "covers"

        const val MAX_ZIP_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L // 64 MB
        const val MAX_ZIP_ENTRIES = 300
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val customDirectory: File = File(context.filesDir, "custom_radio")
    private val widgetCoverDirectory: File = File(context.cacheDir, "radio_station_covers")

    @Volatile
    private var activeStations: Map<Int, RadioStation> = emptyMap()

    @Volatile
    private var currentType: RadioCatalogType = RadioCatalogType.BUILT_IN

    @Volatile
    var isWidgetBroadcastEnabled: Boolean = prefs.getBoolean(KEY_WIDGET_BROADCAST_ENABLED, true)
        private set

    private val _catalogInfoFlow = MutableStateFlow(buildCatalogInfo())
    val catalogInfoFlow: StateFlow<RadioCatalogInfo> = _catalogInfoFlow.asStateFlow()

    init {
        reloadCatalog()
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
        val useCustom = prefs.getBoolean(KEY_CUSTOM_CATALOG_ACTIVE, false)
        if (useCustom && customDirectory.isDirectory) {
            val manifestFile = File(customDirectory, MANIFEST_NAME)
            if (manifestFile.isFile) {
                val loaded = runCatching {
                    InputStreamReader(FileInputStream(manifestFile), StandardCharsets.UTF_8).use {
                        RadioCatalogCsv.read(it)
                    }
                }.getOrNull()

                if (!loaded.isNullOrEmpty()) {
                    activeStations = loaded.associateBy { it.frequencyKHz }
                    currentType = RadioCatalogType.CUSTOM
                    _catalogInfoFlow.value = buildCatalogInfo()
                    return
                }
            }
        }

        // Default / Built-in
        val builtIn = runCatching {
            InputStreamReader(context.assets.open("$BUILT_IN_DIR/$MANIFEST_NAME"), StandardCharsets.UTF_8).use {
                RadioCatalogCsv.read(it)
            }
        }.onFailure(Timber::e).getOrDefault(emptyList())

        activeStations = builtIn.associateBy { it.frequencyKHz }
        currentType = RadioCatalogType.BUILT_IN
        _catalogInfoFlow.value = buildCatalogInfo()
    }

    fun lookup(frequencyKHz: Int): RadioStation? {
        return activeStations[frequencyKHz]
    }

    /** Immutable active catalog for clients that need to present every imported station. */
    fun stations(): List<RadioStation> = activeStations.values.sortedBy(RadioStation::frequencyKHz)

    fun artworkUri(station: RadioStation): String {
        if (station.coverFileName.isBlank()) return ""
        val extension = station.coverFileName.substringAfterLast('.', "bin")
            .lowercase()
            .takeIf { it in setOf("webp", "png", "jpg", "jpeg") }
            ?: "bin"
        val catalogKey = currentType.name.lowercase()
        val target = File(widgetCoverDirectory, "${catalogKey}_${station.frequencyKHz}.$extension")
        return runCatching {
            widgetCoverDirectory.mkdirs()
            if (!target.isFile || target.length() <= 0L) {
                val temporary = File.createTempFile(
                    "${target.nameWithoutExtension}-",
                    ".tmp",
                    widgetCoverDirectory,
                )
                try {
                    openCoverStream(station)?.use { input ->
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

    fun openCoverStream(station: RadioStation): InputStream? {
        if (station.coverFileName.isBlank()) return null
        return when (currentType) {
            RadioCatalogType.BUILT_IN -> {
                runCatching {
                    context.assets.open("$BUILT_IN_DIR/$COVERS_DIR/${station.coverFileName}")
                }.getOrNull()
            }
            RadioCatalogType.CUSTOM -> {
                val coversDirectory = File(customDirectory, COVERS_DIR)
                val file = File(coversDirectory, station.coverFileName)
                val safePath = runCatching {
                    file.canonicalFile.parentFile == coversDirectory.canonicalFile
                }.getOrDefault(false)
                if (safePath && file.isFile && file.length() > 0L) {
                    runCatching { FileInputStream(file) }.getOrNull()
                } else null
            }
        }
    }

    @Synchronized
    fun restoreDefaultCatalog() {
        customDirectory.deleteRecursively()
        prefs.edit().putBoolean(KEY_CUSTOM_CATALOG_ACTIVE, false).apply()
        reloadCatalog()
    }

    @Synchronized
    fun importCustomZip(inputStream: InputStream): Result<Int> = runCatching {
        val tempStagingDir = File(context.cacheDir, "staging_radio_${System.currentTimeMillis()}")
        tempStagingDir.deleteRecursively()
        tempStagingDir.mkdirs()

        var totalExtractedBytes = 0L
        var totalEntries = 0

        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                totalEntries++
                if (totalEntries > MAX_ZIP_ENTRIES) {
                    throw IllegalStateException("Превышено максимальное количество файлов в архиве ($MAX_ZIP_ENTRIES)")
                }

                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                val targetFile = File(tempStagingDir, normalizedName)
                if (!targetFile.canonicalPath.startsWith(tempStagingDir.canonicalPath)) {
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

        val manifestFile = File(tempStagingDir, MANIFEST_NAME)
        if (!manifestFile.isFile) {
            tempStagingDir.deleteRecursively()
            throw IllegalArgumentException("В корне архива отсутствует файл $MANIFEST_NAME")
        }

        val parsedStations = InputStreamReader(FileInputStream(manifestFile), StandardCharsets.UTF_8).use {
            RadioCatalogCsv.read(it)
        }

        if (parsedStations.isEmpty()) {
            tempStagingDir.deleteRecursively()
            throw IllegalArgumentException("Каталог не содержит валидных радиостанций")
        }

        // Atomically swap custom directory
        customDirectory.deleteRecursively()
        if (!tempStagingDir.renameTo(customDirectory)) {
            // Fallback to copy if cross-device rename fails
            customDirectory.mkdirs()
            tempStagingDir.copyRecursively(customDirectory, overwrite = true)
            tempStagingDir.deleteRecursively()
        }

        prefs.edit().putBoolean(KEY_CUSTOM_CATALOG_ACTIVE, true).apply()
        reloadCatalog()
        parsedStations.size
    }

    fun exportSampleZip(outputStream: OutputStream) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            // Add stations.csv
            val manifestBytes = context.assets.open("$BUILT_IN_DIR/$MANIFEST_NAME").use { it.readBytes() }
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            zos.write(manifestBytes)
            zos.closeEntry()

            // Add covers
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

    fun createSampleZipFile(): File {
        val exportDir = File(context.cacheDir, "exported_samples")
        exportDir.mkdirs()
        val sampleZip = File(exportDir, "radio-catalog-sample.zip")
        FileOutputStream(sampleZip).use { exportSampleZip(it) }
        return sampleZip
    }

    private fun buildCatalogInfo(): RadioCatalogInfo {
        val count = activeStations.size
        val desc = when (currentType) {
            RadioCatalogType.BUILT_IN -> "Встроенный каталог (Пенза, $count станций)"
            RadioCatalogType.CUSTOM -> "Пользовательский каталог ($count станций)"
        }
        return RadioCatalogInfo(
            type = currentType,
            stationCount = count,
            isWidgetBroadcastEnabled = isWidgetBroadcastEnabled,
            description = desc,
        )
    }
}
