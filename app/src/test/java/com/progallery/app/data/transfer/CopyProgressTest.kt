package com.progallery.app.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far a copy has got, checked as arithmetic.
 *
 * The case that matters most is a provider that does not say how big its document is: `0` from a
 * content provider means "I do not know", not "this file is empty" — and a bar drawn from it would
 * sit at nothing while the copy ran, which is worse than no bar.
 */
class CopyProgressTest {

    @Test
    fun aKnownTotalGivesAFraction() {
        assertEquals(0.5f, CopyProgress.of(copied = 50, total = 100).let { (it as CopyProgress.Known).fraction })
        assertEquals(0.25f, CopyProgress.of(copied = 25, total = 100).let { (it as CopyProgress.Known).fraction })
    }

    @Test
    fun theFractionIsClampedRatherThanGoingPastTheEnd() {
        // A provider may report a size that turns out to be smaller than the file. A bar that went
        // past 1 would be drawn wrongly, so it stops at the end.
        assertEquals(1f, (CopyProgress.of(copied = 150, total = 100) as CopyProgress.Known).fraction)
        assertEquals(0f, (CopyProgress.of(copied = 0, total = 100) as CopyProgress.Known).fraction)
        assertEquals(1f, (CopyProgress.of(copied = 100, total = 100) as CopyProgress.Known).fraction)
    }

    @Test
    fun aTotalOfZeroMeansNoTotalRatherThanAnEmptyFile() {
        // The distinction the whole type exists for.
        assertTrue(CopyProgress.of(copied = 10, total = 0) is CopyProgress.UnknownTotal)
        assertTrue(CopyProgress.of(copied = 10, total = -5) is CopyProgress.UnknownTotal)
    }

    @Test
    fun anUnknownTotalStillSaysHowManyBytesHaveGone() {
        val progress = CopyProgress.of(copied = 4096, total = 0) as CopyProgress.UnknownTotal
        assertEquals(4096L, progress.copied)
    }

    @Test
    fun startingIsItsOwnStateRatherThanZeroOfSomething() {
        assertEquals(0L, CopyProgress.Starting.copied)
        assertTrue(CopyProgress.Starting !is CopyProgress.Known)
    }

    @Test
    fun aRealTotalOfOneIsNotTreatedAsNoTotal() {
        // One byte is a real total, and the guard must not swallow it.
        assertTrue(CopyProgress.of(copied = 0, total = 1) is CopyProgress.Known)
    }
}
