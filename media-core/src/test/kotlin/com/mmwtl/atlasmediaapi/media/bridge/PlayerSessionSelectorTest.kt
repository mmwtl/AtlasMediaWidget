package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSessionSelectorTest {
    @Test
    fun `playing player wins over the shown paused player`() {
        val sessions = listOf(
            session("com.example.shown", playing = false, updated = 300L),
            session("com.example.playing", playing = true, updated = 100L),
        )

        assertEquals("com.example.playing", select(sessions, displayed = "com.example.shown"))
    }

    @Test
    fun `shown player wins among several playing players`() {
        val sessions = listOf(
            session("com.example.other", playing = true),
            session("com.example.shown", playing = true),
        )

        assertEquals("com.example.shown", select(sessions, displayed = "com.example.shown"))
    }

    @Test
    fun `newer paused session does not replace the shown paused player`() {
        val sessions = listOf(
            session("com.example.shown", playing = false, updated = 100L),
            session("com.example.newer", playing = false, updated = 900L),
        )

        assertEquals("com.example.shown", select(sessions, displayed = "com.example.shown"))
    }

    @Test
    fun `shown player keeps the card while its session is temporarily empty`() {
        val sessions = listOf(
            session("com.example.shown", playing = false, content = false),
            session("com.example.other", playing = false, updated = 900L),
        )

        assertEquals("com.example.shown", select(sessions, displayed = "com.example.shown"))
    }

    @Test
    fun `empty session cannot take the card after the shown player is gone`() {
        val sessions = listOf(session("com.example.empty", playing = true, content = false))

        assertNull(select(sessions, displayed = "com.example.gone"))
    }

    @Test
    fun `commands may reach an empty session`() {
        val sessions = listOf(session("com.example.empty", playing = false, content = false))

        assertEquals(
            "com.example.empty",
            select(sessions, displayed = "", requireContent = false),
        )
    }

    @Test
    fun `remembered player wins over a newer paused session`() {
        val sessions = listOf(
            session("com.example.newer", playing = false, updated = 900L),
            session("com.example.remembered", playing = false, updated = 100L),
        )

        assertEquals(
            "com.example.remembered",
            select(sessions, displayed = "", remembered = "com.example.remembered"),
        )
    }

    @Test
    fun `without shown or remembered player the most recently updated session wins`() {
        val sessions = listOf(
            session("com.example.older", playing = false, updated = 100L),
            session("com.example.newer", playing = false, updated = 900L),
        )

        assertEquals("com.example.newer", select(sessions, displayed = ""))
    }

    private fun select(
        sessions: List<PlayerSessionFacts>,
        displayed: String,
        remembered: String = "",
        requireContent: Boolean = true,
    ): String? = PlayerSessionSelector.select(
        sessions = sessions,
        facts = { it },
        displayedPackage = displayed,
        rememberedPackage = remembered,
        requireContent = requireContent,
    )?.packageName

    private fun session(
        packageName: String,
        playing: Boolean,
        content: Boolean = true,
        updated: Long = 0L,
    ) = PlayerSessionFacts(packageName, playing, content, updated)
}
