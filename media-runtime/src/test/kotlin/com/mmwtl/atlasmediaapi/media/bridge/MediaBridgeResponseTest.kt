package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MediaBridgeResponseTest {
    @Test
    fun `settings and backup responses carry the protocol envelope`() {
        val received = mutableListOf<Message>()
        val target = Messenger(Handler(Looper.getMainLooper()) { message ->
            received += Message.obtain(message)
            true
        })
        val service = MediaBridgeService()
        val send = MediaBridgeService::class.java.getDeclaredMethod(
            "send", Messenger::class.java, Int::class.javaPrimitiveType, Bundle::class.java
        ).apply { isAccessible = true }
        val responses = listOf(
            MediaBridgeContract.ServerMessage.SETTINGS,
            MediaBridgeContract.ServerMessage.SETTINGS_UPDATED,
            MediaBridgeContract.ServerMessage.MEDIA_BACKUP_EXPORTED,
            MediaBridgeContract.ServerMessage.MEDIA_IMPORT_PREPARED,
            MediaBridgeContract.ServerMessage.MEDIA_IMPORT_COMMITTED,
            MediaBridgeContract.ServerMessage.MEDIA_IMPORT_STATUS,
            MediaBridgeContract.ServerMessage.MEDIA_IMPORT_ABORTED,
            MediaBridgeContract.ServerMessage.DEFAULT_CATALOG_RESTORED
        )
        responses.forEach { what ->
            val payload = Bundle().apply {
                putString(MediaBridgeContract.Key.REQUEST_ID, "request-$what")
                putLong(MediaBridgeContract.Key.SETTINGS_REVISION, 42L)
            }
            assertTrue(send.invoke(service, target, what, payload) as Boolean)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(responses, received.map { it.what })
        received.forEach { message ->
            assertEquals(MediaBridgeContract.PROTOCOL_VERSION,
                message.data.getInt(MediaBridgeContract.Key.PROTOCOL_VERSION, -1))
            assertEquals("request-${message.what}",
                message.data.getString(MediaBridgeContract.Key.REQUEST_ID))
            assertEquals(42L, message.data.getLong(MediaBridgeContract.Key.SETTINGS_REVISION))
        }
    }
}
