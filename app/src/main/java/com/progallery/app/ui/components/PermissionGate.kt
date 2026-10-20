package com.progallery.app.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.progallery.app.util.PermissionUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Gates media content behind the runtime permissions for the running Android
 * version.
 *
 * Access is granted if the app can see *any* category — photos, videos or audio.
 * Requiring every permission at once meant a single denial (typically audio)
 * blanked the whole app, which is exactly what made audio look "missing".
 * Whatever is still missing is offered as an actionable banner instead.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionGate(content: @Composable () -> Unit) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionUtils.requiredPermissions(),
    )

    val granted = permissionsState.permissions
        .associate { it.permission to it.status.isGranted }
    val needsAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val visualGranted = granted[Manifest.permission.READ_MEDIA_IMAGES] == true ||
        granted[Manifest.permission.READ_MEDIA_VIDEO] == true
    val audioGranted = !needsAudio || granted[Manifest.permission.READ_MEDIA_AUDIO] == true
    val partial = granted[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true

    when {
        visualGranted || audioGranted || partial -> Column(modifier = Modifier.fillMaxSize()) {
            if (needsAudio && !audioGranted) {
                PermissionBanner(
                    message = "Audio access is off, so music and recordings are hidden.",
                    action = "Grant",
                    onAction = { permissionsState.launchMultiplePermissionRequest() },
                )
            }
            if (partial && !visualGranted) {
                val reselect = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { /* The system applies the new selection; the refresh below picks it up. */ }

                PermissionBanner(
                    message = "Limited access: only the photos you selected are shown.",
                    action = "Grant full access",
                    onAction = { permissionsState.launchMultiplePermissionRequest() },
                    secondaryAction = "Choose more".takeIf { PermissionUtils.reselectMediaIntent() != null },
                    onSecondaryAction = {
                        PermissionUtils.reselectMediaIntent()?.let { reselect.launch(it) }
                    },
                )
            }
            content()
        }

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Media access needed",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "ProGallery needs permission to read your photos, videos and audio so it can show them here. Nothing ever leaves your device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Grant access") }
        }
    }
}

/**
 * Explains and offers the Android 11+ "All files access" grant. Without it,
 * MediaStore hides documents created by other apps, so PDFs and similar files
 * cannot appear in the main list — Deep Scan over a folder is the alternative.
 * The card disappears once the grant is in place.
 */
@Composable
fun AllFilesAccessCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(PermissionUtils.hasAllFilesAccess(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = PermissionUtils.hasAllFilesAccess(context)
    }
    if (granted) return

    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "See PDFs and other documents",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Android hides documents created by other apps from a normal media permission. " +
                    "Grant \"All files access\" to browse them here, or use Deep Scan on a folder instead.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = {
                    runCatching { context.startActivity(PermissionUtils.allFilesAccessIntent(context)) }
                },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Open settings") }
        }
    }
}

@Composable
private fun PermissionBanner(
    message: String,
    action: String,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (secondaryAction != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction) { Text(secondaryAction) }
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
