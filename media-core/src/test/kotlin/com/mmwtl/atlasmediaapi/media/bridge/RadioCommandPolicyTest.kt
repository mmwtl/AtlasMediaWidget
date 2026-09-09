package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioCommandPolicyTest {
    @Test
    fun `head unit status 1001 means radio is playing`() {
        assertTrue(isRadioPlaying(0x1001))
        assertFalse(isRadioPlaying(0x1000))
        assertFalse(isRadioPlaying(0))
    }

    @Test
    fun `radio toggle pauses while playing and plays otherwise`() {
        assertEquals(
            RadioPlaybackAction.PAUSE,
            radioPlaybackAction(MediaCommand.TOGGLE, 0x1001),
        )
        assertEquals(
            RadioPlaybackAction.PLAY,
            radioPlaybackAction(MediaCommand.TOGGLE, 0x1000),
        )
    }

    @Test
    fun `explicit radio play and pause keep protocol semantics`() {
        assertEquals(
            RadioPlaybackAction.PLAY,
            radioPlaybackAction(MediaCommand.PLAY, 0x1001),
        )
        assertEquals(
            RadioPlaybackAction.PAUSE,
            radioPlaybackAction(MediaCommand.PAUSE, 0x1000),
        )
    }

    @Test
    fun `steering radio button ignores one os function and always toggles`() {
        assertEquals(MediaCommand.TOGGLE, oneOsPlayPauseCommand(0x1000, forceToggle = true))
        assertEquals(MediaCommand.TOGGLE, oneOsPlayPauseCommand(0x1001, forceToggle = true))
    }

    @Test
    fun `non radio native source keeps explicit one os function`() {
        assertEquals(MediaCommand.PLAY, oneOsPlayPauseCommand(0x1000, forceToggle = false))
        assertEquals(MediaCommand.PAUSE, oneOsPlayPauseCommand(0x1001, forceToggle = false))
        assertEquals(MediaCommand.TOGGLE, oneOsPlayPauseCommand(0, forceToggle = false))
    }
}
