package com.progallery.app.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cancellation must never be turned into an ordinary failure. If it is, a
 * cancelled scan or export reports an error and "clean-up on failure" runs even
 * though the user simply stopped the work.
 */
class CancellationSafeTest {

    @Test
    fun runCatchingCancellable_behavesLikeRunCatchingForOrdinaryOutcomes() {
        assertEquals(5, runCatchingCancellable { 5 }.getOrNull())
        assertTrue(runCatchingCancellable { error("boom") }.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun runCatchingCancellable_propagatesCancellation() {
        runCatchingCancellable { throw CancellationException("stopped") }
    }

    @Test
    fun ignoreUnlessCancelled_reportsOrdinaryOutcomes() {
        assertTrue(ignoreUnlessCancelled { })
        assertFalse(ignoreUnlessCancelled { error("boom") })
    }

    @Test(expected = CancellationException::class)
    fun ignoreUnlessCancelled_propagatesCancellation() {
        ignoreUnlessCancelled { throw CancellationException("stopped") }
    }

    @Test
    fun plainRunCatchingWouldSwallowIt_whichIsWhyTheHelperExists() {
        // Demonstrates the bug being guarded against.
        val swallowed = runCatching { throw CancellationException("stopped") }
        assertTrue("the stdlib helper swallows cancellation", swallowed.isFailure)
    }
}
