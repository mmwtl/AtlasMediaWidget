package com.mmwtl.atlasmediaapi.media.session

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PreparedSessionTest {
    private val recent = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "Recent track")
        .build()

    @Test
    fun `recent track stands in until the session publishes its own`() {
        val (session, controller) = controller()
        val prepared = PreparedSession(controller, recent)
        assertEquals("Recent track", prepared.metadata()?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))

        val own = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Own track").build()
        shadowOf(controller).setMetadata(own)
        assertSame(own, prepared.metadata())

        // A player clearing its metadata between tracks does not bring the recent track back.
        val cleared = MediaMetadata.Builder().build()
        shadowOf(controller).setMetadata(cleared)
        assertSame(cleared, prepared.metadata())
        session.release()
    }

    @Test
    fun `session without a playback state offers play while stopped`() {
        val state = PreparedSession.withPlay(null)!!
        assertEquals(PlaybackState.STATE_STOPPED, state.state)
        assertTrue(state.actions and PlaybackState.ACTION_PLAY != 0L)
        assertEquals(0f, state.playbackSpeed)
    }

    @Test
    fun `session without a play action keeps its state and actions and gains play`() {
        val own = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 42_000L, 0f, 7L)
            .setActions(PlaybackState.ACTION_SKIP_TO_NEXT)
            .build()
        val state = PreparedSession.withPlay(own)!!
        assertEquals(PlaybackState.STATE_PAUSED, state.state)
        assertEquals(42_000L, state.position)
        assertEquals(7L, state.lastPositionUpdateTime)
        assertTrue(state.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
        assertTrue(state.actions and PlaybackState.ACTION_PLAY_PAUSE != 0L)
    }

    @Test
    fun `playing session and a session that offers play stay as published`() {
        val playing = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0L, 1f).build()
        assertSame(playing, PreparedSession.withPlay(playing))
        val paused = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 0L, 0f)
            .setActions(PlaybackState.ACTION_PLAY)
            .build()
        assertSame(paused, PreparedSession.withPlay(paused))
        val none = PlaybackState.Builder()
            .setState(PlaybackState.STATE_NONE, 0L, 0f)
            .setActions(PlaybackState.ACTION_PLAY)
            .build()
        assertEquals(PlaybackState.STATE_STOPPED, PreparedSession.withPlay(none)!!.state)
    }

    @Test
    fun `recent item description becomes card metadata`() {
        val description = android.media.MediaDescription.Builder()
            .setMediaId("id-1")
            .setTitle("Title")
            .setSubtitle("Artist")
            .setIconUri(android.net.Uri.parse("content://player/art/1"))
            .build()
        val metadata = description.toRecentMetadata()
        assertEquals("id-1", metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
        assertEquals("Title", metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
        assertEquals("Artist", metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
        assertEquals("content://player/art/1", metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))
        assertNull(metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
    }

    private fun controller(): Pair<MediaSession, MediaController> {
        val context = RuntimeEnvironment.getApplication()
        val session = MediaSession(context, "prepared-session-test")
        val controller = MediaController(context, session.sessionToken)
        shadowOf(controller).setPackageName("com.example.resume")
        return session to controller
    }
}
