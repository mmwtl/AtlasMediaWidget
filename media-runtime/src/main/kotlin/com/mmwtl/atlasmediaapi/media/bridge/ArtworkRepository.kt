package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max

data class ArtworkInput(
    val bitmap: Bitmap? = null,
    val sourceUri: String = "",
)

data class NormalizedArtwork(
    val token: String,
    val uri: String,
)

fun interface ArtworkNormalizer {
    fun normalize(
        input: ArtworkInput,
        onResult: (NormalizedArtwork) -> Unit,
    )
}

/** Normalizes OEM/session artwork into a small private cache exposed only by per-client URI grants. */
class ArtworkRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) : ArtworkNormalizer {
    companion object {
        private const val MAX_EDGE_PX = 512
        private const val JPEG_QUALITY = 88
        private const val MAX_CACHE_FILES = 48
    }

    private val cacheDirectory = File(context.cacheDir, "media_artwork")

    override fun normalize(
        input: ArtworkInput,
        onResult: (NormalizedArtwork) -> Unit,
    ) {
        if (input.bitmap == null && input.sourceUri.isBlank()) {
            onResult(NormalizedArtwork("", ""))
            return
        }

        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                cacheDirectory.mkdirs()
                val decoded = input.bitmap ?: decodeUri(input.sourceUri)
                decoded ?: return@runCatching null
                var prepared: Bitmap? = null
                try {
                    prepared = prepare(decoded)
                    val token = ArtworkContentIdentity.token(prepared)
                    val target = File(cacheDirectory, "$token.jpg")
                    if (!target.isFile || target.length() <= 0L) {
                        writeNormalized(prepared, target)
                    }
                    pruneCache(target)
                    NormalizedArtwork(token, uriFor(target).toString())
                } finally {
                    if (prepared != null && prepared !== decoded) prepared.recycle()
                    if (input.bitmap == null) decoded.recycle()
                }
            }.onFailure(Timber::e).getOrNull()
            onResult(result ?: NormalizedArtwork("", ""))
        }
    }

    private fun decodeUri(value: String): Bitmap? {
        val uri = runCatching { value.toUri() }.getOrNull() ?: return extractEmbeddedArtwork(value)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(value, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return extractEmbeddedArtwork(value)
        }

        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE_PX * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = openInputStream(value, uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
        return decoded ?: extractEmbeddedArtwork(value)
    }

    fun extractEmbeddedArtwork(filePath: String): Bitmap? {
        if (filePath.isBlank()) return null
        return runCatching {
            val uri = runCatching { filePath.toUri() }.getOrNull()
            val rawPath = when {
                uri?.scheme == "file" -> uri.path
                uri?.scheme.isNullOrBlank() -> filePath
                else -> null
            }
            val decodedPath = rawPath?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }

            val mmr = MediaMetadataRetriever()
            try {
                if (decodedPath != null) {
                    val file = File(decodedPath).takeIf { it.exists() }
                        ?: rawPath?.let { File(it) }?.takeIf { it.exists() }
                        ?: File(filePath)
                    if (file.exists()) {
                        runCatching {
                            FileInputStream(file).use { fis ->
                                mmr.setDataSource(fis.fd)
                            }
                        }.getOrElse {
                            mmr.setDataSource(file.absolutePath)
                        }
                    } else {
                        mmr.setDataSource(decodedPath)
                    }
                } else if (uri != null && uri.scheme == "content") {
                    mmr.setDataSource(context, uri)
                } else {
                    return@runCatching null
                }
                val picture = mmr.embeddedPicture ?: return@runCatching null
                BitmapFactory.decodeByteArray(picture, 0, picture.size)
            } finally {
                runCatching { mmr.release() }
            }
        }.onFailure(Timber::e).getOrNull()
    }

    private fun openInputStream(value: String, uri: Uri): java.io.InputStream? = runCatching {
        when {
            uri.scheme == "content" -> context.contentResolver.openInputStream(uri)
            uri.scheme == "file" -> {
                val decodedPath = uri.path?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }
                val file = decodedPath?.let(::File)?.takeIf { it.exists() }
                    ?: uri.path?.let(::File)?.takeIf { it.exists() }
                file?.let(::FileInputStream)
            }
            uri.scheme.isNullOrBlank() -> {
                val decodedPath = runCatching { Uri.decode(value) }.getOrDefault(value)
                val file = File(decodedPath).takeIf { it.exists() }
                    ?: File(value).takeIf { it.exists() }
                file?.let(::FileInputStream)
            }
            else -> context.contentResolver.openInputStream(uri)
        }
    }.onFailure(Timber::e).getOrNull()

    private fun prepare(source: Bitmap): Bitmap {
        val largest = max(source.width, source.height)
        val scaled = if (largest > MAX_EDGE_PX) {
            val scale = MAX_EDGE_PX.toFloat() / largest.toFloat()
            source.scale(
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
            )
        } else source
        if (scaled.config != Bitmap.Config.HARDWARE) return scaled
        return checkNotNull(scaled.copy(Bitmap.Config.ARGB_8888, false))
    }

    private fun writeNormalized(source: Bitmap, target: File) {
        val temporary = File.createTempFile(
            "${target.nameWithoutExtension}-",
            ".tmp",
            target.parentFile,
        )
        try {
            FileOutputStream(temporary).use { stream ->
                check(source.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream))
                stream.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                check(target.isFile && target.length() > 0L) {
                    "Unable to publish artwork cache file"
                }
            }
            target.setReadable(true, false)
        } finally {
            temporary.delete()
        }
    }

    fun getCacheFile(token: String): File = File(cacheDirectory, "$token.jpg")

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun pruneCache(keep: File) {
        cacheDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it != keep && !it.name.endsWith(".tmp") }
            .sortedByDescending(File::lastModified)
            .drop(MAX_CACHE_FILES - 1)
            .forEach(File::delete)
    }
}
