package com.mmwtl.atlasmediaapi.media.session

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import com.mmwtl.atlasmediaapi.media.bridge.PlayerSessionSelector
import com.mmwtl.atlasmediaapi.media.bridge.hasPlayerContent

/**
 * The session of a player reached through its media browser before it plays; it may not be in
 * the active session list yet.
 *
 * Until the session publishes a track of its own, [recentTrack] (the item of the player's recent
 * root) stands in for its metadata. A session without a playback state, or without a play action,
 * is reported stopped with play: the play command reaches it through the session's media key.
 */
class PreparedSession(
    val controller: MediaController,
    private val recentTrack: MediaMetadata?,
) {
    @Volatile
    private var sessionPublishedTrack = false

    fun metadata(): MediaMetadata? {
        val own = controller.metadata
        if (own.hasPlayerContent()) {
            sessionPublishedTrack = true
            return own
        }
        return if (sessionPublishedTrack) own else recentTrack ?: own
    }

    fun playbackState(): PlaybackState? = withPlay(controller.playbackState)

    companion object {
        private const val PLAY_ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE

        internal fun withPlay(own: PlaybackState?): PlaybackState? {
            if (own != null && PlayerSessionSelector.isPlaying(own.state)) return own
            if (own != null && own.state != PlaybackState.STATE_NONE &&
                own.actions and PLAY_ACTIONS != 0L
            ) return own
            val builder = own?.let { PlaybackState.Builder(it) } ?: PlaybackState.Builder()
            // STOPPED, not PAUSED: an idle session gets the media key, which resumes a Media3
            // player whose play() ignores an empty playlist.
            val state = own?.state?.takeIf { it != PlaybackState.STATE_NONE }
                ?: PlaybackState.STATE_STOPPED
            return builder
                .setState(
                    state,
                    own?.position ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    0f,
                    own?.lastPositionUpdateTime ?: 0L,
                )
                .setActions((own?.actions ?: 0L) or PLAY_ACTIONS)
                .build()
        }
    }
}
