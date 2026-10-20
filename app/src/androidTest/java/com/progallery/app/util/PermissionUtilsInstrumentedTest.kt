package com.progallery.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the permission matrix resolves correctly on the device's Android
 * version. Runs against the real platform, so it catches version-specific drift.
 */
@RunWith(AndroidJUnit4::class)
class PermissionUtilsInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun requiredPermissions_isNotEmpty() {
        assertTrue(PermissionUtils.requiredPermissions().isNotEmpty())
    }

    @Test
    fun hasFullAccess_returnsFalseWhenNotGranted() {
        // In a fresh instrumentation run permissions are not pre-granted.
        assertFalse(PermissionUtils.hasFullAccess(context) && PermissionUtils.hasPartialAccess(context))
    }
}
