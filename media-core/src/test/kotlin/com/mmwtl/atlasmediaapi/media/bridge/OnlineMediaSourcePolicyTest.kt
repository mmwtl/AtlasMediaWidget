package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMediaSourcePolicyTest {
    @Test
    fun `meaningful session blocks competing online OneOS callbacks`() {
        val policy = OnlineMediaSourcePolicy()

        assertTrue(policy.onSession("com.example.player", meaningful = true))

        assertFalse(policy.acceptOneOs(BridgeAudioSource.ONLINE))
    }

    @Test
    fun `empty controller cannot displace meaningful session`() {
        val policy = OnlineMediaSourcePolicy()
        policy.onSession("com.example.player", meaningful = true)

        assertFalse(policy.onSession("com.example.empty", meaningful = false))
        assertFalse(policy.acceptOneOs(BridgeAudioSource.ONLINE))
    }

    @Test
    fun `empty online OneOS metadata is ignored without a session`() {
        val policy = OnlineMediaSourcePolicy()

        assertFalse(policy.acceptOneOs(BridgeAudioSource.ONLINE, meaningful = false))
    }

    @Test
    fun `OneOS resumes after session disappears`() {
        val policy = OnlineMediaSourcePolicy()
        policy.onSession("com.example.player", meaningful = true)

        policy.onSessionGone()

        assertTrue(policy.acceptOneOs(BridgeAudioSource.ONLINE))
    }

    @Test
    fun `last native source is restored when online session disappears`() {
        val policy = OnlineMediaSourcePolicy()
        policy.onAudioSource(BridgeAudioSource.RADIO)
        policy.onSession("com.example.player", meaningful = true)

        assertEquals(
            BridgeAudioSource.RADIO,
            policy.onSessionGone(BridgeAudioSource.ONLINE),
        )
    }

    @Test
    fun `online source without a session does not trigger native fallback`() {
        val policy = OnlineMediaSourcePolicy()
        policy.onAudioSource(BridgeAudioSource.RADIO)

        assertEquals(null, policy.onSessionGone(BridgeAudioSource.ONLINE))
    }

    @Test
    fun `session priority does not suppress native sources`() {
        val policy = OnlineMediaSourcePolicy()
        policy.onSession("com.example.player", meaningful = true)

        assertTrue(policy.acceptOneOs(BridgeAudioSource.BT))
        assertTrue(policy.acceptOneOs(BridgeAudioSource.USB))
    }
}
