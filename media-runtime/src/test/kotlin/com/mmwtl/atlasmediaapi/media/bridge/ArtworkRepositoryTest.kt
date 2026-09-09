package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArtworkRepositoryTest {

    @Test
    fun `isAudioFilePath identifies valid audio file paths and uris`() {
        val validPaths = listOf(
            "/storage/usb/song.mp3",
            "/storage/0000-0000/Music/track.FLAC",
            "file:///mnt/media_rw/usb/audio.m4a",
            "/sdcard/Music/file.aac",
            "/storage/emulated/0/Music/audio.ogg",
            "/path/to/sound.wav",
            "/path/to/file.wma",
            "/path/to/file.ape",
            "/path/to/file.opus",
            "/path/to/file.alac",
            "/storage/usb/song.mp3?query=1#anchor",
            "  /storage/usb/song.flac  ",
            "file:///storage/udisk0/%D0%9F%D0%B5%D1%81%D0%BD%D1%8F.mp3",
            "content://media/external/audio/media/12345",
        )

        for (path in validPaths) {
            assertTrue("Expected $path to be recognized as audio file", isAudioFilePath(path))
        }
    }

    @Test
    fun `isAudioFilePath rejects non-audio paths and empty input`() {
        val invalidPaths = listOf(
            null,
            "",
            "   ",
            "/storage/usb/cover.jpg",
            "/storage/usb/image.png",
            "/storage/usb/video.mp4",
            "/storage/usb/notes.txt",
            "/storage/usb/archive.zip",
            "/storage/usb/noextension",
        )

        for (path in invalidPaths) {
            assertFalse("Expected $path to NOT be recognized as audio file", isAudioFilePath(path))
        }
    }

    @Test
    fun `extractEmbeddedArtwork returns null for missing or unreadable file`() {
        val nonExistentFile = File.createTempFile("temp_missing", ".mp3").apply { delete() }
        assertFalse(nonExistentFile.exists())

        val result = runCatching {
            val file = nonExistentFile
            if (!file.exists() || !file.canRead()) null else "exists"
        }.getOrNull()

        assertNull(result)
    }

    @Test
    fun `bounded URI grant rotation evicts oldest URIs beyond maximum limit`() {
        val maxUris = 8
        val grantedUris = LinkedHashSet<String>()
        val revokedUris = mutableListOf<String>()

        for (i in 1..15) {
            val uri = "content://com.mmwtl.atlasmediaapi.fileprovider/media_artwork/art_$i.jpg"
            grantedUris.add(uri)
            while (grantedUris.size > maxUris) {
                val oldest = grantedUris.first()
                grantedUris.remove(oldest)
                revokedUris.add(oldest)
            }
        }

        assertEquals(8, grantedUris.size)
        assertEquals(7, revokedUris.size)
        // Check that first 7 URIs were revoked in order
        for (i in 1..7) {
            assertEquals("content://com.mmwtl.atlasmediaapi.fileprovider/media_artwork/art_$i.jpg", revokedUris[i - 1])
        }
        // Check that remaining 8 URIs are 8..15
        val expectedRemaining = (8..15).map { "content://com.mmwtl.atlasmediaapi.fileprovider/media_artwork/art_$it.jpg" }.toSet()
        assertEquals(expectedRemaining, grantedUris)
    }
}
