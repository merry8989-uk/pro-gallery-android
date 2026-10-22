package com.progallery.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

/**
 * The settings diff.
 *
 * It is built on the export's field list rather than on the data class, so these tests are about
 * the two things that could go wrong: reporting a change that did not happen, and missing one that
 * did.
 */
class SettingsAuditTest {

    private val base = AppSettings.Defaults

    @Test
    fun nothingChangedIsNothingReported() {
        assertTrue(SettingsAudit.changed(base, base).isEmpty())
    }

    @Test
    fun aChangedSwitchIsReportedWithBothValues() {
        val changed = SettingsAudit.changed(base, base.copy(scanVideos = !base.scanVideos))
        assertEquals(1, changed.size)
        val (key, value) = changed.entries.single()
        assertEquals("scanVideos", key)
        assertTrue("should read as old -> new, was: $value", value.contains(" -> "))
        assertTrue(value.startsWith(base.scanVideos.toString()))
        assertTrue(value.endsWith((!base.scanVideos).toString()))
    }

    @Test
    fun severalChangesAreAllReported() {
        val changed = SettingsAudit.changed(
            base,
            base.copy(scanVideos = !base.scanVideos, scanImages = !base.scanImages, usageDataEnabled = false),
        )
        assertEquals(setOf("scanVideos", "scanImages", "usageDataEnabled"), changed.keys)
    }

    @Test
    fun theReportIsBoundedSoOneLineStaysOneLine() {
        // Turning every category off at once is a real thing to do, and it must not write a
        // transcript into a single log line.
        val changed = SettingsAudit.changed(
            base,
            base.copy(
                scanVideos = !base.scanVideos, scanImages = !base.scanImages,
                scanAudio = !base.scanAudio, scanDocuments = !base.scanDocuments,
                scanArchives = !base.scanArchives, scanOtherFiles = !base.scanOtherFiles,
                keepActivityHistory = !base.keepActivityHistory, saveDiagnosticLogs = !base.saveDiagnosticLogs,
            ),
        )
        assertEquals(SettingsAudit.MAX_REPORTED, changed.size)
    }

    @Test
    fun aFieldAddedLaterIsCoveredWithoutTouchingThisFile() {
        // The point of building on the export: every field it names is diffed. If this ever
        // returns nothing for a change, the export has stopped naming that field — which its own
        // test would have caught first.
        // A field whose default is `false`, so turning it on is a change the diff must see.
        val changed = SettingsAudit.changed(base, base.copy(keepActivityHistory = true))
        assertEquals(setOf("keepActivityHistory"), changed.keys)
    }
}

/**
 * The performance mode and the theme, which were both decorative before.
 *
 * A mode is a preset that writes real numbers; the theme is a choice that reaches the theme. Both
 * are pure here, so what they mean is tested rather than assumed.
 */
class PerformanceModeTest {

    @Test
    fun everyPresetHasNumbersAndCustomHasNone() {
        PerformanceMode.entries.forEach { mode ->
            if (mode == PerformanceMode.CUSTOM) {
                assertNull("custom means the user's own numbers", mode.policy)
            } else {
                val policy = mode.policy
                assertNotNull("$mode must stand for something", policy)
                assertTrue("$mode must ask for at least one task", policy!!.concurrentTasks >= 1)
                assertTrue("$mode must ask for a real batch", policy.batchSize >= 100)
            }
        }
    }

    @Test
    fun theBatterySaverIsTheGentlestOne() {
        val saver = PerformanceMode.BATTERY_SAVER.policy!!
        val balanced = PerformanceMode.BALANCED.policy!!
        val performance = PerformanceMode.PERFORMANCE.policy!!

        assertTrue(saver.concurrentTasks < balanced.concurrentTasks)
        assertTrue(balanced.concurrentTasks < performance.concurrentTasks)
        assertTrue(saver.batchSize < balanced.batchSize)
        assertTrue(balanced.batchSize < performance.batchSize)
        // A battery saver that keeps scanning with the app off screen is not a battery saver.
        assertTrue("the saver must pause in the background", saver.pauseWhenBackgrounded)
        assertFalse(balanced.pauseWhenBackgrounded)
        assertFalse(performance.pauseWhenBackgrounded)
    }

    @Test
    fun aPresetOverridesTheIndividualControlsAndCustomUsesThem() {
        val preset = AppSettings.Defaults.copy(
            performanceMode = PerformanceMode.BATTERY_SAVER,
            maxConcurrentTasks = 16,
            scanBatchSize = 5_000,
            pauseWhenBackgrounded = false,
        )
        assertEquals(2, preset.effectiveConcurrency)
        assertEquals(250, preset.effectiveBatchSize)
        assertTrue(preset.effectivePauseWhenBackgrounded)

        val custom = preset.copy(performanceMode = PerformanceMode.CUSTOM)
        assertEquals(16, custom.effectiveConcurrency)
        assertEquals(5_000, custom.effectiveBatchSize)
        assertFalse(custom.effectivePauseWhenBackgrounded)
    }
}

class ThemeModeTest {

    @Test
    fun systemFollowsTheDeviceAndTheOthersDoNot() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemInDarkTheme = false))
        assertTrue(ThemeMode.DARK.isDark(systemInDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemInDarkTheme = true))
    }
}
