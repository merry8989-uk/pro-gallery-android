package com.progallery.app.ui.screens.cloud

import android.content.Intent
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.progallery.app.data.cloud.CloudDocument
import com.progallery.app.data.cloud.CloudKind
import com.progallery.app.data.cloud.GrantedCloudTree
import com.progallery.app.util.MediaFormatUtils

/**
 * Cloud folders, browsed inside the app.
 *
 * **How a folder gets here.** The user chooses one through the system's own folder picker.
 * That picker's sidebar is where every installed service appears — Drive, TeraBox, WPS
 * Office, Zoho WorkDrive, a NAS — because that listing belongs to the platform, not to this
 * app. Choosing a folder is a **one-time** step: the platform persists the grant, and the
 * service keeps its own account and session. The app never receives a password and never
 * signs anything in, which is precisely why there is nothing to sign into again later.
 *
 * From then on the folder opens straight away, and everything is browsed and opened without
 * leaving the app.
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

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Persisted, which is what makes this the only time the user is asked.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.folderChosen(uri.toString())
        }
    }

    LaunchedEffect(pending) {
        pending?.let { request ->
            onOpen(request.uri, request.kind)
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
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "Choose a cloud folder")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.title == null) {
            FolderList(
                state = state,
                padding = padding,
                onChoose = { folderPicker.launch(null) },
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
}

@Composable
private fun FolderList(
    state: CloudUiState,
    padding: PaddingValues,
    onChoose: () -> Unit,
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
                text = "Choose a folder from any service installed on this device — Drive, " +
                    "TeraBox, WPS Office, a NAS. The service keeps its own sign-in, so the app " +
                    "never asks for a password and there is nothing to log into here. A folder " +
                    "you choose once stays chosen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            Button(
                onClick = onChoose,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                Text("  Choose a cloud folder")
            }
        }
        if (state.opening) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            }
        }

        if (state.granted.isEmpty()) {
            item {
                Text(
                    text = "No cloud folder has been chosen yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
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
                    text = (listOf(state.title.orEmpty()) + state.trail.map { it.name })
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
