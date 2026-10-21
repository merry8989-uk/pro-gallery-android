package com.progallery.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug this fixes: every failure used to be blamed on the codec. A missing file was
 * reported as an unsupported codec, which sends the user looking in the wrong place.
 */
class PlaybackFailureTest {

    @Test
    fun aMissingFileIsNotBlamedOnTheCodec() {
        val explanation = PlaybackFailure.explain(2005)
        assertEquals("The file could not be opened", explanation.reason)
        assertFalse(
            "the codec must not be mentioned when the file was never opened",
            explanation.detail.contains("codec", ignoreCase = true),
        )
        assertTrue(
            "the real causes should be named",
            explanation.detail.contains("moved or deleted"),
        )
        assertTrue(
            "and the hidden-folder cause, which is the common one",
            explanation.detail.contains("WhatsApp"),
        )
    }

    @Test
    fun aMissingFileOffersARescanBecauseTheEntryMayBeStale() {
        assertEquals(PlaybackFailure.Action.RESCAN, PlaybackFailure.explain(2005).action)
    }

    @Test
    fun aPermissionFailureSaysItIsAPermissionFailure() {
        val explanation = PlaybackFailure.explain(2006)
        assertTrue(explanation.reason.contains("permission", ignoreCase = true))
        assertFalse(explanation.detail.contains("codec", ignoreCase = true))
    }

    @Test
    fun anUnsupportedCodecIsWhereTheCodecIsActuallyMentioned() {
        val explanation = PlaybackFailure.explain(4005)
        assertTrue("this is the case the old message described", explanation.reason.contains("cannot decode"))
        assertEquals(PlaybackFailure.Action.ANOTHER_APP, explanation.action)
    }

    @Test
    fun anUnsupportedContainerIsDistinguishedFromAnUnsupportedCodec() {
        val container = PlaybackFailure.explain(3003)
        assertTrue(container.reason.contains("kind of file"))
        assertTrue("the formats Android cannot play should be named", container.detail.contains(".wmv"))
    }

    @Test
    fun aDamagedFileSaysSoRatherThanBlamingTheDevice() {
        val explanation = PlaybackFailure.explain(3001)
        assertTrue(explanation.reason.contains("damaged"))
        assertFalse(explanation.detail.contains("this device cannot", ignoreCase = true))
    }

    @Test
    fun aNetworkFailureOffersARetryNotAnotherApp() {
        assertEquals(PlaybackFailure.Action.RETRY, PlaybackFailure.explain(2001).action)
        assertEquals(PlaybackFailure.Action.RETRY, PlaybackFailure.explain(2002).action)
        assertEquals(PlaybackFailure.Action.RETRY, PlaybackFailure.explain(1003).action)
    }

    @Test
    fun aWebPageInsteadOfAFileIsExplained() {
        val explanation = PlaybackFailure.explain(2003)
        assertTrue(explanation.detail.contains("web page"))
    }

    @Test
    fun plainHttpIsExplainedAsAndroidBlockingIt() {
        val explanation = PlaybackFailure.explain(2007)
        assertTrue(explanation.reason.contains("plain http"))
        assertEquals("nothing the user can do inside the app", PlaybackFailure.Action.NONE, explanation.action)
    }

    @Test
    fun everyKnownCodeGivesAReasonAndADetail() {
        val codes = listOf(
            1000, 1003, 2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008,
            3001, 3002, 3003, 3004, 4001, 4002, 4003, 4004, 4005, 5001, 5002,
        )
        codes.forEach { code ->
            val explanation = PlaybackFailure.explain(code)
            assertTrue("code $code needs a reason", explanation.reason.isNotBlank())
            assertTrue("code $code needs a detail", explanation.detail.isNotBlank())
            assertFalse(
                "code $code should end its sentence properly",
                explanation.reason.endsWith("."),
            )
        }
    }

    @Test
    fun anUnknownCodeStillSaysSomethingUseful() {
        val explanation = PlaybackFailure.explain(9999)
        assertTrue(explanation.reason.isNotBlank())
        assertTrue(explanation.detail.contains("another app"))
    }

    @Test
    fun theMessageNamesTheFileWhenItIsKnown() {
        // With a playlist of thirty-two videos open, "something failed" is not enough.
        val message = PlaybackFailure.message(2005, "holiday.mp4")
        assertTrue(message.startsWith("\"holiday.mp4\" — "))
        assertTrue(message.contains("The file could not be opened"))
    }

    @Test
    fun theMessageOmitsTheNameWhenThereIsNone() {
        val message = PlaybackFailure.message(2005, null)
        assertFalse(message.startsWith("\""))
        assertTrue(message.contains("The file could not be opened"))
    }

    @Test
    fun aBlankNameIsNotQuoted() {
        assertFalse(PlaybackFailure.message(2005, "   ").startsWith("\""))
    }
}
