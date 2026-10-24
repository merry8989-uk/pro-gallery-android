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
@Composable
fun FileTreeScreen(
    onBack: () -> Unit,
    onOpenFile: (MediaItem) -> Unit,
    viewModel: FileTreeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                            )
                        }
                        }
                    }
                }
        }
    }
}

@Composable
private fun TreeRowItem(
    row: TreeRow,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    val node = row.node
    val isFolder = node is TreeNode.Folder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The indent is the hierarchy. Capped, so a path twenty deep does not push its name off
            // the screen — a file manager that cannot show you the name has failed at its one job.
            .padding(start = (row.depth.coerceAtMost(8) * 16).dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            .clickable { if (isFolder) onToggle() else onOpen((node as TreeNode.File).item) },
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
