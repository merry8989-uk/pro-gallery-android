package com.progallery.app.data.playback

/**
 * Whether to resume, and where.
 *
 * A position is remembered so a long video can be picked up again — and the reason this is a
 * separate, tested thing rather than a line in the player is that the naive version is annoying:
 *
 * - **Resuming into the credits.** A video watched to within a few seconds of the end is
 *   finished. Restoring it means pressing play to watch the last of the titles and then having to
 *   find the next one by hand. So a position near the end starts over.
 * - **Resuming eight seconds in.** A video opened and closed once is not "in progress". Restoring
 *   a position that is barely past the start makes every file look half-watched, and the resume
 *   is more disruptive than simply starting.
 *
 * Both rules are stated as numbers here, once, so the player and anything else that ever asks the
 * question cannot disagree about what "near the end" means.
 */
object PlaybackPosition {

    /** Below this, a position is not worth restoring — the video has barely begun. */
    const val MIN_RESTORE_MS = 10_000L

    /** Within this much of the end, the video counts as finished. */
    const val END_MARGIN_MS = 15_000L

    /** And as a proportion, for a long video where fifteen seconds is nothing. */
    const val END_FRACTION = 0.95

    /**
     * Whether this position is worth resuming from.
     *
     * An unknown duration (a stream that has not reported one yet) trusts the position: the
     * absolute rules still apply, and refusing to resume because the length is not known yet
     * would mean never resuming anything played from a network.
     */
    fun shouldRestore(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs <= 0) return false
        if (positionMs < MIN_RESTORE_MS) return false
        if (durationMs <= 0) return true
        if (positionMs >= durationMs - END_MARGIN_MS) return false
        if (positionMs.toDouble() / durationMs.toDouble() >= END_FRACTION) return false
        return true
    }

    /**
     * Whether the video has been watched to the end.
     *
     * This is a different question from [shouldRestore], and the difference is what stops a real
     * annoyance: a position is **forgotten** when the video finishes, so next time it starts from
     * the beginning — but a position is **not overwritten** when the video has barely started, so
     * opening a file you are twenty minutes into and closing it after three seconds does not
     * throw those twenty minutes away.
     */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        if (positionMs >= durationMs - END_MARGIN_MS) return true
        return positionMs.toDouble() / durationMs.toDouble() >= END_FRACTION
    }

    /** Where to start, which is the remembered position or the beginning. */
    fun resumeAt(positionMs: Long, durationMs: Long): Long =
        if (shouldRestore(positionMs, durationMs)) positionMs else 0L

    /**
     * Whether a position is worth *saving*.
     *
     * Saving every position would fill the table with eight-second entries that will never be
     * used, so the same threshold applies on the way in.
     */
    fun shouldSave(positionMs: Long, durationMs: Long): Boolean = shouldRestore(positionMs, durationMs)
}
