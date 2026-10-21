package com.progallery.app.ui.screens.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.memories.Memories
import com.progallery.app.data.memories.MemoriesRepository
import com.progallery.app.data.memories.MemoryGroup
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.ViewerContext
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.data.settings.ThumbnailPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoriesUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val memories: Memories = Memories(emptyList(), emptyList()),
    val thumbnailPolicy: ThumbnailPolicy = ThumbnailPolicy(),
    val error: String? = null,
) {
    /** The newest anniversary, which the header leads with. */
    val headline: MemoryGroup? get() = memories.onThisDay.firstOrNull()
}

/** A picture the user tapped, ready to be opened in the viewer. */
data class MemoryOpenRequest(val uri: String, val type: MediaType)

/**
 * Shows the pictures worth seeing again today.
 *
 * The whole library is read once and matched in memory. That is deliberate: a memory is a
 * question about *dates*, and asking MediaStore for "pictures taken on 5 April in any year
 * except this one" is not a query it can answer well — the dates are not indexed that way. The
 * library is already read for the rest of the app, so this reuses that work.
 */
@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val memoriesRepository: MemoriesRepository,
    private val viewerContext: ViewerContext,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MemoriesUiState())
    val state: StateFlow<MemoriesUiState> = _state.asStateFlow()

    private val _open = MutableStateFlow<MemoryOpenRequest?>(null)
    val open: StateFlow<MemoryOpenRequest?> = _open.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(thumbnailPolicy = ThumbnailPolicy.from(settings)) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val today = LocalDate.now()
            runCatching { memoriesRepository.forDate(today) }.fold(
                onSuccess = { memories ->
                    _state.update { it.copy(loading = false, today = today, memories = memories) }
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            today = today,
                            error = failure.message ?: "The library could not be read.",
                        )
                    }
                },
            )
        }
    }

    /**
     * Opens one picture, with the rest of its memory behind it.
     *
     * The viewer's swipe list is set to **this memory's pictures**, not the whole library, so
     * swiping through a memory stays inside it — which is the point of the memory.
     */
    fun open(group: MemoryGroup, item: MediaItem) {
        viewerContext.set(group.items)
        _open.value = MemoryOpenRequest(item.uri.toString(), item.mediaType)
    }

    fun consumeOpen() {
        _open.value = null
    }
}
