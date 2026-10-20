package com.progallery.app.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.local.entity.TrashEntity
import com.progallery.app.data.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val items: List<TrashEntity> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { trashRepository.purgeExpired(30) }
        viewModelScope.launch {
            trashRepository.observeTrash().collect { items ->
                _state.update { it.copy(items = items) }
            }
        }
    }

    fun restore(entity: TrashEntity) = viewModelScope.launch {
        val uri = trashRepository.restore(entity)
        _state.update {
            it.copy(message = if (uri != null) "Restored ${entity.displayName}" else "Could not restore ${entity.displayName}")
        }
    }

    fun deleteForever(entity: TrashEntity) = viewModelScope.launch {
        trashRepository.deleteForever(entity)
        _state.update { it.copy(message = "Deleted ${entity.displayName}") }
    }

    fun emptyTrash() = viewModelScope.launch {
        val count = _state.value.items.size
        _state.value.items.forEach { trashRepository.deleteForever(it) }
        _state.update { it.copy(message = "Emptied $count item(s)") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
