package com.progallery.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central place for the storage/media permission matrix so the rules stay
 * consistent between the UI, the repositories and tests.
 */
object PermissionUtils {

    /**
     * Permissions to request on the current platform. On Android 14+ we also
     * request READ_MEDIA_VISUAL_USER_SELECTED so the user can grant partial access.
     */
    fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Full read access to all photos and videos. */
    fun hasFullAccess(context: Context): Boolean = requiredPermissions()
        .filterNot { Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            it == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        .all { granted(context, it) }

    /** Android 14+ partial ("selected photos") access. */
    fun hasPartialAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
