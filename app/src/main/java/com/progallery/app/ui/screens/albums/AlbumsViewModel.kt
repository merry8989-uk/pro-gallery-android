package com.progallery.app.ui.screens.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.Album
import com.progallery.app.data.repository.AlbumRepository
import com.progallery.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.progallery.app.util.runCatchingCancellable

data class AlbumsUiState(
    val folders: List<Album> = emptyList(),
    val custom: List<Album> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val message: String? = null,
) {
    private fun matches(album: Album) =
        query.isBlank() || album.name.contains(query, ignoreCase = true)

    /** Folder-derived albums, filtered by the search box. */
    val visibleFolders: List<Album> get() = folders.filter(::matches)

    /** User-created albums, filtered by the search box. */
    val visibleCustom: List<Album> get() = custom.filter(::matches)
}

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumsUiState())
    val uiState: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { runCatchingCancellable { repository.refresh() } }
        viewModelScope.launch {
            repository.observeAlbums().collect { albums ->
                _state.update { it.copy(folders = albums, loading = false) }
            }
        }
        viewModelScope.launch {
            albumRepository.observeCustomAlbums().collect { albums ->
                _state.update { it.copy(custom = albums) }
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun createAlbum(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            albumRepository.createAlbum(name)
            _state.update { it.copy(message = "Created \"$name\"") }
        }
    }

    fun deleteAlbum(albumId: Long, name: String) {
        viewModelScope.launch {
            albumRepository.deleteAlbum(albumId)
            _state.update { it.copy(message = "Deleted \"$name\"") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
