package com.progallery.app.ui.screens.tree

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.tree.TreeNode
import com.progallery.app.data.tree.TreeRow
import com.progallery.app.ui.components.MediaThumbnail
import com.progallery.app.util.MediaFormatUtils
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import com.progallery.app.ui.components.MediaPermissionGate
import androidx.compose.material3.ExperimentalMaterial3Api
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * The file tree.
 *
 * One long indented column rather than a nested layout, because that is what a file manager's list
 * is — and because a list can be lazily drawn, which a tree of nested rows cannot be without being
 * flattened first anyway.
 *
 * Every row is a folder or a file, and **every file opens through the same dispatch as everywhere
 * else** (`FileOpener`), so a format that has a viewer here has one there and a format that is
 * handed over says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeScreen(
    onBack: () -> Unit,
    onOpenFile: (MediaItem) -> Unit,
    viewModel: FileTreeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The system's own dialogs. The app never deletes or renames a file by itself: on Android 11
    // and up it *cannot*, and on older versions it should not without being asked.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { viewModel.consumeMessage() }
    val renameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onRenameConsent(result.resultCode == Activity.RESULT_OK) }

    var menuFor by remember { mutableStateOf<MediaItem?>(null) }
    var pendingRename by remember { mutableStateOf<MediaItem?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(pendingDelete) {
        pendingDelete?.let { item ->
            viewModel.deleteRequest(item)?.let { request ->
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } ?: Toast.makeText(
                context,
                "This Android version does not ask before deleting.",
                Toast.LENGTH_LONG,
            ).show()
            pendingDelete = null
        }
    }
    LaunchedEffect(pendingRename) {
        pendingRename?.let { item ->
            viewModel.renameRequest(item)?.let { request ->
                renameLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } ?: viewModel.onRenameConsent(granted = true)
            pendingRename = null
        }
    }

    MediaPermissionGate {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("File tree") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // What the tree adds up to, which is the first thing anybody looks for in a file
                // manager and the one number a tree can give that a grid cannot.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.itemCount} files · ${MediaFormatUtils.formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        TextButton(onClick = viewModel::collapseAll) { Text("Collapse") }
                        TextButton(onClick = viewModel::expandAll) { Text("Expand") }
                    }
                }
                HorizontalDivider()

                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    state.rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nothing to show. The app lists what it can read — the Access screen says " +
                                "what it has been allowed to reach.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.rows, key = { it.node.path }) { row ->
                            TreeRowItem(
                                row = row,
                                collapsed = row.node.path in state.collapsed,
                                onToggle = { viewModel.toggle(row.node.path) },
                                onOpen = onOpenFile,
                                onLongPress = { item -> menuFor = item },
                            )
                        }
                        }
                    }
                }
        }
    }

    // The operations, where the files are. They reuse the calls the viewer already uses rather than
    // a second implementation, because two implementations of "delete a file" is how one of them
    // ends up wrong.
    menuFor?.let { item ->
        DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuFor = null
                    viewModel.startRename(item)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuFor = null
                    pendingDelete = item
                },
            )
        }
    }

    state.renaming?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRename() },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = state.renameText,
                    onValueChange = viewModel::setRenameText,
                    singleLine = true,
                    label = { Text("New name") },
                )
            },
            confirmButton = { TextButton(onClick = { pendingRename = item }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelRename() }) { Text("Cancel") } },
        )
    }

    // Said on the screen rather than silently, because a rename that did not go through and a
    // rename that did look exactly the same otherwise.
    state.message?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRowItem(
    row: TreeRow,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onLongPress: (MediaItem) -> Unit,
) {
    val node = row.node
    val isFolder = node is TreeNode.Folder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The indent is the hierarchy. Capped, so a path twenty deep does not push its name off
            // the screen — a file manager that cannot show you the name has failed at its one job.
            .padding(start = (row.depth.coerceAtMost(8) * 16).dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            .combinedClickable(
                onClick = { if (isFolder) onToggle() else onOpen((node as TreeNode.File).item) },
                // Long-press is where a file manager keeps its operations, and where the gallery
                // already keeps selection — so the gesture means the same thing here.
                onLongClick = { if (!isFolder) onLongPress((node as TreeNode.File).item) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFolder) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (collapsed) "Closed" else "Open",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp).padding(start = 2.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            // A real thumbnail where the app can make one, so the tree is not a list of grey icons.
            MediaThumbnail(
                item = (node as TreeNode.File).item,
                selected = false,
                selectionMode = false,
                onClick = { onOpen(node.item) },
                onLongClick = { onOpen(node.item) },
                modifier = Modifier.size(28.dp),
            )
        }

        Column(modifier = Modifier.padding(start = 10.dp).fillMaxWidth()) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isFolder) {
                    "${node.itemCount} files · ${MediaFormatUtils.formatBytes(node.sizeBytes)}"
                } else {
                    MediaFormatUtils.formatBytes(node.sizeBytes)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
