package com.progallery.app.ui.screens.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.tree.FileTree
import com.progallery.app.data.tree.TreeRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.flow.update
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.util.MediaActions

/** What the tree screen shows. */
data class FileTreeUiState(
    val rows: List<TreeRow> = emptyList(),
    val loading: Boolean = true,
    /** Folders the person has closed. Closed rather than open, so a new folder arrives open. */
    val collapsed: Set<String> = emptySet(),
    val itemCount: Int = 0,
    val totalBytes: Long = 0,
    /** The file a rename is being asked about, so the dialog knows what it is naming. */
    val renaming: MediaItem? = null,
    /** What the person has typed so far. Kept here so it survives a rotation. */
    val renameText: String = "",
    /** What the app last did, said on the screen rather than silently. */
    val message: String? = null,
)

/**
 * The file tree.
 *
 * Built from the app's own snapshot of what it can see — the same list every other screen reads —
 * rather than by walking the filesystem. A walk would show folders the app was refused as **empty**
 * ones, which is a lie about what is in them; this shows only what is really there, and the
 * capability and access screens already say what is not.
 *
 * Collapse state is kept here rather than in the screen so it survives rotation, and it stores the
 * **closed** folders: a folder that appears while the screen is open should be open, because it is
 * new and the person has not seen it.
 */
@HiltViewModel
class FileTreeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _state = MutableStateFlow(FileTreeUiState())
    val uiState: StateFlow<FileTreeUiState> = _state.asStateFlow()

    private var allItems: List<MediaItem> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    repository.snapshot(
                        // Every kind, because a file tree that left things out by category would
                        // be a tree of some files rather than of the folder.
                        kinds = MediaType.entries.filter { it != MediaType.ALL }.toSet(),
                        sort = SortOrder.NAME_ASC,
                        query = "",
                    )
                }.getOrDefault(emptyList())
            }
            allItems = items
            _state.update {
                it.copy(
                    rows = visibleRows(items, it.collapsed),
                    loading = false,
                    itemCount = items.size,
                    totalBytes = items.sumOf { item -> item.sizeBytes },
                )
            }
        }
    }

    fun toggle(path: String) {
        val collapsed = _state.value.collapsed
        val next = if (path in collapsed) collapsed - path else collapsed + path
        _state.update { it.copy(collapsed = next, rows = visibleRows(allItems, next)) }
    }

    fun collapseAll() = _state.update {
        it.copy(
            collapsed = allItems.mapNotNull { item -> item.relativePath }.toSet(),
            rows = visibleRows(allItems, allItems.mapNotNull { item -> item.relativePath }.toSet()),
        )
    }

    fun expandAll() = _state.update {
        it.copy(collapsed = emptySet(), rows = visibleRows(allItems, emptySet()))
    }

    // ---- The operations, which already existed but were reachable only from a viewer ----
    //
    // Renaming, deleting, copying and moving all worked and were wired to the viewer, the player
    // and the PDF screen. A file manager wants them where the files are, so they are offered on the
    // tree as well — reusing the same calls rather than a second implementation, because two
    // implementations of "delete a file" is how one of them ends up wrong.

    /** Asks the system to let the app rename this file. The dialog is the screen's to launch. */
    fun startRename(item: MediaItem) = _state.update {
        it.copy(renaming = item, renameText = item.displayName, message = null)
    }

    fun setRenameText(text: String) = _state.update { it.copy(renameText = text) }

    fun cancelRename() = _state.update { it.copy(renaming = null, renameText = "") }

    /**
     * Applies the rename once the system has answered.
     *
     * The pending item and the typed name are read from the state rather than passed through the
     * activity result, because a result can arrive after the screen has been rebuilt — and a rename
     * that used whatever the field happened to hold at that moment would be a rename of the wrong
     * file.
     */
    fun onRenameConsent(granted: Boolean) {
        val item = _state.value.renaming ?: return
        val name = _state.value.renameText
        if (!granted) {
            _state.update { it.copy(renaming = null, message = "The rename was not allowed.") }
            return
        }
        applyRename(item, name)
    }

    /**
     * The rename request, or null when the platform will not ask.
     *
     * Below Android 11 there is no request to make — the app either has write access or it does
     * not — and returning null rather than a fake intent is the honest answer.
     */
    fun renameRequest(item: MediaItem): android.app.PendingIntent? = mediaActions.renameRequest(item.uri)

    /** Applies a rename once the system has granted it. */
    fun applyRename(item: MediaItem, newName: String) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isEmpty() || trimmed == item.displayName) {
                _state.update { it.copy(renaming = null, renameText = "") }
                return@launch
            }
            val ok = withContext(Dispatchers.IO) { mediaActions.applyRename(item.uri, trimmed) }
            _state.update {
                it.copy(
                    renaming = null,
                    renameText = "",
                    message = if (ok) "Renamed to \"$trimmed\"." else "The rename did not go through.",
                )
            }
            if (ok) load()
        }
    }

    /** The delete request, which the system shows. The app never deletes a file on its own. */
    fun deleteRequest(item: MediaItem): android.app.PendingIntent? =
        mediaActions.deleteRequest(listOf(item))

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * The rows to draw, with anything inside a closed folder left out.
     *
     * A row is inside a closed folder when its path starts with that folder's path and the next
     * character is a separator — so `DCIM` closing hides `DCIM/Camera` but not a folder that merely
     * begins with the same letters, which a plain `startsWith` would get wrong.
     */
    private fun visibleRows(items: List<MediaItem>, collapsed: Set<String>): List<TreeRow> {
        if (collapsed.isEmpty()) return FileTree.rows(items)
        return FileTree.rows(items).filter { row ->
            collapsed.none { closed ->
                row.node.path.startsWith("$closed/")
            }
        }
    }
}
