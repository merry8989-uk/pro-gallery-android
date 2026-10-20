package com.progallery.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A switch must never claim to be doing something it cannot do. These are the rules
 * that keep that true, checked one by one.
 */
class SettingsDependenciesTest {

    @Test
    fun deepScanIsTurnedOffWithFolderAccess() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(safScanEnabled = false, deepScanEnabled = true),
        )
        assertFalse("deep scan walks granted folders; without them it is meaningless", resolved.deepScanEnabled)
    }

    @Test
    fun signatureDetectionIsTurnedOffWhenNoScanMethodIsOn() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(
                quickScanEnabled = false,
                deepScanEnabled = false,
                safScanEnabled = false,
                signatureDetectionEnabled = true,
            ),
        )
        assertFalse(resolved.signatureDetectionEnabled)
    }

    @Test
    fun duplicateDetectionNeedsAFolderWalk() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(
                quickScanEnabled = true,
                deepScanEnabled = false,
                safScanEnabled = false,
                duplicateDetectionEnabled = true,
            ),
        )
        assertFalse("duplicates are only found by walking folders", resolved.duplicateDetectionEnabled)
    }

    @Test
    fun duplicateDetectionSurvivesWithAFolderWalkOn() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(deepScanEnabled = true, duplicateDetectionEnabled = true),
        )
        assertTrue(resolved.duplicateDetectionEnabled)
    }

    @Test
    fun hiddenFilesNeedAFolderWalk() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(
                deepScanEnabled = false,
                safScanEnabled = false,
                includeHiddenFiles = true,
            ),
        )
        assertFalse(resolved.includeHiddenFiles)
    }

    @Test
    fun perTypePreviewsAreTurnedOffWithThumbnailGeneration() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(
                generateThumbnails = false,
                videoThumbnails = true,
                audioArtwork = true,
                documentPreviews = true,
            ),
        )
        assertFalse(resolved.videoThumbnails)
        assertFalse(resolved.audioArtwork)
        assertFalse(resolved.documentPreviews)
    }

    @Test
    fun exportNeedsSomethingToExportFrom() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(
                recoveryQuickScanEnabled = false,
                recoveryThumbnailCacheEnabled = false,
                exportEnabled = true,
            ),
        )
        assertFalse(resolved.exportEnabled)
    }

    @Test
    fun loggingPathsNeedsLoggingOn() {
        val resolved = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(saveDiagnosticLogs = false, includePathsInLogs = true),
        )
        assertFalse("a log that is not written cannot carry paths", resolved.includePathsInLogs)
    }

    @Test
    fun resolveIsIdempotent() {
        val once = SettingsDependencies.resolve(
            AppSettings.Defaults.copy(
                safScanEnabled = false,
                deepScanEnabled = true,
                includeHiddenFiles = true,
                generateThumbnails = false,
                videoThumbnails = true,
                exportEnabled = true,
                recoveryQuickScanEnabled = false,
                recoveryThumbnailCacheEnabled = false,
            ),
        )
        assertEquals(once, SettingsDependencies.resolve(once))
    }

    @Test
    fun resolveNeverTurnsSomethingOn() {
        // A resolution that enabled a feature would be a surprise, not a correction.
        val off = AppSettings(
            quickScanEnabled = false,
            deepScanEnabled = false,
            safScanEnabled = false,
            signatureDetectionEnabled = false,
            duplicateDetectionEnabled = false,
            generateThumbnails = false,
            videoThumbnails = false,
            audioArtwork = false,
            documentPreviews = false,
            includeHiddenFiles = false,
            exportEnabled = false,
            recoveryQuickScanEnabled = false,
            recoveryThumbnailCacheEnabled = false,
            saveDiagnosticLogs = false,
            includePathsInLogs = false,
            showRootStatus = false,
            advancedMode = false,
            experimentalMethodsEnabled = false,
        )
        val resolved = SettingsDependencies.resolve(off)
        listOf(
            resolved.quickScanEnabled, resolved.deepScanEnabled, resolved.safScanEnabled,
            resolved.signatureDetectionEnabled, resolved.duplicateDetectionEnabled,
            resolved.generateThumbnails, resolved.videoThumbnails, resolved.audioArtwork,
            resolved.documentPreviews, resolved.includeHiddenFiles, resolved.exportEnabled,
            resolved.recoveryQuickScanEnabled, resolved.recoveryThumbnailCacheEnabled,
            resolved.saveDiagnosticLogs, resolved.includePathsInLogs, resolved.showRootStatus,
            resolved.advancedMode, resolved.experimentalMethodsEnabled,
        ).forEach { assertFalse("resolve must not enable anything", it) }
    }

    @Test
    fun aFullyEnabledConfigurationIsLeftAlone() {
        val settings = AppSettings.Defaults.copy(
            quickScanEnabled = true,
            safScanEnabled = true,
            deepScanEnabled = true,
            signatureDetectionEnabled = true,
            duplicateDetectionEnabled = true,
            generateThumbnails = true,
            videoThumbnails = true,
            audioArtwork = true,
            documentPreviews = true,
            includeHiddenFiles = true,
            exportEnabled = true,
            recoveryQuickScanEnabled = true,
            saveDiagnosticLogs = true,
            includePathsInLogs = true,
        )
        assertEquals(settings, SettingsDependencies.resolve(settings))
    }

    @Test
    fun explainSaysWhySomethingWasTurnedOff() {
        val before = AppSettings.Defaults.copy(safScanEnabled = false, deepScanEnabled = true)
        val after = SettingsDependencies.resolve(before)
        val notes = SettingsDependencies.explain(before, after)
        assertTrue("the user should be told why", notes.any { it.contains("folder access") })
    }

    @Test
    fun explainIsSilentWhenNothingChanged() {
        val settings = AppSettings.Defaults
        assertTrue(SettingsDependencies.explain(settings, SettingsDependencies.resolve(settings)).isEmpty())
    }
}
