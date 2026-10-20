package com.progallery.app.data.settings

import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertFalse("experimental methods are off", d.experimentalMethodsEnabled)
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
        val s = AppSettings.Defaults.copy(experimentalMethodsEnabled = false).sanitized()
        assertFalse(s.experimentalMethodsEnabled)
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
}
