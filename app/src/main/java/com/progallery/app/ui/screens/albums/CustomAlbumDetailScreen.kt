package com.progallery.app.ui.screens.albums

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.ViewMode
import com.progallery.app.data.repository.AlbumRepository
import com.progallery.app.ui.components.EmptyState
import com.progallery.app.ui.components.MediaCollection
import com.progallery.app.ui.components.MediaPermissionGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomAlbumViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private var loadedAlbumId: Long? = null

    fun load(albumId: Long) {
        if (loadedAlbumId == albumId) return
        loadedAlbumId = albumId
        viewModelScope.launch {
            albumRepository.observeAlbumItems(albumId).collect { _items.value = it }
        }
    }

    fun removeFromAlbum(albumId: Long, mediaKey: String) = viewModelScope.launch {
        albumRepository.removeFromAlbum(albumId, mediaKey)
        _message.value = "Removed from album"
    }

    fun setCover(albumId: Long, uri: Uri) = viewModelScope.launch {
        albumRepository.setCover(albumId, uri)
        _message.value = "Album cover updated"
    }

    fun deleteAlbum(albumId: Long) = viewModelScope.launch {
        albumRepository.deleteAlbum(albumId)
        _deleted.value = true
    }

    fun consumeMessage() { _message.value = null }
}

/**
 * Detail view for a user-created album: browse its contents, set a cover, or
 * remove an item. Tap opens the viewer; long-press opens the item actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAlbumDetailScreen(
    albumId: Long,
    albumName: String,
    onBack: () -> Unit,
    onOpenViewer: (String, String) -> Unit,
    viewModel: CustomAlbumViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var actionTarget by remember { mutableStateOf<MediaItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(albumId) { viewModel.load(albumId) }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(albumName.ifBlank { "Album" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete album")
                    }
                },
            )
        },
    ) { padding ->
        MediaPermissionGate {
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.PhotoAlbum,
                    title = "Album is empty",
                    subtitle = "Select photos in the gallery and use \"Add to album\".",
                    modifier = Modifier.padding(padding),
                )
            } else {
                MediaCollection(
                    items = items,
                    columns = 3,
                    viewMode = ViewMode.GRID,
                    selectionMode = false,
                    selectedIds = emptySet(),
                    onClick = { onOpenViewer(it.uri.toString(), it.mediaType.name) },
                    onLongClick = { actionTarget = it },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(target.displayName, maxLines = 1) },
            text = { Text("What would you like to do with this item?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCover(albumId, target.uri)
                    actionTarget = null
                }) { Text("Set as cover") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.removeFromAlbum(albumId, target.mediaKey)
                    actionTarget = null
                }) { Text("Remove from album") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete album?") },
            text = { Text("This removes the album only. Your photos and videos are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAlbum(albumId)
                }) { Text("Delete album") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
