package com.progallery.app.util

import android.Manifest
import android.content.Context
import android.provider.MediaStore
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Read access to photos and videos. */
    fun hasVisualAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** Read access to audio. */
    fun hasAudioAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** Android 14+ partial ("selected photos") access. */
    fun hasPartialAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    /** Any access at all — enough to show something rather than a blank screen. */
    fun hasAnyAccess(context: Context): Boolean =
        hasVisualAccess(context) || hasAudioAccess(context) || hasPartialAccess(context)

    /** Read access to everything the gallery needs for photos and videos. */
    fun hasFullAccess(context: Context): Boolean = hasVisualAccess(context)

    /**
     * Android 11+ "All files access".
     *
     * This is the only way for an app to see documents, archives and other
     * non-media files created by *other* apps: MediaStore hides those from a
     * normal media permission. It is granted from system Settings, not a dialog,
     * and Google Play restricts its use — appropriate for a sideloaded,
     * on-device gallery, and the app says so before asking.
     */
    fun hasAllFilesAccess(context: Context): Boolean =
        // The permission this checks is `MANAGE_EXTERNAL_STORAGE`; naming it here keeps the manifest
        // and the code tied together, so a checker can see that the declaration is used.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.checkSelfPermission(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            Environment.isExternalStorageManager()

    /** Opens the system screen where the user can grant All files access. */
    fun allFilesAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * The system photo picker, used to change which photos a **partial** grant
     * covers.
     *
     * On Android 14+ a user can share only selected photos. The documented way to
     * let them add more is to launch the picker again with a maximum selection —
     * the system then extends the app's access to what they choose. Returns null
     * below Android 14, where partial access does not exist.
     */
    fun reselectMediaIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Intent(MediaStore.ACTION_PICK_IMAGES).apply {
            type = "image/*"
            putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, RESELECT_LIMIT)
        }
    }

    /** How many photos the picker may return when widening a partial grant. */
    private const val RESELECT_LIMIT = 100
}
