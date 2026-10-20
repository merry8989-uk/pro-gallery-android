package com.progallery.app.data.permissions

import android.content.Context
import android.os.Build
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the real access state off the device.
 *
 * Kept apart from [AccessDiagnostics] on purpose: this part talks to Android, and the
 * part that decides what to say is pure and unit tested.
 */
@Singleton
class AccessSnapshotReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanDirectoryDao: ScanDirectoryDao,
) {

    suspend fun read(): AccessSnapshot {
        val sdk = Build.VERSION.SDK_INT
        return AccessSnapshot(
            sdkInt = sdk,
            visual = visualState(sdk),
            // Below Android 13 the storage permission covers audio too, so there is no
            // separate audio access to report.
            audio = when {
                sdk < Build.VERSION_CODES.TIRAMISU -> AccessState.NOT_APPLICABLE
                PermissionUtils.hasAudioAccess(context) -> AccessState.GRANTED
                else -> AccessState.DENIED
            },
            location = if (PermissionUtils.hasLocationAccess(context)) {
                AccessState.GRANTED
            } else {
                AccessState.DENIED
            },
            allFiles = PermissionUtils.hasAllFilesAccess(context),
            grantedFolders = runCatching { scanDirectoryDao.all().size }.getOrDefault(0),
        )
    }

    /**
     * Partial access only exists from Android 14, and it is reported only when the
     * broad permission is genuinely absent — otherwise a full grant would be described
     * as partial.
     */
    private fun visualState(sdk: Int): AccessState = when {
        PermissionUtils.hasVisualAccess(context) -> AccessState.GRANTED
        sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && PermissionUtils.hasPartialAccess(context) ->
            AccessState.PARTIAL
        else -> AccessState.DENIED
    }
}
