package com.progallery.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
                scanStickers = !base.scanStickers, keepActivityHistory = !base.keepActivityHistory,
            ),
        )
        assertEquals(SettingsAudit.MAX_REPORTED, changed.size)
    }

    @Test
    fun aFieldAddedLaterIsCoveredWithoutTouchingThisFile() {
        // The point of building on the export: every field it names is diffed. If this ever
        // returns nothing for a change, the export has stopped naming that field — which its own
        // test would have caught first.
        val changed = SettingsAudit.changed(base, base.copy(uploadDiagnosticsEnabled = true))
        assertEquals(setOf("uploadDiagnosticsEnabled"), changed.keys)
    }
}
