package com.progallery.app.ui.screens.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.Album
import com.progallery.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
) {
    val visible: List<Album>
        get() = if (query.isBlank()) albums
        else albums.filter { it.name.contains(query, ignoreCase = true) }
}

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumsUiState())
    val uiState: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { runCatching { repository.refresh() } }
        viewModelScope.launch {
            repository.observeAlbums().collect { albums ->
                _state.value = _state.value.copy(albums = albums, loading = false)
            }
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }
}
