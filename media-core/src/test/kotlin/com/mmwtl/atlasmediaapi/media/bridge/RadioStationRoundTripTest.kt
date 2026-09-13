package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadioStationRoundTripTest {
    @Test
    fun `existing FM AM and DAB representations remain supported`() {
        listOf(
            8_755 to 1,
            101_700 to 1,
            1_017 to 2,
            220_352 to 3,
        ).forEach { (frequency, band) ->
            assertNotNull(commandBundle(frequency, band).toMediaCommandRequest())
        }
    }

    @Test
    fun `OIRT and long wave station bundles are accepted back for tuning`() {
        listOf(
            station(frequency = 68_000, band = 1),
            station(frequency = 198, band = 2),
        ).forEach { station ->
            val response = RadioStationLists(saved = listOf(station), favorites = emptyList()).toBundle()
            val stationBundle = savedStationBundles(response).single()

            stationBundle.putString(MediaBridgeContract.Key.REQUEST_ID, "round-trip")
            stationBundle.putString(MediaBridgeContract.Key.COMMAND, MediaCommand.TUNE_RADIO.name)

            assertEquals(station.frequencyKHz, stationBundle.toMediaCommandRequest()?.radioStation?.frequencyKHz)
        }
    }

    @Test
    fun `RADIO_STATIONS to TUNE_RADIO preserves all OneOS tuning fields`() {
        val station = station(
            frequency = 220_352,
            band = 3,
            ensembleName = "Mux 12B",
            serviceName = "Service",
            genre = "News",
            iconId = 17,
            signalQuality = 91,
            selector = "dab:service:7",
        )
        val response = RadioStationLists(saved = listOf(station), favorites = emptyList()).toBundle()
        val command = savedStationBundles(response).single().apply {
            putString(MediaBridgeContract.Key.REQUEST_ID, "dab-round-trip")
            putString(MediaBridgeContract.Key.COMMAND, MediaCommand.TUNE_RADIO.name)
        }

        assertEquals(
            RadioStationTarget(
                frequencyKHz = station.frequencyKHz,
                band = station.band,
                ensembleName = station.ensembleName,
                serviceName = station.serviceName,
                genre = station.genre,
                iconId = station.iconId,
                signalQuality = station.signalQuality,
                selector = station.selector,
            ),
            command.toMediaCommandRequest()?.radioStation,
        )
    }

    @Test
    fun `invalid frequencies are rejected and are not serialized as stations`() {
        listOf(0, -1, 12_345).forEach { frequency ->
            assertFalse(isSupportedRadioFrequency(frequency))
            assertNull(commandBundle(frequency, band = 1).toMediaCommandRequest())
        }

        val response = RadioStationLists(
            saved = listOf(
                station(frequency = 0, band = 1),
                station(frequency = -1, band = 2),
                station(frequency = 12_345, band = 1),
                station(frequency = 101_700, band = 1),
            ),
            favorites = emptyList(),
        ).toBundle()

        assertEquals(101_700, savedStationBundles(response).single().getInt(MediaBridgeContract.Key.RADIO_FREQUENCY_KHZ))
    }

    @Test
    fun `mismatched or unknown OneOS bands are rejected`() {
        assertNull(commandBundle(101_700, band = 2).toMediaCommandRequest())
        assertNull(commandBundle(1_017, band = 1).toMediaCommandRequest())
        assertNull(commandBundle(220_352, band = 99).toMediaCommandRequest())
    }

    @Test
    fun `supported range boundaries are explicit`() {
        listOf(149, 284, 500, 1_800, 8_750, 10_800, 65_800, 74_000, 87_500, 108_000, 174_000, 240_000)
            .forEach { assertTrue(isSupportedRadioFrequency(it)) }
        listOf(0, 148, 285, 499, 1_801, 8_749, 10_801, 65_799, 74_001, 87_499, 108_001, 173_999, 240_001)
            .forEach { assertFalse(isSupportedRadioFrequency(it)) }
    }

    private fun commandBundle(frequency: Int, band: Int): Bundle = Bundle().apply {
        putString(MediaBridgeContract.Key.REQUEST_ID, "request-$frequency")
        putString(MediaBridgeContract.Key.COMMAND, MediaCommand.TUNE_RADIO.name)
        putInt(MediaBridgeContract.Key.RADIO_FREQUENCY_KHZ, frequency)
        putInt(MediaBridgeContract.Key.RADIO_BAND, band)
    }

    @Suppress("DEPRECATION")
    private fun savedStationBundles(response: Bundle): ArrayList<Bundle> =
        requireNotNull(response.getParcelableArrayList(MediaBridgeContract.Key.RADIO_SAVED_STATIONS))

    private fun station(
        frequency: Int,
        band: Int,
        ensembleName: String = "",
        serviceName: String = "",
        genre: String = "",
        iconId: Int = 0,
        signalQuality: Int = 0,
        selector: String = "",
    ): RadioStationSnapshot = RadioStationSnapshot(
        id = "radio:$band:$frequency:$selector:$serviceName",
        frequencyKHz = frequency,
        formattedFrequency = formatRadioFrequency(frequency),
        band = band,
        bandName = resolveRadioBandName(frequency, band),
        name = serviceName.ifBlank { formatRadioFrequency(frequency) },
        ensembleName = ensembleName,
        serviceName = serviceName,
        genre = genre,
        iconId = iconId,
        signalQuality = signalQuality,
        selector = selector,
    )
}
