package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerPackageCommandTest {
    private fun command(source: String, playerPackage: String?): Bundle = Bundle().apply {
        putString(MediaBridgeContract.Key.REQUEST_ID, "req")
        putString(MediaBridgeContract.Key.COMMAND, MediaCommand.SET_SOURCE.name)
        putString(MediaBridgeContract.Key.COMMAND_SOURCE, source)
        if (playerPackage != null) {
            putString(MediaBridgeContract.Key.COMMAND_PLAYER_PACKAGE, playerPackage)
        }
    }

    @Test
    fun `online switch carries the player package`() {
        val request = command("ONLINE", "ru.yandex.music").toMediaCommandRequest()
        assertEquals("ru.yandex.music", request?.playerPackage)
        assertEquals(BridgeAudioSource.ONLINE, request?.source)
    }

    @Test
    fun `plain source switch has no player`() {
        assertNull(command("ONLINE", null).toMediaCommandRequest()?.playerPackage)
    }

    @Test
    fun `invalid or non online player requests are rejected`() {
        assertNull(command("RADIO", "ru.yandex.music").toMediaCommandRequest())
        assertNull(command("ONLINE", "").toMediaCommandRequest())
        assertNull(command("ONLINE", "../etc").toMediaCommandRequest())
        assertNull(command("ONLINE", "a." + "b".repeat(200)).toMediaCommandRequest())
    }
}
