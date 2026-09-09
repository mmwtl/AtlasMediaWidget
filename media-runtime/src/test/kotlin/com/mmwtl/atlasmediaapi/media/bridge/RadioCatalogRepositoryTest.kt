package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class RadioCatalogRepositoryTest {

    @Test
    fun `custom zip archive parses stations and full replacement ignores unlisted frequencies`() {
        val csv = """
            frequency_khz,name,band,cover
            90000,Custom Station 1,FM,cover1.png
            91000,Custom Station 2,FM,cover2.png
        """.trimIndent()

        val parsed = RadioCatalogCsv.read(StringReader(csv))
        assertEquals(2, parsed.size)
        val customMap = parsed.associateBy { it.frequencyKHz }

        // In CUSTOM mode, Penza 100100 is NOT present
        assertNull(customMap[100100])
        assertNotNull(customMap[90000])
        assertEquals("Custom Station 1", customMap[90000]?.name)
        assertEquals("cover1.png", customMap[90000]?.coverFileName)
    }

    @Test
    fun `zip packaging and unpack validation roundtrip`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("stations.csv"))
            zos.write("frequency_khz,name,band,cover\n94200,Station A,FM,a.png\n".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("covers/a.png"))
            zos.write(byteArrayOf(1, 2, 3, 4))
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        var foundManifest = false
        var foundCover = false

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "stations.csv") {
                    foundManifest = true
                    val content = zis.readBytes().toString(StandardCharsets.UTF_8)
                    val stations = RadioCatalogCsv.read(StringReader(content))
                    assertEquals(1, stations.size)
                    assertEquals(94200, stations[0].frequencyKHz)
                } else if (entry.name == "covers/a.png") {
                    foundCover = true
                    val bytes = zis.readBytes()
                    assertEquals(4, bytes.size)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(foundManifest)
        assertTrue(foundCover)
    }

    @Test
    fun `withRadioState updates artworkUri and artworkRevision`() {
        val snapshot = MediaSnapshot(
            generation = 1L,
            backendConnected = true,
            audioSource = BridgeAudioSource.RADIO.name,
            artworkUri = "",
            artworkRevision = 0L,
        )

        val station = RadioMetadata(
            mediaId = "radio:0:100100:Радио 7",
            title = "Радио 7 на семи холмах",
            subtitle = "100.1 MHz",
        )

        val updated = snapshot.withRadioState(
            station = station,
            playing = true,
            elapsedRealtime = 12345L,
            artworkUri = "content://com.mmwtl.atlasmediaapi.fileprovider/media_artwork/abc.jpg",
        )

        assertEquals("Радио 7 на семи холмах", updated.title)
        assertEquals("content://com.mmwtl.atlasmediaapi.fileprovider/media_artwork/abc.jpg", updated.artworkUri)
        assertEquals(1L, updated.artworkRevision)

        // Repeat with same artwork -> revision stays unchanged
        val sameArtwork = updated.withRadioState(
            station = station,
            playing = true,
            elapsedRealtime = 12346L,
            artworkUri = "content://com.mmwtl.atlasmediaapi.fileprovider/media_artwork/abc.jpg",
        )
        assertEquals(1L, sameArtwork.artworkRevision)

        // Radio covers disabled / cleared -> revision increments and artworkUri becomes empty
        val cleared = sameArtwork.withRadioState(
            station = station,
            playing = true,
            elapsedRealtime = 12347L,
            artworkUri = "",
        )
        assertEquals("", cleared.artworkUri)
        assertEquals(2L, cleared.artworkRevision)
    }
}
