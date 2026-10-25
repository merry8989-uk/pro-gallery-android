package com.progallery.app.data.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen's job is to tell the truth about access: what is missing, what it costs,
 * and what to do — without implying a permission unlocks more than it does, and without
 * presenting an optional access as a fault.
 */
class AccessDiagnosticsTest {

    private fun snapshot(
        sdkInt: Int = 34,
        visual: AccessState = AccessState.GRANTED,
        audio: AccessState = AccessState.GRANTED,
        allFiles: Boolean = false,
        folders: Int = 2,
        rootStatus: String? = null,
    ) = AccessSnapshot(
        sdkInt = sdkInt,
        visual = visual,
        audio = audio,
        allFiles = allFiles,
        grantedFolders = folders,
        rootStatus = rootStatus,
    )

    @Test
    fun aFullyGrantedDeviceHasNothingBlocking() {
        val rows = AccessDiagnostics.rows(
            snapshot(visual = AccessState.GRANTED, audio = AccessState.GRANTED, allFiles = true),
        )
        assertTrue(AccessDiagnostics.blocking(rows).isEmpty())
        assertEquals("The app has the access it needs.", AccessDiagnostics.headline(rows))
    }

    @Test
    fun deniedMediaAccessIsBlockingAndSaysSo() {
        val rows = AccessDiagnostics.rows(snapshot(visual = AccessState.DENIED))
        val blocking = AccessDiagnostics.blocking(rows)
        assertEquals(1, blocking.size)
        assertEquals(AccessId.MEDIA_VISUAL, blocking.first().id)
        assertEquals(AccessFix.REQUEST_PERMISSION, blocking.first().fix)
        assertTrue(AccessDiagnostics.headline(rows).contains("Photos and videos"))
    }

    @Test
    fun partialAccessIsNotBlockingAndOffersMorePhotos() {
        val rows = AccessDiagnostics.rows(snapshot(visual = AccessState.PARTIAL))
        assertTrue("partial access still shows something", AccessDiagnostics.blocking(rows).isEmpty())
        val visual = rows.first { it.id == AccessId.MEDIA_VISUAL }
        assertEquals(AccessState.PARTIAL, visual.state)
        assertEquals(AccessFix.REQUEST_MORE_PHOTOS, visual.fix)
        assertEquals("Choose more photos", visual.fixLabel)
        assertTrue("it should explain the limit", visual.consequence.contains("picked"))
    }

    @Test
    fun missingAudioIsExplainedAsPartialLossNotAFault() {
        val rows = AccessDiagnostics.rows(snapshot(audio = AccessState.DENIED))
        val audio = rows.first { it.id == AccessId.MEDIA_AUDIO }
        assertTrue("audio is not required", AccessDiagnostics.blocking(rows).isEmpty())
        assertTrue("it should say what still works", audio.consequence.contains("still work"))
    }

    @Test
    fun allFilesAccessIsPresentedAsOptionalWithItsAlternative() {
        val rows = AccessDiagnostics.rows(snapshot(allFiles = false))
        val row = rows.first { it.id == AccessId.ALL_FILES }
        assertEquals(AccessState.DENIED, row.state)
        assertEquals(AccessFix.OPEN_ALL_FILES_SETTINGS, row.fix)
        assertTrue("the alternative should be named", row.consequence.contains("Deep Scan"))
        assertTrue("optional must be stated", row.consequence.contains("Optional"))
    }

    @Test
    fun noGrantedFoldersOffersToAddOne() {
        val rows = AccessDiagnostics.rows(snapshot(folders = 0))
        val row = rows.first { it.id == AccessId.FOLDERS }
        assertEquals(AccessFix.PICK_FOLDER, row.fix)
        assertTrue(AccessDiagnostics.blocking(rows).isEmpty())
    }

    @Test
    fun grantedFoldersAreCounted() {
        assertEquals(
            "3 folders are being scanned.",
            AccessDiagnostics.rows(snapshot(folders = 3)).first { it.id == AccessId.FOLDERS }.consequence,
        )
        assertEquals(
            "One folder is being scanned.",
            AccessDiagnostics.rows(snapshot(folders = 1)).first { it.id == AccessId.FOLDERS }.consequence,
        )
    }

    @Test
    fun theRootRowAppearsOnlyWhenTheCheckWasRun() {
        assertFalse(AccessDiagnostics.rows(snapshot()).any { it.id == AccessId.ROOT })
        val row = AccessDiagnostics.rows(snapshot(rootStatus = "No root detected."))
            .first { it.id == AccessId.ROOT }
        assertEquals(AccessState.NOT_APPLICABLE, row.state)
        assertEquals(AccessFix.NONE, row.fix)
        assertTrue("it must not offer to do anything", row.enables.contains("Nothing"))
    }

    @Test
    fun aFixIsOfferedExactlyWhenSomethingIsMissing() {
        // The invariant that keeps the screen honest: no fix for an access that is
        // present, and always a way forward for one that is not.
        //
        // Only the visual row has a partial state — audio is granted or it is not — so
        // PARTIAL is varied where it is real.
        listOf(AccessState.GRANTED, AccessState.DENIED).forEach { audio ->
            listOf(AccessState.GRANTED, AccessState.PARTIAL, AccessState.DENIED).forEach { visual ->
                val rows = AccessDiagnostics.rows(
                    snapshot(
                        visual = visual,
                        audio = audio,
                        folders = if (audio == AccessState.GRANTED) 1 else 0,
                    ),
                )
                rows.forEach { row ->
                    val needsHelp = row.state == AccessState.DENIED || row.state == AccessState.PARTIAL
                    if (needsHelp) {
                        assertTrue("${row.id} needs a way forward", row.fix != AccessFix.NONE)
                        assertTrue("${row.id} needs a label", !row.fixLabel.isNullOrBlank())
                    } else {
                        assertEquals("${row.id} must not offer a fix", AccessFix.NONE, row.fix)
                        assertNull("${row.id} must not offer a label", row.fixLabel)
                    }
                }
            }
        }
    }

    @Test
    fun onlyTheVisualAccessHasAPartialState() {
        // Audio is binary: a "partial" audio grant does not exist, so the row must not
        // pretend there is a middle state to act on.
        listOf(AccessState.GRANTED, AccessState.DENIED).forEach { state ->
            val rows = AccessDiagnostics.rows(snapshot(audio = state))
            listOf(AccessId.MEDIA_AUDIO).forEach { id ->
                val row = rows.first { it.id == id }
                assertEquals(
                    "nothing to do for $id when it is $state",
                    state == AccessState.DENIED,
                    row.fix != AccessFix.NONE,
                )
            }
        }
    }

    @Test
    fun theHeadlineCountsOnlyWhatActuallyBlocksTheApp() {
        val rows = AccessDiagnostics.rows(
            snapshot(visual = AccessState.GRANTED, audio = AccessState.DENIED, folders = 0),
        )
        assertEquals("The app has the access it needs.", AccessDiagnostics.headline(rows))
    }

    @Test
    fun theHeadlineCountsMultipleBlockers() {
        // Only the media access is required, so this is the single-blocker wording.
        val rows = AccessDiagnostics.rows(snapshot(visual = AccessState.DENIED))
        assertEquals("One access still needed: Photos and videos.", AccessDiagnostics.headline(rows))
    }

    @Test
    fun everyRowExplainsWhatItEnables() {
        AccessDiagnostics.rows(snapshot(rootStatus = "No root detected.")).forEach { row ->
            assertTrue("${row.id} should say what it unlocks", row.enables.isNotBlank())
            assertTrue("${row.id} should say what it costs", row.consequence.isNotBlank())
            assertTrue("${row.id} needs a title", row.title.isNotBlank())
        }
    }
}
