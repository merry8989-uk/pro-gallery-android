package com.progallery.app.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether to resume, and where.
 *
 * The naive version of this — remember the position, restore the position — is annoying in two
 * specific ways, so both are pinned here at their boundaries rather than described.
 */
class PlaybackPositionTest {

    private val minute = 60_000L

    // ---- the first rule: resuming into the credits ----

    @Test
    fun aVideoWatchedToTheEndStartsOver() {
        val duration = 10 * minute
        assertFalse(PlaybackPosition.shouldRestore(duration - 1_000, duration))
        assertFalse(PlaybackPosition.shouldRestore(duration, duration))
        assertEquals(0L, PlaybackPosition.resumeAt(duration - 1_000, duration))
    }

    @Test
    fun theEndIsJudgedByTimeAndByProportion() {
        // Fifteen seconds from the end of a short video, and five per cent of a long one: either
        // is "finished", and a test for one would have missed the other.
        val short = 2 * minute
        assertFalse("15s from the end of 2 minutes", PlaybackPosition.shouldRestore(short - 10_000, short))

        val long = 200 * minute
        assertFalse("96% of a long video", PlaybackPosition.shouldRestore((long * 0.96).toLong(), long))
    }

    @Test
    fun aVideoJustInsideTheEndMarginStillResumes() {
        val duration = 100 * minute
        // 10 minutes from the end of a 100-minute film: the margin does not apply, and the
        // proportion is 90%.
        assertTrue(PlaybackPosition.shouldRestore(duration - 10 * minute, duration))
    }

    // ---- the second rule: resuming eight seconds in ----

    @Test
    fun aPositionBarelyPastTheStartIsNotWorthRestoring() {
        val duration = 60 * minute
        assertFalse(PlaybackPosition.shouldRestore(0, duration))
        assertFalse(PlaybackPosition.shouldRestore(5_000, duration))
        assertFalse(PlaybackPosition.shouldRestore(PlaybackPosition.MIN_RESTORE_MS - 1, duration))
        assertEquals(0L, PlaybackPosition.resumeAt(5_000, duration))
    }

    @Test
    fun theThresholdItselfRestores() {
        // The boundary, so an off-by-one cannot hide here.
        assertTrue(PlaybackPosition.shouldRestore(PlaybackPosition.MIN_RESTORE_MS, 60 * minute))
    }

    // ---- the ordinary case ----

    @Test
    fun aPositionInTheMiddleIsRestored() {
        val duration = 90 * minute
        assertEquals(37 * minute, PlaybackPosition.resumeAt(37 * minute, duration))
    }

    // ---- an unknown duration ----

    @Test
    fun anUnknownDurationTrustsThePosition() {
        // A stream that has not reported a length yet: the absolute rules still apply, and
        // refusing because the length is unknown would mean never resuming anything networked.
        assertTrue(PlaybackPosition.shouldRestore(5 * minute, 0))
        assertFalse("but a position under the floor is still not worth it", PlaybackPosition.shouldRestore(2_000, 0))
    }

    @Test
    fun aZeroOrNegativePositionIsNeverRestored() {
        assertFalse(PlaybackPosition.shouldRestore(0, 60 * minute))
        assertFalse(PlaybackPosition.shouldRestore(-1_000, 60 * minute))
    }

    // ---- finished, which is a different question ----

    @Test
    fun finishedIsAboutTheEndOnly() {
        val duration = 60 * minute
        assertTrue(PlaybackPosition.isFinished(duration - 1_000, duration))
        assertTrue(PlaybackPosition.isFinished(duration, duration))
        assertFalse(PlaybackPosition.isFinished(30 * minute, duration))
        assertFalse("barely started is not finished", PlaybackPosition.isFinished(3_000, duration))
    }

    @Test
    fun anUnknownDurationIsNeverFinished() {
        // Otherwise a file that finished before reporting its length would be marked watched.
        assertFalse(PlaybackPosition.isFinished(9 * minute, 0))
    }

    @Test
    fun theTwoQuestionsDisagreeExactlyWhereTheyShould() {
        // The case that makes the distinction worth having: three seconds into a video the user
        // is part-way through. Not worth *restoring*, and not *finished* — so the saved position
        // must be left alone rather than overwritten or cleared.
        val duration = 60 * minute
        assertFalse(PlaybackPosition.shouldRestore(3_000, duration))
        assertFalse(PlaybackPosition.isFinished(3_000, duration))
    }
}
