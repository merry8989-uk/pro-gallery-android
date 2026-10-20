package com.progallery.app.ui.screens.common

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.data.model.ViewMode
import com.progallery.app.ui.components.AddToAlbumDialog
import com.progallery.app.ui.components.EmptyState
import com.progallery.app.ui.components.ErrorBanner
import com.progallery.app.ui.components.LoadingState
import com.progallery.app.ui.components.MediaCollection
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.components.SelectionToolbar

/**
 * Standard scaffold used by Photos, Videos, Favourites and Search. Owns the
 * top bar (sort + density), the grid/list, the selection toolbar, the delete
 * consent flow and the snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserScaffold(
    title: String,
    viewModel: MediaBrowserViewModel,
    onBack: (() -> Unit)? = null,
    onOpenItem: (MediaItem) -> Unit,
    onEditItem: ((MediaItem) -> Unit)? = null,
    showSearch: Boolean = false,
    emptyTitle: String = "Nothing here yet",
    emptySubtitle: String = "Photos and videos you add will appear here.",
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let { intent ->
            deleteLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    var sortMenuOpen by remember { mutableStateOf(false) }
    var albumPickerOpen by remember { mutableStateOf(false) }
    val customAlbums by viewModel.customAlbums.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!state.selectionMode) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setViewMode(if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }) {
                            Icon(
                                imageVector = if (state.viewMode == ViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                                contentDescription = "Toggle view",
                            )
                        }
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        viewModel.setSort(order)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                SelectionToolbar(
                    count = state.selection.size,
                    allFavourite = state.selectedItems.all { it.isFavourite },
                    onClose = { viewModel.clearSelection() },
                    onShare = { viewModel.shareSelected() },
                    onFavourite = { viewModel.favouriteSelected() },
                    onEdit = {
                        state.selectedItems.firstOrNull()?.let { onEditItem?.invoke(it) }
                        viewModel.clearSelection()
                    },
                    onAddToAlbum = { albumPickerOpen = true },
                    onDelete = { viewModel.deleteSelected() },
                    onHide = { viewModel.hideSelected() },
                )
            }
        },
    ) { padding ->
        if (albumPickerOpen) {
            AddToAlbumDialog(
                albums = customAlbums,
                onDismiss = { albumPickerOpen = false },
                onPickAlbum = { albumId ->
                    viewModel.addSelectedToAlbum(albumId)
                    albumPickerOpen = false
                },
                onCreateAndAdd = { name ->
                    viewModel.createAlbumAndAdd(name)
                    albumPickerOpen = false
                },
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showSearch) {
                androidx.compose.material3.OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search by name, album or type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            when {
                state.error != null -> ErrorBanner(state.error!!) { viewModel.refresh() }
                state.loading -> LoadingState()
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Filled.GridView,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                )
                else -> MediaCollection(
                    items = state.items,
                    columns = state.columns,
                    viewMode = state.viewMode,
                    selectionMode = state.selectionMode,
                    selectedIds = state.selection,
                    onClick = { item ->
                        when {
                            state.selectionMode -> viewModel.toggleSelection(item)
                            // Images and videos preview, audio plays in the
                            // in-app player; documents/archives/unknown types
                            // are handed to the system.
                            item.isPreviewable || item.isAudio -> onOpenItem(item)
                            else -> viewModel.openWith(item)
                        }
                    },
                    onLongClick = { viewModel.toggleSelection(it) },
                )
            }
        }
    }
}
