package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.io.StringReader

class RadioCatalogCsvTest {

    @Test
    fun parseValidManifest() {
        val csv = """
            frequency_khz,name,band,cover
            100100,Радио 7 на семи холмах,FM,100100_radio_7.webp
            999,Пример AM,AM,example.webp
            101800,"Моя станция, Пенза",FM,local.png
        """.trimIndent()

        val rows = RadioCatalogCsv.read(StringReader(csv))
        assertEquals(3, rows.size)

        assertEquals(100100, rows[0].frequencyKHz)
        assertEquals("Радио 7 на семи холмах", rows[0].name)
        assertEquals("FM", rows[0].band)
        assertEquals("100100_radio_7.webp", rows[0].coverFileName)

        assertEquals(999, rows[1].frequencyKHz)
        assertEquals("Пример AM", rows[1].name)
        assertEquals("AM", rows[1].band)
        assertEquals("example.webp", rows[1].coverFileName)

        assertEquals(101800, rows[2].frequencyKHz)
        assertEquals("Моя станция, Пенза", rows[2].name)
        assertEquals("FM", rows[2].band)
        assertEquals("local.png", rows[2].coverFileName)
    }

    @Test
    fun parseEmptyCoverField() {
        val csv = """
            frequency_khz,name,band,cover
            100100,Радио без обложки,FM,
        """.trimIndent()

        val rows = RadioCatalogCsv.read(StringReader(csv))
        assertEquals(1, rows.size)
        assertEquals(100100, rows[0].frequencyKHz)
        assertEquals("", rows[0].coverFileName)
    }

    @Test
    fun parseUtf8Bom() {
        val csv = "\uFEFFfrequency_khz,name,band,cover\n100100,Радио 7,FM,radio7.webp\n"
        val rows = RadioCatalogCsv.read(StringReader(csv))
        assertEquals(1, rows.size)
        assertEquals(100100, rows[0].frequencyKHz)
    }

    @Test
    fun rejectInvalidHeader() {
        val csv = "freq,name,band,cover\n100100,Радио 7,FM,cover.webp"
        assertThrows(IOException::class.java) {
            RadioCatalogCsv.read(StringReader(csv))
        }
    }

    @Test
    fun rejectDuplicateFrequencies() {
        val csv = """
            frequency_khz,name,band,cover
            100100,Радио 7,FM,cover1.webp
            100100,Радио 7 Дубль,FM,cover2.webp
        """.trimIndent()

        assertThrows(IOException::class.java) {
            RadioCatalogCsv.read(StringReader(csv))
        }
    }

    @Test
    fun rejectOutOfRangeFrequency() {
        val csv = """
            frequency_khz,name,band,cover
            12345,Вне диапазона,FM,cover.webp
        """.trimIndent()

        assertThrows(IOException::class.java) {
            RadioCatalogCsv.read(StringReader(csv))
        }
    }

    @Test
    fun acceptsOirtAndLongWaveFrequencies() {
        val csv = """
            frequency_khz,name,band,cover
            68000,ОИРТ,FM,oirt.webp
            198,Длинные волны,AM,lw.webp
        """.trimIndent()

        val rows = RadioCatalogCsv.read(StringReader(csv))

        assertEquals(listOf(68_000, 198), rows.map { it.frequencyKHz })
        assertEquals(listOf("FM", "AM"), rows.map { it.band })
    }

    @Test
    fun rejectMismatchedBand() {
        val csv = """
            frequency_khz,name,band,cover
            100100,Радио 7,AM,cover.webp
        """.trimIndent()

        assertThrows(IOException::class.java) {
            RadioCatalogCsv.read(StringReader(csv))
        }
    }
}
