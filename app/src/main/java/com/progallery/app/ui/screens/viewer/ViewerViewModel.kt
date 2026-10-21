package com.progallery.app.ui.screens.viewer

import android.app.PendingIntent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.data.repository.ViewerContext
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.progallery.app.ui.components.sameUri
import com.progallery.app.util.runCatchingCancellable

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
    private val viewerContext: ViewerContext,
) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _state = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var collectionJob: Job? = null

    init {
        refreshFolders()
    }

    /**
     * Sets what the pager may swipe through.
     *
     * [type] is the tapped item's own kind, so opening a photo can never slide
     * into a video. When the browser handed us the on-screen list we use exactly
     * that (same items, same order); otherwise we fall back to the library,
     * still limited to this kind.
     */
    fun configure(uri: String, type: MediaType) {
        collectionJob?.cancel()

        val onScreen = ViewerPager.itemsFor(viewerContext.current(), type)
        if (onScreen.any { sameUri(it.uri.toString(), uri) }) {
            _items.value = onScreen
            return
        }

        collectionJob = viewModelScope.launch {
            repository.observeMedia(type).collect { items ->
                _items.value = ViewerPager.itemsFor(items, type)
            }
        }
    }

    private fun refreshFolders() {
        viewModelScope.launch { _folders.value = repository.folders() }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatchingCancellable { repository.refresh() }
            refreshFolders()
        }
    }

    fun share(item: MediaItem) = mediaActions.launchShare(listOf(item))

    /**
     * Advanced editing: hands the file to an installed editing app. The in-app
     * editor stays for the basic adjustments.
     */
    fun advancedEdit(item: MediaItem) {
        val opened = mediaActions.launchAdvancedEdit(item.uri, item.mimeType)
        if (!opened) {
            _state.update {
                it.copy(message = "No installed app can edit ${item.displayName}")
            }
        }
    }

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
            val outcome = trashRepository.moveToTrash(listOf(item))
            if (outcome.succeeded.isEmpty()) {
                _state.update {
                    it.copy(
                        message = "Could not copy to Recently Deleted, so nothing was deleted. " +
                            outcome.explain(),
                    )
                }
                return@launch
            }
            val request = mediaActions.deleteRequest(listOf(item))
            if (request != null) {
                _state.update {
                    it.copy(pendingDelete = request, pendingTrashIds = outcome.succeeded.map { it.trashId })
                }
            } else {
                val ok = mediaActions.deleteDirect(listOf(item))
                if (ok) {
                    _state.update { it.copy(message = "Moved to Recently Deleted") }
                } else {
                    trashRepository.discard(outcome.succeeded.map { it.trashId })
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

    /**
     * Hands the file to another app.
     *
     * Offered from the "no preview" card, where the app has already said it cannot draw
     * the file itself — so this is the honest next step, and the message names the file
     * when nothing on the device can take it.
     */
    fun openWith(item: MediaItem) {
        val opened = mediaActions.launchOpen(item)
        if (!opened) {
            _state.update { it.copy(message = "No installed app can open ${item.displayName}") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
