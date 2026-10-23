package com.progallery.app.ui.screens.recovery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.model.ViewMode
import com.progallery.app.data.recovery.RecoveryResult
import com.progallery.app.data.recovery.RecoveryScanMode
import com.progallery.app.data.recovery.RecoveryScanState
import com.progallery.app.data.recovery.RecoverySort
import com.progallery.app.data.recovery.RecoveryStatus
import com.progallery.app.data.recovery.RecoveryTab
import com.progallery.app.ui.components.EmptyState
import com.progallery.app.ui.components.ErrorBanner
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.components.RecoveryThumbnail
import com.progallery.app.util.MediaFormatUtils
import com.progallery.app.data.settings.ExportDuplicatePolicy
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.progallery.app.data.eventlog.LogLine

/**
 * Quick Scan, Dumpster-style in layout but honest in content.
 *
 * Every result says where it came from and how sure the scan is, and an indexed
 * file is never presented as a deleted one. Anything the app cannot actually read
 * back is labelled "Preview only".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecoveryScreen(
    onBack: () -> Unit,
    /** Set when the person asked for a scan from the scope screen rather than opening this one. */
    autoStart: Boolean = false,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Once per visit, and only when the route said so. `LaunchedEffect(Unit)` is deliberate: a
    // scan that restarted on every recomposition would be worse than no auto-start at all.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (autoStart) viewModel.startScan()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    // The two questions the settings can ask before anything is written, and the folder the
    // export will go to once they are answered.
    var confirmingExport by remember { mutableStateOf(false) }
    var exportDestination by remember { mutableStateOf<android.net.Uri?>(null) }
    val remembered by viewModel.rememberedDestination.collectAsStateWithLifecycle()
    // A scope the composition owns, rather than a lifecycle one reached through an owner: the work
    // here belongs to this screen being on display.
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.addFolder(it) } }

    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            exportDestination = it
            viewModel.exportSelection(it)
            // The grant is taken so the folder is still openable next time; the name is stored
            // beside it only to show the person where they are sending things.
            scope.launch {
                viewModel.rememberDestination(it, it.lastPathSegment?.substringAfterLast(':') ?: "chosen folder")
            }
        }
    }

    // Where the export goes. Asking every time is the default; reusing the last folder is what the
    // person asked for when they turned that off, and there is nothing to reuse on a first run.
    fun chooseDestination() {
        val reuse = !state.exportAskForDestination
        val known = remembered
        if (reuse && known != null) {
            exportDestination = android.net.Uri.parse(known.treeUri)
            viewModel.exportSelection(android.net.Uri.parse(known.treeUri))
        } else {
            destinationPicker.launch(known?.treeUri?.let { android.net.Uri.parse(it) })
        }
    }

    // Everything that starts an export goes through here, so the confirmation cannot be skipped by
    // whichever button happened to be tapped.
    fun startExport() {
        if (state.exportConfirmBeforeExport) confirmingExport = true else chooseDestination()
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    MediaPermissionGate {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Recovery") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Filled.History, contentDescription = "Export history")
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val scan = state.scanState) {
                    RecoveryScanState.Idle -> IdlePane(
                        selectedMode = state.mode,
                        allowedModes = state.allowedModes,
                        onMode = { viewModel.setMode(it) },
                        onScan = { viewModel.startScan() },
                        onAddFolder = { folderPicker.launch(null) },
                    )

                    is RecoveryScanState.Scanning -> Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(scan.stage, style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Found ${scan.found} so far", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { viewModel.cancelScan() }) {
                            Icon(Icons.Filled.Cancel, contentDescription = null)
                            Text("  Cancel")
                        }
                    }

                    is RecoveryScanState.Failed -> Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ErrorBanner(message = scan.message) { viewModel.startScan() }
                    }

                    is RecoveryScanState.Completed -> CompletedPane(
                        state = state,
                        sortMenuOpen = sortMenuOpen,
                        onSortMenuOpen = { sortMenuOpen = it },
                        viewModel = viewModel,
                        onExport = { startExport() },
                        onAddFolder = { folderPicker.launch(null) },
                    )
                }
            }
        }
    }

    state.previewing?.let { result ->
        PreviewDialog(
            result = result,
            exportAllowed = state.exportEnabled,
            onDismiss = { viewModel.preview(null) },
            onExport = {
                viewModel.clearSelection()
                viewModel.toggleSelection(result)
                viewModel.preview(null)
                startExport()
            },
            onShare = { viewModel.share(result) },
        )
    }

    if (showHistory) {
        HistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistory = false },
        )
    }

    if (confirmingExport) {
        ExportConfirmDialog(
            count = state.selection.size,
            destinationName = remembered?.displayName,
            askForDestination = state.exportAskForDestination,
            onDismiss = { confirmingExport = false },
            onConfirm = {
                confirmingExport = false
                chooseDestination()
            },
        )
    }

    if (state.pendingDecision.isNotEmpty()) {
        NameClashDialog(
            count = state.pendingDecision.size,
            onKeepBoth = {
                exportDestination?.let { viewModel.answerDecision(ExportDuplicatePolicy.RENAME, it) }
            },
            onSkip = {
                exportDestination?.let { viewModel.answerDecision(ExportDuplicatePolicy.SKIP, it) }
            },
            onDismiss = { viewModel.dismissDecision() },
        )
    }
}

/** The confirmation the settings can ask for, so a tap cannot start a copy on its own. */
@Composable
private fun ExportConfirmDialog(
    count: Int,
    destinationName: String?,
    askForDestination: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Export this file?" else "Export $count files?") },
        text = {
            Text(
                buildString {
                    append("They are copied to a folder you choose")
                    if (!askForDestination && destinationName != null) {
                        append(" — last time that was \"$destinationName\"")
                    }
                    append(". The originals are never moved or changed.")
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Choose a folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The question the "Ask me each time" duplicate policy exists to ask. */
@Composable
private fun NameClashDialog(
    count: Int,
    onKeepBoth: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "A file of that name is already there" else "$count files already have those names") },
        text = {
            Text(
                "Nothing has been copied yet. Keeping both adds a number to the new file's name; " +
                    "skipping leaves the folder exactly as it is.",
            )
        },
        confirmButton = { TextButton(onClick = onKeepBoth) { Text("Keep both") } },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun IdlePane(
    selectedMode: RecoveryScanMode,
    allowedModes: Set<RecoveryScanMode>,
    onMode: (RecoveryScanMode) -> Unit,
    onScan: () -> Unit,
    onAddFolder: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(tonalElevation = 1.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Pick a scan", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Quick Scan:\n" +
                            "1. Media the system's index already lists.\n" +
                            "2. Files this app is holding in Recently Deleted.\n" +
                            "3. Files inside folders you granted that the index does not list.\n\n" +
                            "Thumbnail & cache:\n" +
                            "Only thumbnails and cached previews in folders you granted. These are " +
                            "small preview files — never the full-resolution original — and they are " +
                            "exported under a \"-thumbnail\" name so they can never be mistaken for it.\n\n" +
                            "Neither mode reads raw storage or other apps' private folders: Android " +
                            "refuses that to every app without root. A file the system already indexes " +
                            "is shown as indexed, not as a deleted file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RecoveryScanMode.entries.filter { it in allowedModes }.forEach { mode ->
                    FilterChip(
                        onClick = { onMode(mode) },
                        selected = selectedMode == mode,
                        label = { Text(mode.label) },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("  Scan")
                }
                OutlinedButton(onClick = onAddFolder) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text("  Add a folder")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedPane(
    state: RecoveryUiState,
    sortMenuOpen: Boolean,
    onSortMenuOpen: (Boolean) -> Unit,
    viewModel: RecoveryViewModel,
    onExport: () -> Unit,
    onAddFolder: () -> Unit,
) {
    val summary = (state.scanState as? RecoveryScanState.Completed)?.summary

    Column(modifier = Modifier.fillMaxSize()) {
        if (summary != null) {
            Surface(tonalElevation = 1.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(state.mode.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${state.results.size} result(s) · ${summary.indexedFound} indexed · " +
                            "${summary.trashFound} in Recently Deleted · ${summary.unindexedFound} unindexed · " +
                            "${summary.foldersScanned} folder(s) scanned",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (summary.skippedAlreadyIndexed > 0) {
                        Text(
                            text = "${summary.skippedAlreadyIndexed} file(s) skipped because the index already lists them.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    summary.errors.forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecoveryTab.entries.forEach { tab ->
                FilterChip(
                    onClick = { viewModel.setTab(tab) },
                    selected = state.tab == tab,
                    label = { Text(tab.label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search results") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { onSortMenuOpen(true) }) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { onSortMenuOpen(false) }) {
                    RecoverySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            onClick = { viewModel.setSort(sort); onSortMenuOpen(false) },
                        )
                    }
                }
            }
            IconButton(onClick = {
                viewModel.setViewMode(
                    if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID,
                )
            }) {
                Icon(
                    imageVector = if (state.viewMode == ViewMode.GRID) {
                        Icons.Filled.ViewList
                    } else {
                        Icons.Filled.GridView
                    },
                    contentDescription = "Toggle view",
                )
            }
        }

        if (state.selectionMode) {
            SelectionBar(
                count = state.selection.size,
                exportable = state.exportableSelection.size,
                exporting = state.exporting,
                exportAllowed = state.exportEnabled,
                onExport = onExport,
                onShare = {
                    state.selectedResults.firstOrNull()?.let { viewModel.share(it) }
                },
                onSelectAll = { viewModel.selectAllVisible() },
                onClose = { viewModel.clearSelection() },
            )
        }

        when {
            state.visible.isEmpty() -> EmptyState(
                icon = Icons.Filled.Search,
                title = "Nothing in this tab",
                subtitle = "Try another tab, or clear the search.",
            )

            state.viewMode == ViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.visible, key = { it.id }) { result ->
                    RecoveryThumbnail(
                        result = result,
                        policy = state.thumbnailPolicy,
                        showName = true,
                        showSize = true,
                        modifier = Modifier.combinedClickable(
                            onClick = { viewModel.preview(result) },
                            onLongClick = { viewModel.toggleSelection(result) },
                        ),
                    )
                }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.visible, key = { it.id }) { result ->
                    ResultRow(
                        result = result,
                        onClick = { viewModel.preview(result) },
                        onLongClick = { viewModel.toggleSelection(result) },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onAddFolder,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Text("  Add another folder to search")
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    exportable: Int,
    exporting: Boolean,
    exportAllowed: Boolean,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count selected", style = MaterialTheme.typography.titleSmall)
                Box(modifier = Modifier.weight(1f))
                TextButton(onClick = onSelectAll) { Text("Select all") }
                TextButton(onClick = onClose) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExport,
                    enabled = exportable > 0 && !exporting && exportAllowed,
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text("  Export $exportable")
                }
                OutlinedButton(onClick = onShare, enabled = count == 1) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Share")
                }
            }
            if (exportable < count) {
                Text(
                    text = "${count - exportable} selected item(s) could not be read, so they cannot be exported.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(result: RecoveryResult, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecoveryThumbnail(
            result = result,
            modifier = Modifier.size(56.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = result.item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${result.source.label} · ${result.confidence.label} confidence · " +
                    "${result.status.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.item.sizeBytes > 0) {
                Text(
                    text = MediaFormatUtils.formatBytes(result.item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewDialog(
    result: RecoveryResult,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    exportAllowed: Boolean,
) {
    val item = result.item
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecoveryThumbnail(
                    result = result,
                    modifier = Modifier.size(160.dp),
                )
                Text("Source: ${result.source.label}", style = MaterialTheme.typography.bodySmall)
                Text("Found in: ${result.originLabel}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Confidence: ${result.confidence.label} — ${result.evidence}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Status: ${result.status.label}", style = MaterialTheme.typography.bodySmall)

                // What is provably wrong with it, and what proved it. Null means the check was
                // switched off, which is not the same as "nothing wrong" — so it says nothing
                // rather than claiming the file is intact.
                result.integrity?.takeIf { it.suspicious }?.let { report ->
                    Text(
                        text = report.issues.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Evidence: ${report.evidence}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                result.integrity?.takeIf { !it.suspicious && it.actualFormat != null }?.let { report ->
                    Text(
                        text = "Contents check out: ${report.actualFormat}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                result.exif?.let { exif ->
                    val line = buildList {
                        exif.takenAt?.let { millis ->
                            add("Taken ${LogLine.isoTimestamp(millis).substringBefore('T')}")
                        }
                        val camera = listOfNotNull(exif.cameraMake, exif.cameraModel).joinToString(" ")
                        if (camera.isNotBlank()) add(camera)
                    }.joinToString(" · ")
                    if (line.isNotBlank()) {
                        Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (result.isThumbnail) {
                    Text(
                        text = "This is a thumbnail, not the original image. Exporting saves the " +
                            "thumbnail itself, named \"...-thumbnail\".",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("Type: ${item.mediaType.name} · ${item.mimeType}", style = MaterialTheme.typography.bodySmall)
                Text("Size: ${MediaFormatUtils.formatBytes(item.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                if (item.dimensionLabel != "—") {
                    Text("Dimensions: ${item.dimensionLabel}", style = MaterialTheme.typography.bodySmall)
                }
                if (item.durationMs > 0) {
                    Text(
                        "Duration: ${MediaFormatUtils.formatDuration(item.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Modified: ${MediaFormatUtils.formatDate(item.dateModifiedSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onExport, enabled = result.canExport && exportAllowed) { Text("Export") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun HistoryDialog(viewModel: RecoveryViewModel, onDismiss: () -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export history") },
        text = {
            if (history.isEmpty()) {
                Text("Nothing has been exported yet.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().width(300.dp)) {
                    items(history, key = { it.id }) { entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${entry.outcome} · ${entry.sourceLabel} → ${entry.destinationLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            entry.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { viewModel.clearHistory() }) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("  Clear history")
            }
        },
    )
}
