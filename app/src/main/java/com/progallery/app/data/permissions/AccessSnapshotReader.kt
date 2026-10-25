package com.progallery.app.data.permissions

import android.content.Context
import android.os.Build
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            allFiles = PermissionUtils.hasAllFilesAccess(context),
            grantedFolders = runCatching { scanDirectoryDao.all().size }.getOrDefault(0),
            mediaItemCount = mediaItemCount(),
        )
    }

    /**
     * Photos and videos the app can see right now.
     *
     * A count query rather than a read: the number is what matters, and pulling the rows would
     * be work done to be thrown away. Images and videos only — audio and documents are not what
     * the media permission controls, so counting them would inflate the number that is meant to
     * say whether the gallery is actually visible.
     */
    private suspend fun mediaItemCount(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
            val arguments = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            )
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns._ID),
                selection,
                arguments,
                null,
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
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
