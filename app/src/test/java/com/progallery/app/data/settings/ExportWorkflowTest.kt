package com.progallery.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export workflow's settings.
 *
 * The one thing worth pinning here is **which policy the exporter may decide for itself**. Two of
 * the three are rules it applies; the third is a question only a person can answer, and treating it
 * as a rule is how an "ask me" setting quietly becomes "decide for me".
 */
class ExportWorkflowTest {

    @Test
    fun onlyAskNeedsAPerson() {
        assertFalse("Ask cannot be decided by the exporter", ExportDuplicatePolicy.ASK.decidesItself)
        assertTrue(ExportDuplicatePolicy.RENAME.decidesItself)
        assertTrue(ExportDuplicatePolicy.SKIP.decidesItself)
    }

    @Test
    fun everyPolicyHasALabelBecauseItIsShown() {
        ExportDuplicatePolicy.entries.forEach { policy ->
            assertTrue("$policy has no label to show", policy.label.isNotBlank())
        }
    }

    @Test
    fun askIsOfferedFirstSoTheSafestChoiceIsTheDefaultOneToRead() {
        // Not the default *value* — the default is still Rename, because asking on every clash
        // would be a nuisance for someone exporting many files at once. It is only listed first.
        assertEquals(ExportDuplicatePolicy.ASK, ExportDuplicatePolicy.entries.first())
        assertEquals(ExportDuplicatePolicy.RENAME, AppSettings.Defaults.exportDuplicatePolicy)
    }

    @Test
    fun theWorkflowDefaultsAreTheCautiousOnes() {
        val d = AppSettings.Defaults
        assertTrue("a tap should not start a copy", d.exportConfirmBeforeExport)
        assertTrue("asking where is the default", d.exportAskForDestination)
        assertTrue("the file keeps its own name by default", d.exportPreserveNames)
        assertEquals(90, d.exportHistoryRetentionDays)
    }

    @Test
    fun anImportedRetentionIsClampedLikeEveryOtherNumber() {
        // A hand-edited or corrupted settings file must not be able to make the purge delete
        // everything, or keep it for ever.
        assertEquals(1, AppSettings.Defaults.copy(exportHistoryRetentionDays = 0).sanitized().exportHistoryRetentionDays)
        assertEquals(1, AppSettings.Defaults.copy(exportHistoryRetentionDays = -5).sanitized().exportHistoryRetentionDays)
        assertEquals(3_650, AppSettings.Defaults.copy(exportHistoryRetentionDays = 999_999).sanitized().exportHistoryRetentionDays)
    }

    @Test
    fun theNewSettingsSurviveASectionReset() {
        val changed = AppSettings.Defaults.copy(
            exportConfirmBeforeExport = false,
            exportAskForDestination = false,
            exportPreserveNames = false,
            exportHistoryRetentionDays = 7,
        )
        val reset = changed.resetSection("Recovery & export")
        assertEquals(AppSettings.Defaults.exportConfirmBeforeExport, reset.exportConfirmBeforeExport)
        assertEquals(AppSettings.Defaults.exportAskForDestination, reset.exportAskForDestination)
        assertEquals(AppSettings.Defaults.exportPreserveNames, reset.exportPreserveNames)
        assertEquals(AppSettings.Defaults.exportHistoryRetentionDays, reset.exportHistoryRetentionDays)
    }

    @Test
    fun theExportCarriesTheNewSettingsAndNoDestination() {
        val json = SettingsExport.encode(AppSettings.Defaults.copy(exportPreserveNames = false))
        assertTrue("the setting must travel", json.contains("exportPreserveNames"))
        // The remembered destination is deliberately not a setting; if it ever becomes one, its
        // access grant would travel in this file.
        assertFalse("a URI grant must never reach the export", json.contains("treeUri"))
        assertFalse("a URI grant must never reach the export", json.contains("content://"))
    }
}
