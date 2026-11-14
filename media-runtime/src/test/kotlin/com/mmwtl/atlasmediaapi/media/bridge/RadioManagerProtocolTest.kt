package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Binder
import android.os.Parcel
import com.geely.lib.oneosapi.mediacenter.IRadioManager
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Station lists of G636 (nine-field Frequency) and 2026 g426 (four-field, no transaction 30). */
@RunWith(RobolectricTestRunner::class)
class RadioManagerProtocolTest {
    @Test
    fun compactServiceListsAreReadInItsLayout() {
        val radio = IRadioManager.Stub.asInterface(FakeRadioService(compact = true))

        assertNull(radio.getCurrentFrequency(1))
        val station = radio.scanStationsList.single()
        assertEquals(10_150, station.frequency)
        assertEquals(1, station.band)
        assertEquals("Europa Plus", station.serviceName)
        assertEquals(true, station.isCollection)
    }

    @Test
    fun compactLayoutIsDetectedBeforeTheFirstList() {
        val radio = IRadioManager.Stub.asInterface(FakeRadioService(compact = true))

        assertEquals("Europa Plus", radio.collectionStationsList.single().serviceName)
    }

    @Test
    fun legacyServiceListsKeepTheFullLayout() {
        val radio = IRadioManager.Stub.asInterface(FakeRadioService(compact = false))

        val station = radio.scanStationsList.single()
        assertEquals(101_500, station.frequency)
        assertEquals("Europa Plus", station.serviceName)
        assertEquals("ensemble", station.ensembleName)
    }

    private class FakeRadioService(private val compact: Boolean) : Binder() {
        // A foreign descriptor makes asInterface() return the Binder proxy, as for a remote service.
        init { attachInterface(null, "fake-radio-service") }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(DESCRIPTOR)
            return when (code) {
                GET_CURRENT_FREQUENCY -> if (compact) false else {
                    reply!!.writeNoException()
                    reply.writeInt(0)
                    true
                }
                GET_SCAN_STATIONS, GET_COLLECTION_STATIONS -> {
                    reply!!.writeNoException()
                    reply.writeInt(1)
                    reply.writeInt(1)
                    if (compact) {
                        reply.writeInt(10_150)
                        reply.writeInt(1)
                        reply.writeString("Europa Plus")
                        reply.writeInt(1)
                    } else {
                        Frequency(101_500, 1, "ensemble", "Europa Plus", "", 0, 0, "")
                            .writeToParcel(reply, 0)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private companion object {
        const val DESCRIPTOR = "com.geely.lib.oneosapi.mediacenter.IRadioManager"
        const val GET_SCAN_STATIONS = 14
        const val GET_COLLECTION_STATIONS = 16
        const val GET_CURRENT_FREQUENCY = 30
    }
}
