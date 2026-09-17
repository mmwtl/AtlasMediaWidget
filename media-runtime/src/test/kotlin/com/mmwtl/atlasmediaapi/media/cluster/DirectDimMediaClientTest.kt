package com.mmwtl.atlasmediaapi.media.cluster

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Binder
import android.os.Parcel
import android.os.SystemClock
import com.mmwtl.atlasmediaapi.media.bridge.MediaSnapshot
import com.mmwtl.atlasmediaapi.media.bridge.BridgeAudioSource
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DirectDimMediaClientTest {
    private class RecordingContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getApplicationContext(): Context = this
        lateinit var connection: ServiceConnection
        lateinit var intent: Intent
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            this.intent = intent
            this.connection = connection
            return true
        }
    }

    private fun payload(artwork: Uri? = null) = DirectDimMediaClient.Payload(
        6, "online-track", "Title", "Album", "Artist", artwork, 120000L, 1, "", 0, "",
    )

    @Test
    fun `online packet without cover preserves text and has no legacy artwork gate`() {
        val context = RecordingContext()
        val client = DirectDimMediaClient(context)
        var calls = 0
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("com.autolink.adapterbinder.IDimMediaInteractioncService")
                assertEquals(1, code)
                assertEquals(1, data.readInt())
                assertEquals(6, data.readInt())
                assertEquals("online-track", data.readString())
                assertEquals(0, data.readInt())
                assertEquals("Title", data.readString())
                assertEquals("Album", data.readString())
                assertEquals("Artist", data.readString())
                assertNull(data.readString())
                assertNull(data.readParcelable<Uri>(Uri::class.java.classLoader))
                reply!!.writeNoException()
                calls++
                return true
            }
        }
        context.connection.onServiceConnected(context.intent.component!!, binder)
        assertTrue(client.sendOrQueue(payload()).startsWith("sent-"))
        assertEquals(1, calls)
        client.setTransmissionEnabled(false)
        assertEquals("disabled", client.sendOrQueue(payload()))
        assertEquals(1, calls)
    }

    @Test
    fun `clearing queued packet prevents replay after service connects`() {
        val context = RecordingContext()
        val client = DirectDimMediaClient(context)
        assertEquals("queued", client.sendOrQueue(payload()))
        client.clearPending()
        var calls = 0
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                calls++
                reply!!.writeNoException()
                return true
            }
        }
        context.connection.onServiceConnected(context.intent.component!!, binder)
        assertEquals(0, client.status().sendCount)
        assertEquals(0, calls)
    }
    @Test
    fun `online opt in is independent of radio and stops on source change`() {
        val context = RecordingContext()
        context.getSharedPreferences(ClusterMediaBridge.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val bridge = ClusterMediaBridge(context)
        var calls = 0
        context.connection.onServiceConnected(context.intent.component!!, object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                calls++
                reply!!.writeNoException()
                return true
            }
        })
        val snapshot = MediaSnapshot(
            backendConnected = true, audioSource = "ONLINE", ownerPackage = "player",
            mediaId = "track", title = "Title", playbackState = 3,
        )
        bridge.setActiveSource(BridgeAudioSource.ONLINE)
        bridge.updateOnlinePlayback(snapshot, null)
        assertEquals(0, calls)
        bridge.setClusterOnlineEnabled(true)
        bridge.setClusterCoversEnabled(false)
        bridge.updateOnlinePlayback(snapshot, null)
        assertEquals(1, calls)
        bridge.updateOnlinePlayback(snapshot.copy(position = 1000L), null)
        assertEquals(1, calls)
        bridge.setActiveSource(BridgeAudioSource.RADIO)
        bridge.updateOnlinePlayback(snapshot.copy(title = "Stale"), null)
        assertEquals(1, calls)
        bridge.setActiveSource(BridgeAudioSource.ONLINE)
        bridge.setClusterOnlineEnabled(false)
        bridge.updateOnlinePlayback(snapshot, null)
        assertEquals(1, calls)
        bridge.setClusterOnlineEnabled(true)
        bridge.setActiveSource(BridgeAudioSource.UNKNOWN)
        bridge.updateOnlinePlayback(snapshot, null)
        assertEquals(1, calls)
    }

    @Test
    fun `current packet is replayed after reconnection`() {
        val context = RecordingContext()
        val client = DirectDimMediaClient(context)
        val replayed = java.util.concurrent.CountDownLatch(2)
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                reply!!.writeNoException()
                replayed.countDown()
                return true
            }
        }
        context.connection.onServiceConnected(context.intent.component!!, binder)
        assertTrue(client.sendOrQueue(payload()).startsWith("sent-"))
        context.connection.onServiceDisconnected(context.intent.component!!)
        context.connection.onServiceConnected(context.intent.component!!, binder)
        assertTrue(replayed.await(2, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test
    fun `online progress waits for initial card without resending metadata`() {
        val context = RecordingContext()
        context.getSharedPreferences(ClusterMediaBridge.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val bridge = ClusterMediaBridge(context)
        val packets = java.util.Collections.synchronizedList(
            mutableListOf<Pair<String, Long>>(),
        )
        val received = java.util.concurrent.CountDownLatch(1)
        context.connection.onServiceConnected(context.intent.component!!, object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("com.autolink.adapterbinder.IDimMediaInteractioncService")
                assertEquals(1, code)
                assertEquals(1, data.readInt())
                data.readInt() // source_type
                data.readString() // uuid
                data.readInt() // mute_state
                val title = data.readString().orEmpty()
                data.readString() // album
                data.readString() // artist
                data.readString() // legacy artwork gate
                data.readParcelable<Uri>(Uri::class.java.classLoader)
                repeat(4) { data.readString() }
                data.readLong() // duration
                val progress = data.readLong()
                packets += title to progress
                received.countDown()
                reply!!.writeNoException()
                return true
            }
        })
        bridge.setActiveSource(BridgeAudioSource.ONLINE)
        bridge.setClusterOnlineEnabled(true)
        bridge.setClusterOnlineProgressEnabled(true)
        val snapshot = MediaSnapshot(
            backendConnected = true,
            audioSource = BridgeAudioSource.ONLINE.name,
            ownerPackage = "player",
            mediaId = "track",
            title = "Title",
            duration = 120_000L,
            position = 1_000L,
            updateElapsedRealtime = SystemClock.elapsedRealtime(),
            speed = 1f,
            playbackState = 3,
        )
        bridge.updateOnlinePlayback(snapshot, null)
        bridge.updateOnlinePlayback(snapshot.copy(position = 2_000L), null)

        assertTrue(received.await(1, java.util.concurrent.TimeUnit.SECONDS))
        Thread.sleep(ClusterMediaBridge.ONLINE_PROGRESS_INITIAL_DELAY_MS + 250L)
        bridge.setActiveSource(BridgeAudioSource.RADIO)
        assertEquals(1, packets.size)
        assertEquals("Title", packets.single().first)
        assertTrue(packets.single().second >= 1_000L)
    }

}
