package com.progallery.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An update check is only useful if it is right about which version is newer. Both ways of
 * being wrong are bad: never offering an update, and offering one that is older.
 */
class VersionNameTest {

    @Test
    fun aLeadingVIsIgnored() {
        assertEquals(0, VersionName.compare("v1.16.0", "1.16.0"))
        assertEquals(0, VersionName.compare("V2.0", "2.0"))
    }

    @Test
    fun aNewerVersionComparesHigher() {
        assertTrue(VersionName.compare("1.16.0", "1.15.0") > 0)
        assertTrue(VersionName.compare("1.15.0", "1.16.0") < 0)
        assertTrue(VersionName.compare("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun theThirdSegmentIsNotIgnored() {
        // The bug this guards against: comparing only major.minor, so a patch release never
        // looks like an update.
        assertTrue(VersionName.isNewer("1.15.1", "1.15.0"))
        assertFalse(VersionName.isNewer("1.15.0", "1.15.1"))
    }

    @Test
    fun aMissingSegmentCountsAsZero() {
        assertEquals(0, VersionName.compare("1.16", "1.16.0"))
        assertEquals(0, VersionName.compare("1.16.0", "1.16"))
        assertTrue(VersionName.compare("1.16.1", "1.16") > 0)
    }

    @Test
    fun aSuffixIsIgnoredForOrdering() {
        // Release tags are written by hand; "1.16.0-beta" is still 1.16.0.
        assertEquals(0, VersionName.compare("1.16.0-beta", "1.16.0"))
        assertEquals(0, VersionName.compare("1.16.0+build7", "1.16.0"))
        assertTrue(VersionName.compare("1.16.0", "1.15.0-beta") > 0)
    }

    @Test
    fun tenIsGreaterThanNine() {
        // A string comparison would get this wrong, which is why the segments are numbers.
        assertTrue(VersionName.isNewer("1.10.0", "1.9.0"))
        assertFalse(VersionName.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun anEqualVersionIsNotAnUpdate() {
        assertFalse(VersionName.isNewer("1.15.0", "1.15.0"))
        assertFalse(VersionName.isNewer("v1.15.0", "1.15.0"))
    }

    @Test
    fun somethingThatIsNotAVersionIsNotAnUpdate() {
        // Better to say nothing than to offer a "newer" version that means nothing.
        listOf("", "   ", "latest", "main", "nightly").forEach { notAVersion ->
            assertFalse("$notAVersion must not be treated as an update", VersionName.isNewer(notAVersion, "1.15.0"))
            assertFalse(VersionName.isValid(notAVersion))
        }
    }

    @Test
    fun aVersionIsRecognisedByHavingDigits() {
        assertTrue(VersionName.isValid("1.15.0"))
        assertTrue(VersionName.isValid("v2"))
        assertTrue(VersionName.isValid("1.0-rc1"))
        // The suffix is cut off, so this is release 1.0 — which is what "rc1" is a
        // candidate of.
        assertEquals(listOf(1, 0), VersionName.segments("1.0-rc1"))
        assertEquals(listOf(1, 16, 0), VersionName.segments("v1.16.0-beta"))
    }

    @Test
    fun anOlderVersionIsNeverOfferedAsAnUpdate() {
        // The worst outcome: telling the user to "upgrade" backwards.
        assertFalse(VersionName.isNewer("1.14.0", "1.15.0"))
        assertFalse(VersionName.isNewer("0.9.9", "1.0.0"))
    }
}
