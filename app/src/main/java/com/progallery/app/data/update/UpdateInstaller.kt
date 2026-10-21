package com.progallery.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a verified update to Android's own installer.
 *
 * **Why the system installer and not this app.** Android does not allow an app to replace
 * itself silently — there is no permission for it, by design, and no workaround short of root.
 * What an app *can* do is fetch the file, check it, and ask the system to install it, which is
 * what happens here: the confirmation the user sees is Android's own, and afterwards they come
 * back to the app.
 *
 * The file is shared through the app's existing FileProvider, so no path is exposed and the
 * receiving installer gets read access to that one file.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Whether Android will let this app ask for a package to be installed.
     *
     * On Android 8 and later this is a per-app switch the user controls, separate from the
     * manifest declaration.
     */
    fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** The screen where that switch is turned on for this app. */
    fun installPermissionSettings(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    /**
     * Asks the system to install [file].
     *
     * The URI comes from the FileProvider, because handing out a `file://` path would throw on
     * Android 7 and later — the receiving installer has no access to the app's private storage
     * otherwise.
     */
    fun install(file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** The Android version, so the UI can be accurate about what happens next. */
    val requiresUnknownSourcesSetting: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
