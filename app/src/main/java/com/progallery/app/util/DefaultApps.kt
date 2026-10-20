package com.progallery.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Android has no API to make an app the default gallery on the user's behalf —
 * only the user can do that. What an app *can* do is declare itself as a
 * candidate (see the APP_GALLERY / ACTION_VIEW intent filters in the manifest)
 * and send the user straight to the screen where they choose.
 */
object DefaultApps {

    /** Opens the system "Default apps" screen. */
    fun openDefaultAppsSettings(context: Context): Boolean = runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrElse {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
