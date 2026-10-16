package com.mmwtl.atlasmediaapi.media.bridge

/**
 * Keeps the card on the shown player for a short time after it stopped playing while another player
 * plays. Some players (AIMP) report a pause while they change tracks; without the hold every
 * track change hands the card and the next command to the other playing app.
 */
object ShownPlayerHold {
    const val GRACE_MS = 1_500L

    /**
     * @param selected package [PlayerSessionSelector] chose, or null.
     * @param shown package of the player the card shows, if it still has a session.
     * @param shownStoppedPlayingAt elapsed realtime the shown player stopped playing, or null.
     * @return milliseconds the shown player keeps the card, or 0 to publish [selected].
     */
    fun remainingMs(
        selected: String?,
        shown: String?,
        shownStoppedPlayingAt: Long?,
        now: Long,
        graceMs: Long = GRACE_MS,
    ): Long {
        if (selected == null || shown.isNullOrBlank() || selected == shown) return 0L
        val stoppedAt = shownStoppedPlayingAt ?: return 0L
        return (stoppedAt + graceMs - now).coerceAtLeast(0L)
    }
}
