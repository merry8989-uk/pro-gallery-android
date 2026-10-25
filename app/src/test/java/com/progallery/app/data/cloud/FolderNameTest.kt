package com.progallery.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a folder name may be, tested rather than discovered.
 *
 * The failure this prevents is quiet: a folder that does not appear, with nothing said. Every rule
 * below is one the platform enforces, plus the two that are a person's own mistake worth naming.
 */
class FolderNameTest {

    private fun ok(raw: String): String {
        val result = FolderName.check(raw)
        assertTrue("\"$raw\" should be allowed, but was refused", result is FolderNameCheck.Ok)
        return (result as FolderNameCheck.Ok).name
    }

    private fun refused(raw: String): String {
        val result = FolderName.check(raw)
        assertTrue("\"$raw\" should be refused, but was allowed", result is FolderNameCheck.Invalid)
        return (result as FolderNameCheck.Invalid).reason
    }

    @Test
    fun anOrdinaryNameIsFine() {
        assertEquals("Holiday photos", ok("Holiday photos"))
        assertEquals("2026-10-25", ok("2026-10-25"))
        assertEquals("नमस्ते", ok("नमस्ते"))
    }

    @Test
    fun spacesAroundANameAreStrippedRatherThanRefused() {
        // A phone keyboard picks these up constantly; refusing them would be pedantic about
        // something invisible.
        assertEquals("Photos", ok("  Photos  "))
        assertEquals("Photos", ok("\tPhotos\n"))
    }

    @Test
    fun nothingAtAllIsRefusedWithSomethingToRead() {
        assertTrue(refused("").isNotBlank())
        assertTrue(refused("   ").isNotBlank())
    }

    @Test
    fun aSlashIsRefusedBecauseThatIsAPathNotAName() {
        assertTrue(refused("a/b").contains("slash"))
        assertTrue(refused("2026/10").contains("slash"))
        assertTrue(refused("a\\b").contains("backslash"))
    }

    @Test
    fun theTwoReservedNamesAreRefused() {
        assertTrue(refused(".").contains("reserved"))
        assertTrue(refused("..").contains("reserved"))
    }

    @Test
    fun controlCharactersAreRefusedBecauseTheyAreInvisibleInAList() {
        assertTrue(refused("a\u0000b").isNotBlank())
        assertTrue(refused("a\nb").isNotBlank())
    }

    @Test
    fun aNameThatIsTooLongIsRefusedByLength() {
        assertEquals(FolderName.MAX_LENGTH, ok("x".repeat(FolderName.MAX_LENGTH)).length)
        assertTrue(refused("x".repeat(FolderName.MAX_LENGTH + 1)).contains("longer"))
    }
}
