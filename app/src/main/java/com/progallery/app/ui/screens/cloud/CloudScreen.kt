package com.progallery.app.ui.screens.cloud

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.cloud.CloudKind
import com.progallery.app.util.MediaFormatUtils
import com.progallery.app.data.cloud.CloudDocument
import com.progallery.app.data.cloud.CloudRoot
import com.progallery.app.data.cloud.GrantedCloudTree

/**
 * Cloud browsing, inside the app.
 *
 * The providers listed here are the ones installed on the device — the same list the
 * system's own file picker shows, because it comes from the same source. **The app holds no
 * account and asks for no password**: the provider app owns the sign-in, which is why there
 * is nothing to sign into again later.
 *
 * A provider may allow its folders straight away, or ask to be granted one folder the first
 * time. That grant is persisted, so it is a one-time step and not a login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    onBack: () -> Unit,
    onOpen: (uri: String, kind: CloudKind) -> Unit,
    viewModel: CloudViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.open.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // The system folder picker, opened *at the provider's own root*, so the user picks a
    // folder inside that service rather than hunting for it.
    val grantPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val root = state.needsGrant
        if (uri == null || root == null) {
            viewModel.dismissGrant()
        } else {
            // Persisted, which is what makes this a one-time step.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.granted(uri.toString(), root.title)
        }
    }

    LaunchedEffect(pending) {
        pending?.let { (uri, kind) ->
            onOpen(uri, kind)
            viewModel.consumeOpen()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: "Cloud") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (state.title != null) viewModel.closeFolder() else onBack() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.title == null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Look again")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.title == null) {
            ServiceList(
                state = state,
                padding = padding,
                onOpenRoot = viewModel::openRoot,
                onOpenGranted = viewModel::openGranted,
                onForget = viewModel::forget,
            )
        } else {
            FolderBrowser(
                state = state,
                padding = padding,
                onOpenDocument = viewModel::openDocument,
                onUp = viewModel::goUp,
            )
        }
    }

    state.needsGrant?.let { root ->
        AlertDialog(
            onDismissRequest = viewModel::dismissGrant,
            title = { Text("Open ${root.title}") },
            text = {
                Text(
                    "${root.title} keeps its own sign-in, so there is nothing to log into " +
                        "here. Choose the folder inside it once and the app will remember it — " +
                        "you will not be asked again.",
                )
            },
            confirmButton = {
                TextButton(onClick = { grantPicker.launch(Uri.parse(root.treeUri)) }) {
                    Text("Choose the folder")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissGrant) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ServiceList(
    state: CloudUiState,
    padding: PaddingValues,
    onOpenRoot: (CloudRoot) -> Unit,
    onOpenGranted: (GrantedCloudTree) -> Unit,
    onForget: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                text = "These are the cloud services installed on this device. The app holds " +
                    "no account and never asks for a password — each service keeps its own " +
                    "sign-in, and a folder you choose once stays chosen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (state.granted.isNotEmpty()) {
            item { SectionLabel("READY TO BROWSE") }
            items(state.granted, key = { it.treeUri }) { granted ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenGranted(granted) },
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Text(
                        text = granted.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { onForget(granted.treeUri) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Forget this folder")
                    }
                }
            }
        }

        item { SectionLabel("SERVICES") }

        when {
            state.loading -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            }

            state.roots.isEmpty() -> item {
                Text(
                    text = "No cloud service on this device publishes a folder to other apps. " +
                        "Install the service's own app and it will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            else -> items(state.roots, key = { it.treeUri }) { root ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenRoot(root) },
                ) {
                    Icon(Icons.Filled.Cloud, contentDescription = null)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = root.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        root.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderBrowser(
    state: CloudUiState,
    padding: PaddingValues,
    onOpenDocument: (CloudDocument) -> Unit,
    onUp: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Up one folder")
                }
                Text(
                    text = listOf(state.title.orEmpty()) + state.trail.map { it.name }
                        .joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when {
            state.browsing -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            }

            state.documents.isEmpty() -> item {
                Text(
                    text = "This folder is empty, or nothing in it is shown to other apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            else -> items(state.documents, key = { it.documentId }) { document ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDocument(document) },
                ) {
                    Icon(
                        imageVector = if (document.isDirectory) Icons.Filled.Folder
                        else Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = document.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!document.isDirectory) {
                            Text(
                                text = listOfNotNull(
                                    document.mimeType.takeIf { it.isNotBlank() },
                                    document.sizeBytes.takeIf { it > 0 }
                                        ?.let { MediaFormatUtils.formatBytes(it) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
