package com.mmwtl.atlasmediaapi.media.cluster

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Binder
import android.os.Parcel
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
    fun `legacy transport preferences are removed and online controls progress`() {
        val context = RecordingContext()
        val preferences = context.getSharedPreferences(
            ClusterMediaBridge.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        preferences.edit()
            .clear()
            .putBoolean(ClusterMediaBridge.KEY_CLUSTER_ONLINE_PROGRESS_ENABLED, true)
            .putBoolean("cluster_dim_online_facade_progress_enabled", true)
            .putBoolean("cluster_dim_radio_facade_enabled", false)
            .commit()

        val bridge = ClusterMediaBridge(context)

        assertFalse(bridge.isClusterOnlineProgressEnabled)
        assertFalse(preferences.contains(ClusterMediaBridge.KEY_CLUSTER_ONLINE_PROGRESS_ENABLED))
        assertFalse(preferences.contains("cluster_dim_online_facade_progress_enabled"))
        assertFalse(preferences.contains("cluster_dim_radio_facade_enabled"))

        bridge.setClusterOnlineEnabled(true)
        assertTrue(bridge.isClusterOnlineProgressEnabled)
    }

    @Test
    fun `radio facade prefers the shared file URI for artwork`() {
        val sharedFile = java.io.File("/data/vendor/nfs/shared/radio_cover_abc.jpg")
        val contentUri = Uri.parse("content://atlas/cover.jpg")

        assertEquals(
            Uri.fromFile(sharedFile),
            ClusterMediaBridge.radioFacadeArtworkUri(sharedFile, contentUri),
        )
        assertEquals(contentUri, ClusterMediaBridge.radioFacadeArtworkUri(null, contentUri))
    }

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
    fun `online opt in controls progress independently of radio setting`() {
        val context = RecordingContext()
        context.getSharedPreferences(ClusterMediaBridge.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val bridge = ClusterMediaBridge(context)
        val snapshot = MediaSnapshot(
            backendConnected = true, audioSource = "ONLINE", ownerPackage = "player",
            mediaId = "track", title = "Title", playbackState = 3,
        )
        bridge.setActiveSource(BridgeAudioSource.ONLINE)
        bridge.updateOnlinePlayback(snapshot, null)
        bridge.setClusterOnlineEnabled(true)
        assertTrue(bridge.isClusterOnlineProgressEnabled)
        bridge.setClusterCoversEnabled(false)
        bridge.updateOnlinePlayback(snapshot, null)
        bridge.updateOnlinePlayback(snapshot.copy(position = 1000L), null)
        bridge.setActiveSource(BridgeAudioSource.RADIO)
        bridge.updateOnlinePlayback(snapshot.copy(title = "Stale"), null)
        bridge.setActiveSource(BridgeAudioSource.ONLINE)
        bridge.setClusterOnlineEnabled(false)
        assertFalse(bridge.isClusterOnlineProgressEnabled)
        bridge.updateOnlinePlayback(snapshot, null)
        bridge.setClusterOnlineEnabled(true)
        bridge.setActiveSource(BridgeAudioSource.UNKNOWN)
        bridge.updateOnlinePlayback(snapshot, null)
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

}
