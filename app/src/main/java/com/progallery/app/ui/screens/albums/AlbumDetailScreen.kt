package com.progallery.app.ui.screens.albums

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.ViewMode
import com.progallery.app.data.repository.MediaRepository
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
class AlbumDetailViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    fun load(bucketId: Long) {
        viewModelScope.launch {
            repository.observeMedia(MediaType.ALL).collect { all ->
                _items.value = all.filter { it.bucketId == bucketId }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    bucketId: Long,
    albumName: String,
    onBack: () -> Unit,
    onOpenViewer: (String, String) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    LaunchedEffect(bucketId) { viewModel.load(bucketId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumName.ifBlank { "Album" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        MediaPermissionGate {
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    title = "Album is empty",
                    subtitle = "No media found in this folder.",
                    modifier = Modifier.padding(padding),
                )
            } else {
                MediaCollection(
                    items = items,
                    columns = 3,
                    viewMode = ViewMode.GRID,
                    selectionMode = false,
                    selectedIds = emptySet<String>(),
                    onClick = { onOpenViewer(it.uri.toString(), it.mediaType.name) },
                    onLongClick = {},
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }
}
