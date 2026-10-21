package com.progallery.app.data.permissions

import com.progallery.app.data.eventlog.FixOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Whether an access fix actually fixed anything.
 *
 * This is the half that was missing: the app logged that a user asked for a fix and never logged
 * the outcome, so a report could show 71 attempts and no explanation. Every combination is
 * checked, because the interesting ones are the ones nobody thinks of.
 */
class AccessFixOutcomeTest {

    @Test
    fun reachingGrantedIsSuccess() {
        assertEquals(FixOutcome.SUCCESS, AccessFixOutcome.of(AccessState.DENIED, AccessState.GRANTED))
        assertEquals(FixOutcome.SUCCESS, AccessFixOutcome.of(AccessState.PARTIAL, AccessState.GRANTED))
    }

    @Test
    fun grantedToGrantedIsNotASuccess() {
        // Nothing was asked for and nothing changed: reporting success would make the log flatter
        // than the truth.
        assertEquals(FixOutcome.NO_OP, AccessFixOutcome.of(AccessState.GRANTED, AccessState.GRANTED))
    }

    @Test
    fun anUnchangedStateIsTheNoOpThatExplainsALoop() {
        // The single most useful thing to know about a stuck user: the tap changed nothing.
        assertEquals(FixOutcome.NO_OP, AccessFixOutcome.of(AccessState.DENIED, AccessState.DENIED))
        assertEquals(FixOutcome.NO_OP, AccessFixOutcome.of(AccessState.PARTIAL, AccessState.PARTIAL))
        assertEquals(
            FixOutcome.NO_OP,
            AccessFixOutcome.of(AccessState.NOT_APPLICABLE, AccessState.NOT_APPLICABLE),
        )
    }

    @Test
    fun movingWithoutReachingTheGoalIsAFailedAttempt() {
        assertEquals(FixOutcome.FAILED, AccessFixOutcome.of(AccessState.DENIED, AccessState.PARTIAL))
    }

    @Test
    fun goingBackwardsIsAFailedAttempt() {
        assertEquals(FixOutcome.FAILED, AccessFixOutcome.of(AccessState.GRANTED, AccessState.DENIED))
        assertEquals(FixOutcome.FAILED, AccessFixOutcome.of(AccessState.GRANTED, AccessState.PARTIAL))
    }

    @Test
    fun everyCombinationIsCovered() {
        // Sixteen pairs, and none of them may fall through to an invented answer.
        AccessState.entries.forEach { before ->
            AccessState.entries.forEach { after ->
                val outcome = AccessFixOutcome.of(before, after)
                val expected = when {
                    after == AccessState.GRANTED && before != AccessState.GRANTED -> FixOutcome.SUCCESS
                    after == before -> FixOutcome.NO_OP
                    else -> FixOutcome.FAILED
                }
                assertEquals("$before -> $after", expected, outcome)
            }
        }
    }
}
