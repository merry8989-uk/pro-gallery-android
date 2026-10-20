package com.progallery.app.data.androidfolders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reachability rules are a platform fact, so they are pinned down here:
 * a non-rooted app cannot read other apps' Android/data or Android/obb from
 * Android 11, no matter which permission it holds.
 */
class AndroidFolderPolicyTest {

    private fun reachable(
        sdk: Int,
        hasAllFilesAccess: Boolean,
        target: AndroidFolderTarget,
    ): Boolean = AndroidFolderPolicy.evaluate(sdk, hasAllFilesAccess)
        .first { it.target == target }
        .reachable

    @Test
    fun android10AndBelow_everythingIsOrdinaryStorage() {
        for (sdk in listOf(26, 28, 29)) {
            assertTrue("media on $sdk", reachable(sdk, false, AndroidFolderTarget.MEDIA))
            assertTrue("other apps' data on $sdk", reachable(sdk, false, AndroidFolderTarget.OTHER_APP_DATA))
            assertTrue("obb on $sdk", reachable(sdk, false, AndroidFolderTarget.OBB))
        }
    }

    @Test
    fun android11AndAbove_blockOtherAppsDataAndObb_evenWithAllFilesAccess() {
        for (sdk in listOf(30, 31, 32, 33, 34, 35)) {
            assertFalse("other apps' data on $sdk", reachable(sdk, true, AndroidFolderTarget.OTHER_APP_DATA))
            assertFalse("obb on $sdk", reachable(sdk, true, AndroidFolderTarget.OBB))
            // ...and the report must say why, not just "no".
            val reason = AndroidFolderPolicy.evaluate(sdk, true)
                .first { it.target == AndroidFolderTarget.OTHER_APP_DATA }
                .reason
            assertTrue("reason should name Android 11+", reason.contains("Android 11"))
        }
    }

    @Test
    fun androidMedia_needsAllFilesAccessFromAndroid11() {
        assertFalse(reachable(33, false, AndroidFolderTarget.MEDIA))
        assertTrue(reachable(33, true, AndroidFolderTarget.MEDIA))
    }

    @Test
    fun ownAppFolderIsAlwaysReadable() {
        for (sdk in listOf(26, 29, 30, 33, 35)) {
            assertTrue("own data on $sdk", reachable(sdk, false, AndroidFolderTarget.OWN_DATA))
        }
    }

    @Test
    fun reachableTargets_matchesTheDetailedReport() {
        val report = AndroidFolderPolicy.evaluate(33, true)
        val reachable = AndroidFolderPolicy.reachableTargets(33, true)
        assertEquals(report.filter { it.reachable }.map { it.target }, reachable)

        // On Android 13 with All files access: media and our own folder, nothing else.
        assertEquals(
            listOf(AndroidFolderTarget.MEDIA, AndroidFolderTarget.OWN_DATA),
            reachable,
        )

        // Without All files access on Android 13: only our own folder.
        assertEquals(
            listOf(AndroidFolderTarget.OWN_DATA),
            AndroidFolderPolicy.reachableTargets(33, false),
        )
    }

    @Test
    fun everyTargetIsAlwaysReported() {
        val targets = AndroidFolderPolicy.evaluate(33, true).map { it.target }
        assertEquals(AndroidFolderTarget.entries.toList(), targets)
    }
}
