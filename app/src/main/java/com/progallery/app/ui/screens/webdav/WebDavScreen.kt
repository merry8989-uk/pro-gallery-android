package com.progallery.app.ui.screens.webdav

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.webdav.WebDavEntry
import com.progallery.app.data.webdav.WebDavServer
import com.progallery.app.util.MediaFormatUtils

/**
 * Browse a WebDAV server.
 *
 * This is the cloud browsing that works without anyone registering anything: WebDAV is an
 * open protocol, so a Nextcloud, ownCloud, Box or NAS address is enough. What it does not
 * do is stated on the screen — Basic authentication only, and a self-signed certificate is
 * refused rather than waved through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavScreen(
    onBack: () -> Unit,
    onPlay: (serverId: String, url: String) -> Unit,
    viewModel: WebDavViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.play.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(pending) {
        pending?.let { (serverId, url) ->
            onPlay(serverId, url)
            viewModel.consumePlay()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.openServer?.name ?: "WebDAV") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (state.openServer != null) viewModel.closeServer() else onBack() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.openServer != null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                        }
                    } else {
                        IconButton(onClick = { adding = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add a server")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.openServer == null) {
            ServerList(
                servers = state.servers,
                padding = padding,
                onOpen = viewModel::openServer,
                onRemove = viewModel::removeServer,
                onAdd = { adding = true },
            )
        } else {
            Browser(
                state = state,
                padding = padding,
                onOpenEntry = viewModel::openEntry,
                onUp = viewModel::goUp,
            )
        }
    }

    if (adding) {
        AddServerDialog(
            onDismiss = { adding = false },
            onAdd = { name, url, user, pass ->
                viewModel.addServer(name, url, user, pass)
                adding = false
            },
        )
    }
}

@Composable
private fun ServerList(
    servers: List<WebDavServer>,
    padding: PaddingValues,
    onOpen: (WebDavServer) -> Unit,
    onRemove: (WebDavServer) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "Add a WebDAV address — a Nextcloud, ownCloud or NAS folder. " +
                    "Basic sign-in only, and a self-signed certificate is refused rather " +
                    "than waved through. The password is kept in this app's private storage " +
                    "and never leaves it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (servers.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No servers added yet.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Add a server")
                    }
                }
            }
        }
        items(servers, key = { it.id }) { server ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(server) },
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(server.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemove(server) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
private fun Browser(
    state: WebDavUiState,
    padding: PaddingValues,
    onOpenEntry: (WebDavEntry) -> Unit,
    onUp: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.canGoUp) {
                    IconButton(onClick = onUp) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Up one folder")
                    }
                }
                Text(
                    text = state.folderUrl.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when {
            state.loading -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> item {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            state.entries.isEmpty() -> item {
                Text(
                    text = "This folder is empty.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            else -> items(state.entries, key = { it.url }) { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(entry) },
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!entry.isDirectory) {
                            Text(
                                text = listOfNotNull(
                                    entry.mimeType,
                                    entry.sizeBytes.takeIf { it > 0 }
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
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, username: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a WebDAV server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address") },
                    placeholder = { Text("https://cloud.example.com/remote.php/dav/files/me/") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, url, username, password) }, enabled = url.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
