package com.mmwtl.atlasmediaapi.media.bridge

import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationMapperTest {
    @Test
    fun `local catalog is authoritative for widget name cover and band`() {
        val vendor = Frequency(
            100_100,
            1,
            "Vendor Ensemble",
            "Vendor RDS",
            "Pop",
            42,
            87,
            "fm:100100",
            true,
        )
        val catalog = RadioStation(
            frequencyKHz = 100_100,
            name = "Радио 7 из локального архива",
            band = "FM",
            coverFileName = "radio7.webp",
        )

        val result = vendor.toRadioStationSnapshot(
            catalogStation = catalog,
            favorite = false,
            artworkUri = "content://atlas/radio7.webp",
        )

        assertEquals("Радио 7 из локального архива", result.name)
        assertEquals("content://atlas/radio7.webp", result.artworkUri)
        assertEquals("FM", result.bandName)
        assertEquals("100.1 MHz", result.formattedFrequency)
        assertEquals("Vendor RDS", result.serviceName)
        assertEquals("Vendor Ensemble", result.ensembleName)
        assertEquals("Pop", result.genre)
        assertEquals(42, result.iconId)
        assertEquals(87, result.signalQuality)
        assertEquals("fm:100100", result.selector)
        assertTrue(result.favorite)
    }

    @Test
    fun `vendor title and formatted frequency are fallbacks outside local catalog`() {
        val vendorTitle = Frequency(94_200, 1, "", "Vendor Station", "", 0, 0, "")
        val frequencyOnly = Frequency(95_200, 1, "", "", "", 0, 0, "")

        assertEquals(
            "Vendor Station",
            vendorTitle.toRadioStationSnapshot(null, false, "").name,
        )
        assertEquals(
            "95.2 MHz",
            frequencyOnly.toRadioStationSnapshot(null, false, "").name,
        )
        assertFalse(frequencyOnly.toRadioStationSnapshot(null, false, "").favorite)
    }

    @Test
    fun `scan and named variants merge while richer metadata wins`() {
        val scan = Frequency(100_100, 1, "", "", "", 0, 40, "")
        val named = Frequency(100_100, 1, "Ensemble", "Named", "Pop", 5, 80, "")

        val merged = mergeRadioStations(listOf(scan, named))

        assertEquals(1, merged.size)
        assertEquals("Named", merged.single().serviceName)
    }

    @Test
    fun `different DAB selectors remain separate stations on the same frequency`() {
        val first = Frequency(220_352, 3, "Mux", "One", "", 0, 80, "dab:1")
        val second = Frequency(220_352, 3, "Mux", "Two", "", 0, 80, "dab:2")

        assertEquals(2, mergeRadioStations(listOf(first, second)).size)
    }

    @Test
    fun `OIRT and long wave stations have informative frequency and band names`() {
        val oirt = Frequency(68_000, 1, "", "", "", 0, 0, "")
            .toRadioStationSnapshot(null, false, "")
        val longWave = Frequency(198, 2, "", "", "", 0, 0, "")
            .toRadioStationSnapshot(null, false, "")

        assertEquals("68 MHz", oirt.formattedFrequency)
        assertEquals("FM", oirt.bandName)
        assertEquals("198 kHz", longWave.formattedFrequency)
        assertEquals("AM", longWave.bandName)
    }

    @Test
    fun `station merge filters frequencies that cannot be tuned safely`() {
        val validOirt = Frequency(68_000, 1, "", "OIRT", "", 0, 0, "")
        val zero = Frequency(0, 1, "", "Zero", "", 0, 0, "")
        val negative = Frequency(-1, 2, "", "Negative", "", 0, 0, "")
        val unsupported = Frequency(12_345, 1, "", "Unsupported", "", 0, 0, "")

        assertEquals(listOf(validOirt), mergeRadioStations(listOf(zero, validOirt, negative, unsupported)))
    }

    @Test
    fun `tune target preserves all OneOS frequency fields`() {
        val target = RadioStationTarget(
            frequencyKHz = 220_352,
            band = 3,
            ensembleName = "Mux",
            serviceName = "Service",
            genre = "News",
            iconId = 11,
            signalQuality = 91,
            selector = "dab:service:7",
        )

        val frequency = target.toOneOsFrequency()

        assertEquals(target.frequencyKHz, frequency.frequency)
        assertEquals(target.band, frequency.band)
        assertEquals(target.ensembleName, frequency.ensembleName)
        assertEquals(target.serviceName, frequency.serviceName)
        assertEquals(target.genre, frequency.ensembleRdsPty)
        assertEquals(target.iconId, frequency.iconId)
        assertEquals(target.signalQuality, frequency.signalQuality)
        assertEquals(target.selector, frequency.selector)
    }
}
