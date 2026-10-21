package com.progallery.app.data.eventlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The code every install gets.
 *
 * Two properties matter beyond the format: it must not be mistakable when read aloud, and it
 * must be random rather than derived from anything about the person or the phone.
 */
class UserCodeTest {

    @Test
    fun aCodeIsBuiltFromTheRandomBytes() {
        // Deterministic from the input, so the format is pinned.
        assertEquals("PG-012-345", UserCode.from(byteArrayOf(0.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 4.toByte(), 5.toByte())))
        assertEquals("PG-89A-BCD", UserCode.from(byteArrayOf(200.toByte(), 201.toByte(), 202.toByte(), 203.toByte(), 204.toByte(), 205.toByte())))
    }

    @Test
    fun aByteAboveTheSignedRangeStillIndexesTheAlphabet() {
        // A Kotlin Byte is signed: 200 arrives as -56. Without masking it would index backwards
        // and produce a character from the wrong end of the alphabet.
        assertEquals("PG-800-000", UserCode.from(byteArrayOf(200.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())))
    }

    @Test
    fun aCodeHasTheDocumentedShape() {
        val code = UserCode.generate()
        assertEquals("PG-XXX-XXX is ten characters", 10, code.length)
        assertTrue(code.startsWith("PG-"))
        assertEquals("two groups after the prefix", 3, code.split("-").size)
    }

    @Test
    fun theAlphabetLeavesOutTheCharactersPeopleConfuse() {
        // No I, L, O or U, so a code read aloud or typed from a screenshot cannot become a
        // different one. Generated many times, because one sample proves nothing.
        val forbidden = setOf('I', 'L', 'O', 'U')
        repeat(200) {
            val code = UserCode.generate()
            code.filter { it.isLetter() }.forEach { character ->
                assertFalse("$code contains $character", character in forbidden)
            }
        }
    }

    @Test
    fun generatedCodesAreValid() {
        repeat(50) {
            val code = UserCode.generate()
            assertTrue("$code should validate", UserCode.isValid(code))
        }
    }

    @Test
    fun generatedCodesDiffer() {
        // A pseudonym that repeats is not a pseudonym.
        val codes = (1..200).map { UserCode.generate() }.toSet()
        assertTrue("expected mostly distinct codes, got ${codes.size}", codes.size > 190)
    }

    @Test
    fun validationAcceptsLowerCase() {
        // A person copying a code may well lower-case it; rejecting that is a support question
        // nobody needs.
        assertTrue(UserCode.isValid("pg-4f9-k2q"))
        assertTrue(UserCode.isValid("  PG-4F9-K2Q  "))
    }

    @Test
    fun validationRejectsWhatIsNotACode() {
        assertFalse(UserCode.isValid(""))
        assertFalse(UserCode.isValid("PG-4F9"))
        assertFalse(UserCode.isValid("PG-4F9-K2Q-X1"))
        assertFalse(UserCode.isValid("XX-4F9-K2Q"))
        assertFalse(UserCode.isValid("PG-4F9K-2Q"))
        assertFalse(UserCode.isValid("PG-4F9-K2"))
        // I, L, O and U are not in the alphabet.
        assertFalse(UserCode.isValid("PG-ILO-K2Q"))
        assertFalse(UserCode.isValid("PG-4FU-K2Q"))
    }

    @Test
    fun normaliseTrimsAndUpperCases() {
        assertEquals("PG-4F9-K2Q", UserCode.normalise("  pg-4f9-k2q "))
    }
}
