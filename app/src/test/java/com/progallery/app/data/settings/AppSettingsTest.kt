package com.progallery.app.data.settings

import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The defaults must be safe, and every toggle must have exactly one meaning.
 */
class AppSettingsTest {

    @Test
    fun defaults_areSafe() {
        val d = AppSettings.Defaults
        // Dangerous / experimental features are off out of the box.
        assertFalse("advanced mode starts hidden", d.advancedMode)
        assertFalse("root status is opt-in", d.showRootStatus)
        assertFalse("hidden files excluded by default", d.includeHiddenFiles)
        assertFalse("background pause is opt-in", d.pauseWhenBackgrounded)
        // Ordinary discovery is on.
        assertTrue(d.quickScanEnabled)
        assertTrue(d.deepScanEnabled)
        assertTrue(d.generateThumbnails)
    }

    @Test
    fun enabledKinds_reflectsCategoryToggles() {
        val all = AppSettings.Defaults
        assertEquals(
            setOf(
                MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO,
                MediaType.DOCUMENT, MediaType.ARCHIVE, MediaType.OTHER,
            ),
            all.enabledKinds,
        )
        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), all.copy(
            scanAudio = false, scanDocuments = false, scanArchives = false, scanOtherFiles = false,
        ).enabledKinds)
        assertTrue(all.copy(
            scanImages = false, scanVideos = false, scanAudio = false,
            scanDocuments = false, scanArchives = false, scanOtherFiles = false,
        ).enabledKinds.isEmpty())
    }

    @Test
    fun videoToggle_gatesVideoDiscovery() {
        assertTrue(AppSettings.Defaults.videoScanningEnabled)
        assertFalse(AppSettings.Defaults.copy(scanVideos = false).videoScanningEnabled)
    }

    @Test
    fun scanCannotStart_withoutAMethodOrACategory() {
        assertTrue(AppSettings.Defaults.scanCanStart)

        val noMethods = AppSettings.Defaults.copy(
            quickScanEnabled = false, deepScanEnabled = false, safScanEnabled = false,
        )
        assertFalse(noMethods.anyScanModeEnabled)
        assertFalse(noMethods.scanCanStart)

        val noCategories = AppSettings.Defaults.copy(
            scanImages = false, scanVideos = false, scanAudio = false,
            scanDocuments = false, scanArchives = false, scanOtherFiles = false,
        )
        assertFalse(noCategories.anyCategoryEnabled)
        assertFalse(noCategories.scanCanStart)

        // Disabling Deep Scan must not disable Quick Scan.
        val quickOnly = AppSettings.Defaults.copy(deepScanEnabled = false, safScanEnabled = false)
        assertTrue(quickOnly.scanCanStart)
    }

    @Test
    fun sanitized_clampsNumericValuesIntoRange() {
        val wild = AppSettings.Defaults.copy(
            maxRecursionDepth = 999,
            maxScanResults = -5,
            signatureReadBytes = 10_000_000,
            maxConcurrentTasks = 0,
        ).sanitized()

        assertEquals(32, wild.maxRecursionDepth)
        assertEquals(50, wild.maxScanResults)
        assertEquals(65_536, wild.signatureReadBytes)
        assertEquals(1, wild.maxConcurrentTasks)

        val tiny = AppSettings.Defaults.copy(
            maxRecursionDepth = 0, maxScanResults = 0,
            signatureReadBytes = 0, maxConcurrentTasks = 0,
        ).sanitized()
        assertEquals(1, tiny.maxRecursionDepth)
        assertEquals(50, tiny.maxScanResults)
        assertEquals(64, tiny.signatureReadBytes)
        assertEquals(1, tiny.maxConcurrentTasks)
    }

    @Test
    fun sanitized_doesNotEnableDangerousFeatures() {
        val s = AppSettings.Defaults.sanitized()
        assertFalse("background pause is opt-in", s.pauseWhenBackgrounded)
    }

    @Test
    fun sizeRange_defaultsToOneByteWithNoUpperLimit() {
        val d = AppSettings.Defaults
        assertEquals(1L, d.minFileSizeBytes)
        assertTrue("no maximum by default", d.isMaxSizeUnlimited)
        assertTrue(d.sizeRangeLabel.startsWith("1 B"))
        assertTrue(d.sizeRangeLabel.endsWith("unlimited"))
    }

    @Test
    fun acceptsSize_coversOneByteToInfinite() {
        val d = AppSettings.Defaults
        // An unreadable size is never treated as too small, or real files would
        // be dropped because SAF reports 0 for "unknown".
        assertTrue("unknown size", d.acceptsSize(0L))
        assertTrue("unknown size", d.acceptsSize(-1L))
        // Everything from a single byte upward is in range.
        assertTrue("1 byte", d.acceptsSize(1L))
        assertTrue("1 KB", d.acceptsSize(1_024L))
        assertTrue("100 MB", d.acceptsSize(104_857_600L))
        assertTrue("1 TB", d.acceptsSize(1_099_511_627_776L))
        assertTrue("Long.MAX_VALUE", d.acceptsSize(Long.MAX_VALUE))
    }

    @Test
    fun acceptsSize_honoursANarrowedRange() {
        val raised = AppSettings.Defaults.copy(minFileSizeBytes = 1_000L)
        assertFalse(raised.acceptsSize(999L))
        assertTrue(raised.acceptsSize(1_000L))

        val capped = AppSettings.Defaults.copy(maxFileSizeBytes = 1_000L)
        assertFalse(capped.isMaxSizeUnlimited)
        assertTrue(capped.acceptsSize(1_000L))
        assertFalse(capped.acceptsSize(1_001L))
    }

    @Test
    fun sanitized_repairsAnImpossibleSizeRange() {
        val zeroed = AppSettings.Defaults.copy(minFileSizeBytes = 0L, maxFileSizeBytes = 0L).sanitized()
        assertEquals(1L, zeroed.minFileSizeBytes)
        assertTrue("zero max becomes unlimited", zeroed.isMaxSizeUnlimited)

        val inverted = AppSettings.Defaults
            .copy(minFileSizeBytes = 10_000L, maxFileSizeBytes = 100L)
            .sanitized()
        assertTrue(
            "max is raised to the minimum",
            inverted.maxFileSizeBytes >= inverted.minFileSizeBytes,
        )
    }

    @Test
    fun summary_describesWhatWouldBeScanned() {
        assertTrue(AppSettings.Defaults.summary().contains("Quick"))
        assertTrue(AppSettings.Defaults.summary().contains("image"))
        assertEquals(
            "No scan method is enabled",
            AppSettings.Defaults.copy(
                quickScanEnabled = false, deepScanEnabled = false, safScanEnabled = false,
            ).summary(),
        )
        assertEquals(
            "No file category is enabled",
            AppSettings.Defaults.copy(
                scanImages = false, scanVideos = false, scanAudio = false,
                scanDocuments = false, scanArchives = false, scanOtherFiles = false,
            ).summary(),
        )
    }

    // ---- Per-section reset -------------------------------------------------

    @Test
    fun resetSection_restoresOnlyItsOwnSection() {
        val changed = AppSettings.Defaults.copy(
            quickScanEnabled = false,   // Scanning
            scanImages = false,         // File types
            generateThumbnails = false, // Thumbnails & result cards
            themeMode = ThemeMode.DARK, // Appearance
            maxRecursionDepth = 2,      // Scan limits
            maxConcurrentTasks = 1,     // Performance
            showRootStatus = true,      // Diagnostics
            advancedMode = true,
        )

        val after = changed.resetSection("Scanning")
        assertEquals(AppSettings.Defaults.quickScanEnabled, after.quickScanEnabled)
        assertFalse("another section must be left alone", after.scanImages)
        assertEquals(ThemeMode.DARK, after.themeMode)
        assertTrue("advancedMode must survive: it is the switch that reveals the section", after.advancedMode)
    }

    @Test
    fun resetSection_handlesEverySectionTheScreenShows() {
        // If a section name drifts from the screen's, its Reset would silently do
        // nothing — so every name the screen uses is exercised here.
        val sections = listOf(
            "Scanning", "File types", "Thumbnails & result cards", "Appearance",
            "Detection", "Scan limits", "Performance", "Diagnostics & experimental",
        )
        val changed = AppSettings.Defaults.copy(
            quickScanEnabled = false,
            scanImages = false,
            generateThumbnails = false,
            themeMode = ThemeMode.DARK,
            includeHiddenFiles = true,
            maxRecursionDepth = 2,
            maxConcurrentTasks = 1,
            showRootStatus = true,
        )
        sections.forEach { section ->
            assertNotEquals(
                "\"$section\" reset changed nothing",
                changed,
                changed.resetSection(section),
            )
        }
    }

    @Test
    fun theAboutSectionHasNothingToReset() {
        // It holds the version and the contact handle, which are not settings — so its
        // Reset does nothing, deliberately, rather than pretending to restore something.
        val settings = AppSettings.Defaults.copy(quickScanEnabled = false)
        assertEquals(settings, settings.resetSection("About"))
    }

    @Test
    fun anUnknownSectionChangesNothing() {
        val settings = AppSettings.Defaults.copy(quickScanEnabled = false)
        assertEquals(settings, settings.resetSection("Not A Section"))
    }
}
