package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Parcel
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.listener.IRadioStateListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Frequency callbacks as G636 (parcelable) and 2026 g426 (plain int) MediaCenterService send them. */
@RunWith(RobolectricTestRunner::class)
class RadioStateListenerParcelTest {
    private val received = mutableListOf<Frequency?>()
    private val listener = object : IRadioStateListener.Stub() {
        override fun onCurrentFrequency(frequency: Frequency?) { received += frequency }
        override fun onStationFrequency(frequency: Frequency?) { received += frequency }
        override fun onOpenRadioResult(succeeded: Boolean) = Unit
        override fun onRadioClosed(succeeded: Boolean) = Unit
        override fun onCurrentBand(band: Int) = Unit
        override fun onSeekStarted(direction: Int) = Unit
        override fun onSeekStopped(succeeded: Boolean) = Unit
        override fun onScanStarted(direction: Int) = Unit
        override fun onScanStopped(succeeded: Boolean) = Unit
        override fun onSignalQualityChanged(level: Int) = Unit
        override fun onCarouselPlayStart(succeeded: Boolean) = Unit
        override fun onCarouserPlayStop(frequency: Int) = Unit
        override fun onCarouserWaiting(frequency: Int, leftTime: Long) = Unit
        override fun onRadioNameListChanged(frequencyList: MutableList<Frequency>?) = Unit
        override fun onCollectionStationListReachesMax() = Unit
        override fun onCollectionStationListChanged(
            frequencyList: MutableList<Frequency>?,
            frequency: Int,
            collection: Boolean,
        ) = Unit
        override fun onScanStationListChanged(frequencyList: MutableList<Frequency>?) = Unit
        override fun onRadioStatusChanged(status: Int) = Unit
        override fun onRadioMuteState(state: Int) = Unit
        override fun onRadioError(errorCode: Int) = Unit
    }

    @Test
    fun parcelableFrequencyKeepsItsFields() {
        val sent = Frequency(101_500, 1, "ensemble", "Europa Plus", "", 0, 0, "")
        val reply = dispatch(CURRENT_FREQUENCY) { data ->
            data.writeInt(1)
            sent.writeToParcel(data, 0)
        }

        val value = received.single()!!
        assertEquals(101_500, value.frequency)
        assertEquals(1, value.band)
        assertEquals("Europa Plus", value.serviceName)
        assertFalse(value.isBareValue)
        reply.readException()
        assertEquals(1, reply.readInt())
        reply.recycle()
    }

    @Test
    fun plainIntFrequencyBecomesBareValueWithUnknownBand() {
        dispatch(STATION_FREQUENCY) { it.writeInt(101_500) }.recycle()

        val value = received.single()!!
        assertEquals(101_500, value.frequency)
        assertEquals(Frequency.BAND_UNKNOWN, value.band)
        assertTrue(value.isBareValue)
        assertNull(value.serviceName)
    }

    @Test
    fun absentFrequencyStaysNull() {
        dispatch(CURRENT_FREQUENCY) { it.writeInt(0) }.recycle()

        assertNull(received.single())
    }

    private fun dispatch(code: Int, write: (Parcel) -> Unit): Parcel {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        data.writeInterfaceToken(DESCRIPTOR)
        write(data)
        data.setDataPosition(0)
        assertTrue(listener.onTransact(code, data, reply, 0))
        data.recycle()
        reply.setDataPosition(0)
        return reply
    }

    private companion object {
        const val DESCRIPTOR = "com.geely.lib.oneosapi.mediacenter.listener.IRadioStateListener"
        const val CURRENT_FREQUENCY = 4
        const val STATION_FREQUENCY = 5
    }
}
