package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ConfirmedSourceSwitchTest {
    @Test
    fun `play waits for target after an intermediate source`() = runBlocking {
        val current = AtomicReference(BridgeAudioSource.BT)
        val switch = switch(
            current = current,
            sourceWaitTimeoutMs = 2_000L,
            sourcePollDelaysMs = listOf(50L, 100L, 200L, 300L),
        )
        var playCalls = 0
        val update = launch {
            delay(550L)
            current.set(BridgeAudioSource.USB)
        }

        assertTrue(
            switch.requestAndConfirm(BridgeAudioSource.USB) {
                assertEquals(BridgeAudioSource.BT, current.get())
            },
        )
        assertEquals(BridgeAudioSource.USB, current.get())

        assertTrue(
            switch.playAndConfirm(
                target = BridgeAudioSource.USB,
                sendPlay = {
                    assertEquals(BridgeAudioSource.USB, current.get())
                    playCalls++
                    true
                },
                isPlaying = { playCalls >= 2 },
            ),
        )
        assertEquals(2, playCalls)
        update.cancel()
    }

    @Test
    fun `target timeout does not send play or report success`() = runBlocking {
        val current = AtomicReference(BridgeAudioSource.BT)
        val switch = switch(
            current = current,
            sourceWaitTimeoutMs = 80L,
            sourcePollDelaysMs = listOf(10L),
        )
        var playCalls = 0

        assertFalse(
            switch.requestAndConfirm(BridgeAudioSource.USB) {},
        )
        assertEquals(BridgeAudioSource.BT, current.get())
        assertFalse(
            switch.playAndConfirm(
                target = BridgeAudioSource.USB,
                sendPlay = {
                    playCalls++
                    true
                },
                isPlaying = { false },
            ),
        )
        assertEquals(0, playCalls)
    }

    @Test
    fun `accepted play is retried until playing is confirmed`() = runBlocking {
        val current = AtomicReference(BridgeAudioSource.BT)
        val switch = switch(
            current = current,
            autoplayConfirmDelaysMs = listOf(1L, 1L, 1L),
        )
        var playCalls = 0

        assertTrue(
            switch.playAndConfirm(
                target = BridgeAudioSource.BT,
                sendPlay = {
                    playCalls++
                    true
                },
                isPlaying = { playCalls >= 3 },
            ),
        )
        assertEquals(3, playCalls)
    }

    @Test
    fun `accepted play without playing confirmation fails after bounded retries`() = runBlocking {
        val current = AtomicReference(BridgeAudioSource.USB)
        val switch = switch(
            current = current,
            autoplayConfirmDelaysMs = listOf(1L, 1L),
        )
        var playCalls = 0

        assertFalse(
            switch.playAndConfirm(
                target = BridgeAudioSource.USB,
                sendPlay = {
                    playCalls++
                    true
                },
                isPlaying = { false },
            ),
        )
        assertEquals(2, playCalls)
    }

    @Test
    fun `fallback play command is used only after primary command fails to reach playing`() = runBlocking {
        val current = AtomicReference(BridgeAudioSource.BT)
        val playing = AtomicBoolean(false)
        val switch = switch(
            current = current,
            autoplayConfirmDelaysMs = listOf(1L, 1L),
        )
        var primaryCalls = 0
        var fallbackCalls = 0

        assertTrue(
            switch.playAndConfirmWithFallback(
                target = BridgeAudioSource.BT,
                sendPlay = {
                    primaryCalls++
                    true
                },
                fallbackSendPlay = {
                    fallbackCalls++
                    playing.set(true)
                    true
                },
                isPlaying = { playing.get() },
            ),
        )

        assertEquals(2, primaryCalls)
        assertEquals(1, fallbackCalls)
        assertEquals(BridgeAudioSource.BT, current.get())
    }

    @Test
    fun `source confirmation succeeds without autoplay command`() = runBlocking {
        val current = AtomicReference(BridgeAudioSource.BT)
        val switch = switch(current)
        var playCalls = 0

        assertTrue(switch.requestAndConfirm(BridgeAudioSource.BT) {})

        assertEquals(0, playCalls)
    }

    private fun switch(
        current: AtomicReference<BridgeAudioSource>,
        sourceWaitTimeoutMs: Long = 1_000L,
        sourcePollDelaysMs: List<Long> = listOf(1L),
        autoplayConfirmDelaysMs: List<Long> = listOf(1L, 1L),
    ) = ConfirmedSourceSwitch(
        currentSource = { current.get() },
        sourceWaitTimeoutMs = sourceWaitTimeoutMs,
        sourcePollDelaysMs = sourcePollDelaysMs,
        autoplayConfirmDelaysMs = autoplayConfirmDelaysMs,
    )
}
