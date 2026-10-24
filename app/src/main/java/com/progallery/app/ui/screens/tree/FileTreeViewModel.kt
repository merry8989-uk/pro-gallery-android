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

/** What the tree screen shows. */
data class FileTreeUiState(
    val rows: List<TreeRow> = emptyList(),
    val loading: Boolean = true,
    /** Folders the person has closed. Closed rather than open, so a new folder arrives open. */
    val collapsed: Set<String> = emptySet(),
    val itemCount: Int = 0,
    val totalBytes: Long = 0,
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
