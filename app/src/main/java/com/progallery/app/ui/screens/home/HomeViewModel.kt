package com.progallery.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.domain.MediaFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val photos: Int = 0,
    val videos: Int = 0,
    val albums: Int = 0,
    val favourites: Int = 0,
    val trash: Int = 0,
    val recent: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.refresh() }
            repository.observeMedia(MediaType.ALL).collect { items ->
                _state.update {
                    it.copy(
                        photos = MediaFilters.byType(items, MediaType.IMAGE).size,
                        videos = MediaFilters.byType(items, MediaType.VIDEO).size,
                        albums = items.map { m -> m.bucketId }.distinct().size,
                        favourites = MediaFilters.favourites(items).size,
                        recent = items.take(12),
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            trashRepository.observeTrash().collect { trash ->
                _state.update { it.copy(trash = trash.size) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { runCatching { repository.refresh() } }
    }
}
