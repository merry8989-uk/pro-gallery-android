package com.progallery.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update used to leave the app for a browser. These are the cases that decide what it does
 * instead, and each one is a situation a real phone is in.
 */
class UpdateInstallPlanTest {

    @Test
    fun anOrdinarySideloadedCopyOffersADownloadFirst() {
        assertEquals(
            UpdateInstallStep.DOWNLOAD,
            UpdateInstallPlan.step(
                installedFromPlay = false,
                hasApk = true,
                downloaded = false,
                canInstallPackages = true,
            ),
        )
    }

    @Test
    fun onceDownloadedAndAllowedItOffersTheInstall() {
        assertEquals(
            UpdateInstallStep.INSTALL,
            UpdateInstallPlan.step(false, hasApk = true, downloaded = true, canInstallPackages = true),
        )
    }

    @Test
    fun beforeThePermissionIsGrantedItAsksForItRatherThanInstalling() {
        // Installing without it would fail; asking first is the difference between a step and
        // an error.
        assertEquals(
            UpdateInstallStep.ALLOW_INSTALLS,
            UpdateInstallPlan.step(false, hasApk = true, downloaded = true, canInstallPackages = false),
        )
    }

    @Test
    fun aPlayInstalledCopyIsSentToTheStoreInstead() {
        assertEquals(
            UpdateInstallStep.PLAY,
            UpdateInstallPlan.step(true, hasApk = true, downloaded = false, canInstallPackages = true),
        )
    }

    @Test
    fun aReleaseWithNoApkCannotBeInstalledFromHere() {
        assertEquals(
            UpdateInstallStep.UNAVAILABLE,
            UpdateInstallPlan.step(false, hasApk = false, downloaded = false, canInstallPackages = true),
        )
    }

    @Test
    fun aPlayInstallIsPreferredOverEverythingElse() {
        // Even with the file already downloaded and permission granted, a Play install should
        // not be replaced by a hand-installed one.
        assertEquals(
            UpdateInstallStep.PLAY,
            UpdateInstallPlan.step(true, hasApk = true, downloaded = true, canInstallPackages = true),
        )
    }

    @Test
    fun everyStepHasALabelToShow() {
        UpdateInstallStep.entries.forEach { step ->
            assertTrue(step.name, UpdateInstallPlan.actionLabel(step).isNotBlank())
        }
    }

    @Test
    fun theDownloadStepSaysTheFileIsCheckedBeforeInstalling() {
        val note = UpdateInstallPlan.note(UpdateInstallStep.DOWNLOAD)!!
        assertTrue("it should mention the check", note.contains("checked"))
        assertTrue("and the signing key", note.contains("signing key"))
    }

    @Test
    fun theInstallStepSaysTheSystemWillAsk() {
        // The one thing that must never be implied: that this can happen silently.
        val note = UpdateInstallPlan.note(UpdateInstallStep.INSTALL)!!
        assertTrue("it must say the system asks", note.contains("confirmation"))
        assertTrue("and that it cannot be skipped", note.contains("cannot be skipped"))
    }

    @Test
    fun thePermissionStepSaysNothingIsInstalledYet() {
        val note = UpdateInstallPlan.note(UpdateInstallStep.ALLOW_INSTALLS)!!
        assertTrue(note.contains("permission"))
        assertTrue("it must not read as though the install already happened",
            note.contains("nothing is installed"))
    }

    @Test
    fun thePlayStepExplainsWhyItGoesToTheStore() {
        val note = UpdateInstallPlan.note(UpdateInstallStep.PLAY)!!
        assertTrue(note.contains("Play"))
        assertTrue("and the reason", note.contains("conflict"))
    }

    @Test
    fun noStepClaimsItCanInstallSilently() {
        UpdateInstallStep.entries.forEach { step ->
            val note = UpdateInstallPlan.note(step) ?: return@forEach
            assertFalse(
                "$step must not promise a silent install",
                note.contains("automatically") || note.contains("without asking"),
            )
        }
    }

    @Test
    fun anUnverifiedFileIsNotOfferedForInstall() {
        // Android would refuse a differently-signed APK anyway; refusing it here means the
        // reason can be explained instead of surfacing as a failed install.
        assertTrue(UpdateInstallPlan.canOfferInstall(verified = true))
        assertFalse(UpdateInstallPlan.canOfferInstall(verified = false))
    }

    @Test
    fun everyStepHasANoteOrIsTheOneThatDoesNotNeedOne() {
        UpdateInstallStep.entries.forEach { step ->
            assertNotNull("$step should explain itself", UpdateInstallPlan.note(step))
        }
    }
}
