package com.progallery.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.progallery.app.util.DefaultApps
import androidx.compose.ui.res.stringResource
import com.progallery.app.R

/**
 * Shown once, on first launch, offering to make ProGallery the default gallery.
 *
 * The app cannot set this itself: Android only lets the user pick a default.
 * This dialog explains that, and the button opens the system screen where the
 * choice is actually made.
 */
@Composable
fun DefaultGalleryPrompt(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set ${stringResource(R.string.app_name)} as your gallery?") },
        text = {
            Text(
                "${stringResource(R.string.app_name)} can open your photos and videos by default. " +
                    "Android only lets you choose this yourself, so the next screen is where you pick it.\n\n" +
                    "You can change this any time in Settings → Default app.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                DefaultApps.openDefaultAppsSettings(context)
                onDismiss()
            }) { Text("Open default apps") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
