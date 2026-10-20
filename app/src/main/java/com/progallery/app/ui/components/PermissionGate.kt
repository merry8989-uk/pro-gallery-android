package com.progallery.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.progallery.app.util.PermissionUtils

/**
 * Gates media content behind the correct runtime permissions for the running
 * Android version, and explains partial access on Android 14+.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionGate(content: @Composable () -> Unit) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionUtils.requiredPermissions(),
    )

    val granted = permissionsState.permissions
        .filterNot { it.permission == android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        .all { it.status.isGranted }
    val partial = permissionsState.permissions
        .firstOrNull { it.permission == android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        ?.status?.isGranted == true

    when {
        granted -> content()
        partial -> Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Limited access: showing only the items you selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("Grant full access") }
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
                text = "ProGallery needs permission to read your photos and videos so it can show them here. Nothing ever leaves your device.",
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
