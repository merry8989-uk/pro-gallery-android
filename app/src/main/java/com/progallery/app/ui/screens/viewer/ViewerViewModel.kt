package com.progallery.app.ui.screens.viewer

import android.app.PendingIntent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A rename or move that may need the platform's write-consent before it applies. */
sealed interface WriteAction {
    data class Rename(val item: MediaItem, val newName: String) : WriteAction
    data class Move(val item: MediaItem, val targetRelativePath: String) : WriteAction
}

/** A consent request plus the change to run once the user approves it. */
data class PendingWrite(val request: PendingIntent, val action: WriteAction)

data class ViewerUiState(
    val pendingDelete: PendingIntent? = null,
    val pendingWrite: PendingWrite? = null,
    val pendingTrashIds: List<Long> = emptyList(),
    val message: String? = null,
)

/**
 * Backs the full-screen viewer's actions. Deleting, renaming and moving all go
 * through the platform consent flow on Android 11+, so the user approves each
 * change; on older versions the app applies what it is permitted to apply and
 * says so plainly when it cannot.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val trashRepository: TrashRepository,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _state = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMedia(MediaType.ALL).collect { _items.value = it }
        }
        refreshFolders()
    }

    private fun refreshFolders() {
        viewModelScope.launch { _folders.value = repository.folders() }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.refresh() }
            refreshFolders()
        }
    }

    fun share(item: MediaItem) = mediaActions.launchShare(listOf(item))

    fun toggleFavourite(item: MediaItem) {
        viewModelScope.launch {
            repository.toggleFavourite(item)
            _state.update {
                it.copy(message = if (item.isFavourite) "Removed from favourites" else "Added to favourites")
            }
        }
    }

    /** Recoverable delete: copy into the trash first, then remove the original. */
    fun requestDelete(item: MediaItem) {
        viewModelScope.launch {
            val trashed = trashRepository.moveToTrash(listOf(item))
            if (trashed.isEmpty()) {
                _state.update {
                    it.copy(message = "Could not move to Recently Deleted — nothing was deleted")
                }
                return@launch
            }
            val request = mediaActions.deleteRequest(listOf(item))
            if (request != null) {
                _state.update {
                    it.copy(pendingDelete = request, pendingTrashIds = trashed.map { it.trashId })
                }
            } else {
                val ok = mediaActions.deleteDirect(listOf(item))
                if (ok) {
                    _state.update { it.copy(message = "Moved to Recently Deleted") }
                } else {
                    trashRepository.discard(trashed.map { it.trashId })
                    _state.update { it.copy(message = "Could not delete — nothing was changed") }
                }
                refresh()
            }
        }
    }

    fun onDeleteConsent(approved: Boolean) {
        val trashIds = _state.value.pendingTrashIds
        _state.update { it.copy(pendingDelete = null, pendingTrashIds = emptyList()) }
        if (approved) {
            _state.update { it.copy(message = "Moved to Recently Deleted") }
            refresh()
        } else {
            viewModelScope.launch {
                trashRepository.discard(trashIds)
                _state.update { it.copy(message = "Deletion cancelled — nothing was changed") }
            }
        }
    }

    fun requestRename(item: MediaItem, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == item.displayName) return
        val action = WriteAction.Rename(item, trimmed)
        val request = mediaActions.renameRequest(item.uri)
        if (request != null) {
            _state.update { it.copy(pendingWrite = PendingWrite(request, action)) }
        } else {
            applyWrite(action)
        }
    }

    fun requestMove(item: MediaItem, targetRelativePath: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _state.update { it.copy(message = "Moving files needs Android 11 or newer") }
            return
        }
        if (targetRelativePath.isBlank()) return
        val action = WriteAction.Move(item, targetRelativePath)
        val request = mediaActions.writeRequest(listOf(item.uri))
        if (request != null) {
            _state.update { it.copy(pendingWrite = PendingWrite(request, action)) }
        } else {
            applyWrite(action)
        }
    }

    fun onWriteConsent(approved: Boolean) {
        val pending = _state.value.pendingWrite
        _state.update { it.copy(pendingWrite = null) }
        if (approved && pending != null) applyWrite(pending.action)
    }

    private fun applyWrite(action: WriteAction) {
        when (action) {
            is WriteAction.Rename -> {
                val ok = mediaActions.applyRename(action.item.uri, action.newName)
                _state.update {
                    it.copy(message = if (ok) "Renamed to ${action.newName}" else "Rename failed")
                }
            }
            is WriteAction.Move -> {
                val ok = mediaActions.applyMove(action.item, action.targetRelativePath)
                _state.update {
                    it.copy(
                        message = if (ok) "Moved to ${action.targetRelativePath}" else "Move failed",
                    )
                }
            }
        }
        refresh()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
