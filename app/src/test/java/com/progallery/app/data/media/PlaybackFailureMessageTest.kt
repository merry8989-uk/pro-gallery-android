package com.progallery.app.data.media

import com.progallery.app.data.capability.Capability
import com.progallery.app.data.capability.CapabilityMatrix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player must not contradict the capability screen.
 *
 * It did. Part D handed MPEG program streams to the device's own parser on Android 11 and up, and the
 * capability screen started saying "opens, with a caveat" — while the player's message was a
 * **hardcoded** list that still said those containers "have no support on Android at all". Two parts
 * of the same app, disagreeing about the same file, on the one subject the capability matrix exists
 * to settle.
 *
 * This is the invariant that stops it happening again, and it is checked against the matrix rather
 * than against a list kept here.
 */
class PlaybackFailureMessageTest {

    private val containerError = androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED

    @Test
    fun aFormatTheMatrixRefusesGetsTheMatrixsOwnReason() {
        // `.wmv` really is unreadable on Android, and the matrix says so in words.
        val explanation = PlaybackFailure.explain(containerError, extension = "wmv")
        assertTrue(
            "a refused format must give the matrix's reason",
            explanation.detail.contains("Windows Media") || explanation.detail.contains("decoder"),
        )
    }

    @Test
    fun aFormatTheMatrixAcceptsNeverGetsToldItIsUnsupported() {
        // The regression this test exists for: `.mpg` is a caveat now, not a refusal.
        val accepted = CapabilityMatrix.forExtension("mpg")
        assertTrue("the premise: .mpg is not refused", accepted.verdict != Capability.NEEDS_ANOTHER_APP)

        val explanation = PlaybackFailure.explain(containerError, extension = "mpg")
        assertFalse(
            "the player must not call a format unsupported when the matrix says it opens here",
            explanation.detail.contains("no support on Android at all"),
        )
        assertTrue(
            "and it should point at the file rather than the format",
            explanation.detail.contains("usually works here"),
        )
    }

    @Test
    fun everyFormatTheMatrixAcceptsIsSafeFromThatSentence() {
        // Not a hand-picked list: every extension the app knows, checked against the rule.
        val offenders = CapabilityMatrix.all()
            .filter { it.verdict != Capability.NEEDS_ANOTHER_APP }
            .filter { PlaybackFailure.explain(containerError, extension = it.extension).detail.contains("no support on Android at all") }
            .map { it.extension }
        assertTrue("these formats are accepted by the matrix but called unsupported: $offenders", offenders.isEmpty())
    }

    @Test
    fun anUnknownExtensionStillSaysSomethingUseful() {
        val explanation = PlaybackFailure.explain(containerError, extension = "")
        assertTrue(explanation.detail.isNotBlank())
        assertTrue(explanation.reason.isNotBlank())
    }
}
