package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState

/** Facts about one player session that decide which player the card shows and commands reach. */
data class PlayerSessionFacts(
    val packageName: String,
    val playing: Boolean,
    val hasContent: Boolean,
    val lastPositionUpdateTime: Long,
)

/**
 * One ordering for the player app the card shows and transport commands reach: a playing player
 * wins, otherwise the shown player keeps the card and the buttons, so a paused or empty session of
 * another app cannot take them over.
 */
object PlayerSessionSelector {
    /**
     * @param displayedPackage package of the player the card currently shows.
     * @param rememberedPackage player that last accepted a command.
     * @param requireContent skip sessions without a title, artist or media ID, except the shown
     *   player, whose empty session still carries its playback state.
     */
    fun <T> select(
        sessions: List<T>,
        facts: (T) -> PlayerSessionFacts,
        displayedPackage: String,
        rememberedPackage: String = "",
        requireContent: Boolean,
    ): T? {
        val described = sessions.map { it to facts(it) }
        val eligible = described.filter { (_, fact) -> !requireContent || fact.hasContent }

        val playing = eligible.filter { (_, fact) -> fact.playing }
        playing.firstOrNull { (_, fact) -> fact.packageName.matches(displayedPackage) }
            ?.let { return it.first }
        playing.firstOrNull { (_, fact) -> fact.packageName.matches(rememberedPackage) }
            ?.let { return it.first }
        playing.firstOrNull()?.let { return it.first }

        described.firstOrNull { (_, fact) -> fact.packageName.matches(displayedPackage) }
            ?.let { return it.first }
        eligible.firstOrNull { (_, fact) -> fact.packageName.matches(rememberedPackage) }
            ?.let { return it.first }
        return eligible.maxByOrNull { (_, fact) -> fact.lastPositionUpdateTime }?.first
    }

    /**
     * Playing, or passing through a state that a playing player reports while it changes or loads
     * a track. Such a player keeps the card: otherwise another playing app takes the card and the
     * next command during every track change.
     */
    fun isPlaying(state: Int?): Boolean = when (state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    private fun String.matches(packageName: String): Boolean =
        packageName.isNotBlank() && this == packageName
}
