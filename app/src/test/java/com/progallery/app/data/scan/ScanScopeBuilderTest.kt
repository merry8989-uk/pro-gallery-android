package com.progallery.app.data.scan

import com.progallery.app.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan scope.
 *
 * The value of this screen is that it tells the truth before a scan rather than after it, so these
 * tests are about the two ways it could lie: promising to look at something it cannot reach, and
 * staying silent about a reason it will find nothing.
 */
class ScanScopeBuilderTest {

    private val base = AppSettings.Defaults

    private fun scope(
        settings: AppSettings = base,
        folders: List<String> = emptyList(),
        allFiles: Boolean = true,
        androidMedia: Boolean = true,
    ) = ScanScopeBuilder.build(settings, folders, allFiles, androidMedia)

    private fun lines(scope: ScanScope) = scope.methods.associateBy { it.label }

    // ---- what will be scanned ---------------------------------------------------------------

    @Test
    fun eachMethodReportsItsOwnSwitchAndNothingElse() {
        val s = scope(base.copy(quickScanEnabled = true, deepScanEnabled = false, safScanEnabled = false))
        val byLabel = lines(s)
        assertTrue(byLabel.getValue(ScanScope.QUICK).included)
        assertFalse(byLabel.getValue(ScanScope.DEEP).included)
        assertFalse(byLabel.getValue(ScanScope.SAF).included)
    }

    @Test
    fun theKindsAreTheOnesTheCategoriesAllow() {
        val s = scope(base.copy(scanVideos = false, scanImages = true, scanAudio = false))
        assertEquals(listOf("image"), s.kinds)
    }

    @Test
    fun theLimitsQuoteTheRealNumbers() {
        val s = scope(base.copy(maxScanResults = 123, maxRecursionDepth = 7, signatureReadBytes = 4096))
        val text = s.limits.joinToString(" | ") { it.label + "=" + it.detail }
        assertTrue(text.contains("123"))
        assertTrue(text.contains("7"))
        assertTrue(text.contains("4096"))
    }

    @Test
    fun theSizeRangeComesFromTheSettingThatEnforcesIt() {
        assertEquals(base.sizeRangeLabel, scope().sizeRange)
    }

    // ---- where start goes -------------------------------------------------------------------

    @Test
    fun nothingSwitchedOnMeansNowhereToGo() {
        val s = scope(base.copy(quickScanEnabled = false, deepScanEnabled = false, safScanEnabled = false))
        assertFalse(s.canStart)
        assertEquals(ScanTarget.NONE, s.target)
    }

    @Test
    fun quickScanWinsWhenBothAreOnBecauseItIsTheCheapOne() {
        val both = scope(base.copy(quickScanEnabled = true, deepScanEnabled = true))
        assertEquals(ScanTarget.RECOVERY, both.target)
    }

    @Test
    fun deepScanAloneGoesToTheDeepScan() {
        val s = scope(base.copy(quickScanEnabled = false, deepScanEnabled = true, safScanEnabled = true))
        assertEquals(ScanTarget.DEEP_SCAN, s.target)
    }

    @Test
    fun everyCategoryOffMeansNothingToScanEvenWithAMethodOn() {
        val s = scope(
            base.copy(
                quickScanEnabled = true, deepScanEnabled = true,
                scanImages = false, scanVideos = false, scanAudio = false,
                scanDocuments = false, scanArchives = false, scanOtherFiles = false,
            ),
        )
        assertFalse("a method with no kinds to look at is not a scan", s.canStart)
        assertEquals(ScanTarget.NONE, s.target)
    }

    // ---- the honest part: what it will not do ----------------------------------------------

    @Test
    fun theFolderLineSaysHowManyAndWhich() {
        val s = scope(folders = listOf("Download", "DCIM"))
        val line = lines(s).getValue(ScanScope.SAF)
        assertTrue(line.detail.contains("2 folder"))
        assertTrue(line.detail.contains("Download"))
        assertTrue(line.detail.contains("DCIM"))
    }

    @Test
    fun folderScanningWithNoFoldersSaysSoInBothPlaces() {
        val s = scope(base.copy(safScanEnabled = true), folders = emptyList())
        // once on the line itself...
        assertTrue(lines(s).getValue(ScanScope.SAF).detail.contains("No folder has been granted"))
        // ...and once as a note, because a line can be skimmed and a note cannot
        assertTrue(s.notes.any { it.contains("no folder has been granted") })
    }

    @Test
    fun anUnreachableAndroidMediaIsSaidRatherThanListed() {
        val reachable = scope(androidMedia = true)
        val not = scope(androidMedia = false)
        assertTrue(
            "a scope must not stay silent about a folder this device will not open",
            not.notes.any { it.contains("Android/media") },
        )
        assertFalse(reachable.notes.any { it.contains("Android/media") })
    }

    @Test
    fun withoutAllFilesAccessItSaysWhatIsOutOfReach() {
        val s = scope(folders = listOf("Download"), allFiles = false)
        assertTrue(s.notes.any { it.contains("All files access") })
    }

    @Test
    fun withNoFoldersTheAllFilesNoteIsNotMadeBecauseThereIsNothingToLimit() {
        val s = scope(folders = emptyList(), allFiles = false)
        assertFalse(
            "a warning about granted folders when none are granted is noise",
            s.notes.any { it.contains("only the folders you granted are readable") },
        )
    }

    @Test
    fun signatureDetectionOffIsSaidBecauseItChangesWhatAFileIsJudgedToBe() {
        val s = scope(base.copy(signatureDetectionEnabled = false))
        assertTrue(s.notes.any { it.contains("extension") })
    }

    @Test
    fun theAppMediaIndexBeingOffIsSaid() {
        val s = scope(base.copy(includeAppMediaInGallery = false))
        assertTrue(s.notes.any { it.contains("messaging apps") })
    }

    @Test
    fun aCleanConfigurationProducesNoNotesAtAll() {
        // Every warning off: the scope should be quiet rather than manufacture something to say.
        val s = scope(
            base.copy(
                quickScanEnabled = true, deepScanEnabled = true, safScanEnabled = true,
                includeAppMediaInGallery = true, signatureDetectionEnabled = true,
            ),
            folders = listOf("Download"),
            allFiles = true,
            androidMedia = true,
        )
        assertEquals(emptyList<String>(), s.notes)
    }

    @Test
    fun nothingSwitchedOnIsExplainedTwiceForTwoDifferentReasons() {
        val s = scope(base.copy(quickScanEnabled = false, deepScanEnabled = false, safScanEnabled = false))
        assertTrue(s.notes.any { it.contains("No scan method") })
    }
}
