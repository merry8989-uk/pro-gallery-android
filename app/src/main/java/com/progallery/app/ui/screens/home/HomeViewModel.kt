package com.progallery.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.domain.MediaFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.progallery.app.util.runCatchingCancellable

data class HomeUiState(
    val photos: Int = 0,
    val videos: Int = 0,
    val audio: Int = 0,
    val documents: Int = 0,
    val other: Int = 0,
    val allFiles: Int = 0,
    val albums: Int = 0,
    val favourites: Int = 0,
    val trash: Int = 0,
    val recent: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
    /** Offered once, on first launch. */
    val showDefaultGalleryPrompt: Boolean = false,
    /**
     * Categories the user has left switched on. Home hides the entry point for
     * anything switched off, so it never offers a folder that must be empty.
     */
    val enabledKinds: Set<MediaType> = MediaType.entries.toSet(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val trashRepository: TrashRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatchingCancellable { repository.refresh() }
            repository.observeMedia(MediaType.ALL).collect { items ->
                _state.update {
                    it.copy(
                        photos = items.count { m -> m.mediaType == MediaType.IMAGE },
                        videos = items.count { m -> m.mediaType == MediaType.VIDEO },
                        audio = items.count { m -> m.mediaType == MediaType.AUDIO },
                        documents = items.count { m -> m.mediaType == MediaType.DOCUMENT },
                        other = items.count {
                            m -> m.mediaType == MediaType.ARCHIVE || m.mediaType == MediaType.OTHER
                        },
                        allFiles = items.size,
                        albums = items.map { m -> m.bucketId }.distinct().size,
                        favourites = MediaFilters.favourites(items).size,
                        // Only previewable items go in the "Recent" grid; the
                        // rest have no thumbnail.
                        recent = items.filter { m -> m.isPreviewable }.take(12),
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update {
                    it.copy(
                        showDefaultGalleryPrompt = !settings.hasSeenDefaultGalleryPrompt,
                        enabledKinds = settings.enabledKinds,
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
        viewModelScope.launch { runCatchingCancellable { repository.refresh() } }
    }

    /** Remembers that the one-time prompt has been shown. */
    fun dismissDefaultGalleryPrompt() {
        viewModelScope.launch { settingsRepository.markDefaultGalleryPromptSeen() }
    }
}
