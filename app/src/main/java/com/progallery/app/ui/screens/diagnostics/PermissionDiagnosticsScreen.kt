package com.progallery.app.ui.screens.diagnostics

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.progallery.app.data.permissions.AccessFix
import com.progallery.app.data.permissions.AccessRow
import com.progallery.app.data.permissions.AccessState
import com.progallery.app.util.PermissionUtils

/**
 * What the app can and cannot reach, why it matters, and how to change it.
 *
 * Every fix here goes through the official route — the system dialog, the system
 * settings screen, or the system folder picker. Nothing is worked around, and nothing
 * here asks for more than the row describes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PermissionDiagnosticsScreen(
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    viewModel: PermissionDiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The normal permission dialog.
    val permissionRequest = rememberMultiplePermissionsState(
        permissions = PermissionUtils.requiredPermissions(),
    ) { viewModel.refresh() }

    // The system photo picker, the documented way to widen a partial grant.
    val morePhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    // For the screens that live in system Settings.
    val settingsScreen = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Access") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check again")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = state.headline,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item {
                Text(
                    // Said plainly, because it is the thing people worry about.
                    text = "This screen only reports what the app can reach. Nothing is " +
                        "requested here that the row does not describe, and nothing is sent " +
                        "anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            items(state.rows, key = { it.id.name }) { row ->
                AccessCard(
                    row = row,
                    onFix = {
                        viewModel.noteFixAttempt(row)
                        when (row.fix) {
                            AccessFix.REQUEST_PERMISSION -> permissionRequest.launchMultiplePermissionRequest()
                            AccessFix.REQUEST_MORE_PHOTOS ->
                                PermissionUtils.reselectMediaIntent()?.let { morePhotos.launch(it) }
                            AccessFix.OPEN_ALL_FILES_SETTINGS ->
                                settingsScreen.launch(PermissionUtils.allFilesAccessIntent(context))
                            AccessFix.OPEN_APP_SETTINGS ->
                                settingsScreen.launch(
                                    Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            AccessFix.PICK_FOLDER -> onPickFolder()
                            AccessFix.NONE -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AccessCard(row: AccessRow, onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (row.state == AccessState.DENIED) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(row.state.label()) },
                )
            }
            Text(
                text = row.enables,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = row.consequence,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (row.fix != AccessFix.NONE && row.fixLabel != null) {
                TextButton(
                    onClick = onFix,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(row.fixLabel)
                }
            }
        }
    }
}

private fun AccessState.label(): String = when (this) {
    AccessState.GRANTED -> "Allowed"
    AccessState.PARTIAL -> "Partly allowed"
    AccessState.DENIED -> "Not allowed"
    AccessState.NOT_APPLICABLE -> "Not needed"
}
