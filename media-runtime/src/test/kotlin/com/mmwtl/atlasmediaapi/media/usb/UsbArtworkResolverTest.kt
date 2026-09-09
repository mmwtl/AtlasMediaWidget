package com.mmwtl.atlasmediaapi.media.usb

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class UsbArtworkResolverTest {

    private class MockContext : android.content.ContextWrapper(null)

    @Test
    fun resolveUsbArtwork_whenUriEmpty_returnsNull() {
        val resolver = UsbArtworkResolver(MockContext())
        val result = resolver.resolveUsbArtwork("", "", "", "")
        assertNull(result)
    }

    @Test
    fun resolveUsbArtwork_whenUriProvided_resolvesArtworkInput() {
        val tempFile = File.createTempFile("test_track", ".mp3").apply {
            writeBytes(byteArrayOf(0, 1, 2))
            deleteOnExit()
        }

        val resolver = UsbArtworkResolver(MockContext())
        val result = resolver.resolveUsbArtwork(
            uriString = tempFile.absolutePath,
            title = "Track Title",
            artist = "Track Artist",
            album = "Album",
        )

        assertNotNull(result)
        assertEquals(tempFile.absolutePath, result?.sourceUri)
    }
}
