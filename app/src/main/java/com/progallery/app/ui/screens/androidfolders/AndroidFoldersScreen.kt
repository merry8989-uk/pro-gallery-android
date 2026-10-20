package com.progallery.app.ui.screens.androidfolders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.androidfolders.AndroidFolderTarget
import com.progallery.app.data.deepscan.ScanState
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.ViewMode
import com.progallery.app.ui.components.MediaCollection
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.util.DefaultApps
import com.progallery.app.util.PermissionUtils

/**
 * Access to the `Android/` folder, stated honestly.
 *
 * On Android 11+ the OS refuses `Android/data` and `Android/obb` to *every*
 * non-rooted app — including with "All files access" and the folder picker — so
 * this screen says so instead of offering a button that could never work. What
 * it does offer is everything that is genuinely reachable: shared media under
 * `Android/media`, this app's own folder, and the legacy tree on Android 10.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidFoldersScreen(
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    viewModel: AndroidFoldersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshAccess() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    MediaPermissionGate {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Android folders") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Surface(tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("What can be read", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Android keeps part of the Android/ folder private. This is the same for " +
                                    "every app on a non-rooted phone, and no permission unlocks it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                items(state.reachability.size) { index ->
                    val item = state.reachability[index]
                    Surface(tonalElevation = 1.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = if (item.reachable) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (item.reachable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(item.target.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = item.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    if (!state.hasAllFilesAccess) {
                        TextButton(onClick = {
                            runCatching { context.startActivity(PermissionUtils.allFilesAccessIntent(context)) }
                        }) { Text("Grant All files access to reach Android/media") }
                    }
                }

                item {
                    Column {
                        Text("Scan these", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.reachability.filter { it.reachable }.forEach { item ->
                                FilterChip(
                                    onClick = { viewModel.toggleTarget(item.target) },
                                    selected = item.target in state.selectedTargets,
                                    label = { Text(item.target.label.substringAfter("Android/")) },
                                )
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.startScan() },
                            enabled = state.selectedTargets.isNotEmpty() && !state.scanning,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("  Scan")
                        }
                        if (state.scanning) {
                            OutlinedButton(onClick = { viewModel.cancelScan() }) {
                                Icon(Icons.Filled.Cancel, contentDescription = null)
                                Text("  Cancel")
                            }
                        }
                    }
                }

                when (val scan = state.scanState) {
                    is ScanState.Scanning -> item {
                        Column {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Visited ${scan.directoriesScanned} folders, examined ${scan.filesExamined} " +
                                    "files, found ${scan.mediaFound}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (scan.currentPath.isNotBlank()) {
                                Text(
                                    text = scan.currentPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is ScanState.Failed -> item {
                        Text(scan.message, color = MaterialTheme.colorScheme.error)
                    }

                    is ScanState.Completed -> item {
                        Column {
                            Text(
                                text = "Found ${scan.summary.mediaFound} file(s) in " +
                                    "${scan.summary.directoriesScanned} folder(s).",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (scan.summary.signatureIdentified > 0) {
                                Text(
                                    "Identified by file signature: ${scan.summary.signatureIdentified}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (scan.summary.errors.isNotEmpty()) {
                                Text(
                                    text = "Not readable:",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                scan.summary.errors.forEach { error ->
                                    Text(
                                        text = "• $error",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    ScanState.Idle -> Unit
                }

                if (state.scanState is ScanState.Completed) {
                    item {
                        val media = (state.scanState as ScanState.Completed).media
                        if (media.isEmpty()) {
                            Text(
                                "Nothing found in the folders you selected.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            MediaCollection(
                                items = media,
                                columns = 3,
                                viewMode = ViewMode.GRID,
                                selectionMode = false,
                                selectedIds = emptySet<String>(),
                                onClick = onOpen,
                                onLongClick = {},
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                item {
                    TextButton(onClick = { DefaultApps.openDefaultAppsSettings(context) }) {
                        Text("Open system Default apps")
                    }
                }
            }
        }
    }
}
