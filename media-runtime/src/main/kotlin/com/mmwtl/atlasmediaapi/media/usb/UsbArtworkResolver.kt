package com.mmwtl.atlasmediaapi.media.usb

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.mmwtl.atlasmediaapi.media.bridge.ArtworkInput
import timber.log.Timber
import java.io.File

class UsbArtworkResolver(
    private val context: Context,
) {
    companion object {
        private const val ONEOS_THUMBNAIL_DIR = "/storage/emulated/0/Music/.thumbnails"
        private const val ALBUM_ART_COLUMN = "album_art"
    }

    fun resolveUsbArtwork(
        uriString: String,
        title: String,
        artist: String,
        album: String,
    ): ArtworkInput? {
        val decodedPath = if (uriString.isNotBlank()) {
            runCatching { Uri.decode(uriString) }.getOrDefault(uriString)
        } else ""

        // 1. Поиск через системный MediaStore ContentProvider
        val mediaStoreResult = queryMediaStore(decodedPath, title, artist, album)
        if (mediaStoreResult != null) {
            return mediaStoreResult
        }

        // 2. Если есть прямой путь к аудиофайлу на USB, используем его для извлечения ID3-обложки
        if (decodedPath.isNotBlank()) {
            val file = File(decodedPath.removePrefix("file://"))
            if (file.exists() && file.isFile) {
                return ArtworkInput(sourceUri = file.absolutePath)
            }
            if (uriString.isNotBlank()) {
                return ArtworkInput(sourceUri = uriString)
            }
        }

        return null
    }

    private fun queryMediaStore(
        filePath: String,
        title: String,
        artist: String,
        album: String,
    ): ArtworkInput? {
        val cr = runCatching { context.contentResolver }.getOrNull() ?: return null

        return runCatching {
            // Ищем трек в MediaStore по пути, названию или комбинации title + artist
            var cursor: Cursor? = null
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.TITLE,
                )

                // Попытка 1: точный поиск по пути файла (_data)
                if (filePath.isNotBlank()) {
                    val rawPath = filePath.removePrefix("file://")
                    cursor = cr.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        "${MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(rawPath),
                        null,
                    )
                }

                // Попытка 2: поиск по имени файла (_display_name)
                if ((cursor == null || cursor.count == 0) && filePath.isNotBlank()) {
                    val fileName = File(filePath).name
                    if (fileName.isNotBlank()) {
                        cursor?.close()
                        cursor = cr.query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                            arrayOf(fileName),
                            null,
                        )
                    }
                }

                // Попытка 3: поиск по title и artist
                if ((cursor == null || cursor.count == 0) && title.isNotBlank()) {
                    cursor?.close()
                    if (artist.isNotBlank()) {
                        cursor = cr.query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?",
                            arrayOf(title, artist),
                            null,
                        )
                    }
                    if (cursor == null || cursor.count == 0) {
                        cursor?.close()
                        cursor = cr.query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            "${MediaStore.Audio.Media.TITLE} = ?",
                            arrayOf(title),
                            null,
                        )
                    }
                }

                if (cursor != null && cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                    val mediaId = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
                    val albumId = if (albumIdIndex >= 0) cursor.getLong(albumIdIndex) else -1L

                    Timber.tag("UsbArtworkResolver").d(
                        "Found MediaStore entry: mediaId=%d, albumId=%d for title='%s'",
                        mediaId,
                        albumId,
                        title,
                    )

                    // Проверяем кэш миниатюр OneOS usbservice: /storage/emulated/0/Music/.thumbnails/<id>.jpg
                    if (mediaId > 0) {
                        val thumbnailFile = File(ONEOS_THUMBNAIL_DIR, "$mediaId.jpg")
                        if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
                            Timber.tag("UsbArtworkResolver").d(
                                "Found OneOS thumbnail: %s",
                                thumbnailFile.absolutePath,
                            )
                            return@runCatching ArtworkInput(sourceUri = thumbnailFile.absolutePath)
                        }
                    }

                    // Проверяем системный кэш обложек альбомов MediaStore
                    if (albumId > 0) {
                        val albumArtPath = queryAlbumArt(cr, albumId)
                        if (!albumArtPath.isNullOrBlank()) {
                            val artFile = File(albumArtPath)
                            if (artFile.exists() && artFile.length() > 0) {
                                Timber.tag("UsbArtworkResolver").d(
                                    "Found MediaStore album art file: %s",
                                    albumArtPath,
                                )
                                return@runCatching ArtworkInput(sourceUri = albumArtPath)
                            }
                        }

                        // Uri через content://media/external/audio/albumart/<albumId>
                        val albumUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId,
                        )
                        return@runCatching ArtworkInput(sourceUri = albumUri.toString())
                    }
                }
            } finally {
                cursor?.close()
            }
            null
        }.onFailure(Timber::e).getOrNull()
    }

    private fun queryAlbumArt(cr: ContentResolver, albumId: Long): String? {
        val albumUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albums"),
            albumId,
        )
        return runCatching {
            cr.query(
                albumUri,
                arrayOf(ALBUM_ART_COLUMN),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val colIndex = c.getColumnIndex(ALBUM_ART_COLUMN)
                    if (colIndex >= 0) c.getString(colIndex) else null
                } else null
            }
        }.onFailure(Timber::e).getOrNull()
    }
}
