package com.progallery.app.ui.screens.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.data.storage.StorageSummary
import com.progallery.app.data.storage.StorageSummaryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.progallery.app.data.model.SortOrder

/** What the storage screen shows. */
data class StorageUiState(
    val summary: StorageSummary? = null,
    val loading: Boolean = true,
)

/**
 * Where the space went.
 *
 * Built from the app's own snapshot and its own trash — the same two sources every other screen
 * reads. **Not** from a walk of the device: the app cannot see the whole device, and a total that
 * claimed to be one would look like an answer while being a fraction of the truth.
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val (items, trash) = withContext(Dispatchers.IO) {
                val all = runCatching {
                    repository.snapshot(
                        kinds = MediaType.entries.filter { it != MediaType.ALL }.toSet(),
                        sort = SortOrder.SIZE_DESC,
                        query = "",
                    )
                }.getOrDefault(emptyList())
                val held = runCatching { trashRepository.list() }.getOrDefault(emptyList())
                all to held
            }
            _state.update {
                it.copy(
                    summary = StorageSummaryBuilder.build(
                        items = items,
                        inTrashBytes = trash.sumOf { entry -> entry.sizeBytes },
                        inTrashCount = trash.size,
                    ),
                    loading = false,
                )
            }
        }
    }
}
