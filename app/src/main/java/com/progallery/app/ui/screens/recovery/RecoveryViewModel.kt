package com.progallery.app.ui.screens.recovery

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.deepscan.DeepScanRepository
import com.progallery.app.data.local.entity.RecoveryHistoryEntity
import com.progallery.app.data.model.ViewMode
import com.progallery.app.data.recovery.RecoveryExportOutcome
import com.progallery.app.data.recovery.RecoveryExporter
import com.progallery.app.data.recovery.RecoveryQuery
import com.progallery.app.data.recovery.RecoveryRepository
import com.progallery.app.data.recovery.RecoveryResult
import com.progallery.app.data.recovery.RecoveryScanState
import com.progallery.app.data.recovery.RecoverySort
import com.progallery.app.data.recovery.RecoveryStatus
import com.progallery.app.data.recovery.RecoveryTab
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecoveryUiState(
    val scanState: RecoveryScanState = RecoveryScanState.Idle,
    val tab: RecoveryTab = RecoveryTab.ALL,
    val sort: RecoverySort = RecoverySort.RELEVANCE,
    val viewMode: ViewMode = ViewMode.GRID,
    val query: String = "",
    val selection: Set<String> = emptySet(),
    val generateThumbnails: Boolean = true,
    val previewing: RecoveryResult? = null,
    val exporting: Boolean = false,
    val message: String? = null,
) {
    val scanning: Boolean get() = scanState is RecoveryScanState.Scanning

    val results: List<RecoveryResult>
        get() = (scanState as? RecoveryScanState.Completed)?.results.orEmpty()

    /** What the grid or list actually shows, after tab, search and sort. */
    val visible: List<RecoveryResult> get() = RecoveryQuery.apply(results, tab, query, sort)

    val selectionMode: Boolean get() = selection.isNotEmpty()
    val selectedResults: List<RecoveryResult> get() = results.filter { it.id in selection }

    /** Exportable: everything selected except preview-only artefacts. */
    val exportableSelection: List<RecoveryResult>
        get() = selectedResults.filter { it.status != RecoveryStatus.PREVIEW_ONLY }
}

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecoveryRepository,
    private val exporter: RecoveryExporter,
    private val deepScanRepository: DeepScanRepository,
    private val mediaActions: MediaActions,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    /** Export history, newest first. */
    val history: StateFlow<List<RecoveryHistoryEntity>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(generateThumbnails = settings.generateThumbnails) }
            }
        }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        _uiState.update {
            it.copy(scanState = RecoveryScanState.Scanning("Starting", 0), selection = emptySet())
        }
        scanJob = viewModelScope.launch {
            repository.quickScan()
                // Belt and braces: the screen must always reach a terminal state.
                .catch { t ->
                    _uiState.update {
                        it.copy(
                            scanState = RecoveryScanState.Failed(
                                t.message ?: "The scan stopped unexpectedly.",
                            ),
                        )
                    }
                }
                .collect { state ->
                    if (state is RecoveryScanState.Completed) repository.remember(state.results)
                    _uiState.update { it.copy(scanState = state) }
                }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(scanState = RecoveryScanState.Idle, message = "Scan cancelled") }
    }

    fun setTab(tab: RecoveryTab) = _uiState.update { it.copy(tab = tab) }
    fun setSort(sort: RecoverySort) = _uiState.update { it.copy(sort = sort) }
    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }
    fun setViewMode(mode: ViewMode) = _uiState.update { it.copy(viewMode = mode) }
    fun preview(result: RecoveryResult?) = _uiState.update { it.copy(previewing = result) }

    fun toggleSelection(result: RecoveryResult) = _uiState.update { current ->
        val selection = current.selection.toMutableSet()
        if (!selection.add(result.id)) selection.remove(result.id)
        current.copy(selection = selection)
    }

    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    fun selectAllVisible() = _uiState.update { current ->
        current.copy(selection = current.visible.map { it.id }.toSet())
    }

    /** Grants a folder to look inside, sharing the Deep Scan grants. */
    fun addFolder(treeUri: Uri) {
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name
            ?: treeUri.lastPathSegment
            ?: "Folder"
        viewModelScope.launch {
            val persisted = deepScanRepository.addAuthorisedDirectory(treeUri, name)
            _uiState.update {
                it.copy(
                    message = if (persisted) {
                        "Added \"$name\". Scan again to look inside it."
                    } else {
                        "Android did not keep access to \"$name\". Please pick the folder again."
                    },
                )
            }
        }
    }

    fun share(result: RecoveryResult) = mediaActions.launchShare(listOf(result.item))

    /**
     * Exports the selection into the folder the user chose. Every file is verified
     * before it is reported as exported.
     */
    fun exportSelection(destinationTree: Uri) {
        val targets = _uiState.value.exportableSelection
        if (targets.isEmpty()) {
            _uiState.update { it.copy(message = "Nothing exportable is selected") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            val outcomes = exporter.exportAll(targets, destinationTree)
            val succeeded = outcomes.count { it is RecoveryExportOutcome.Success }
            val failed = outcomes.size - succeeded
            val firstFailure = outcomes.filterIsInstance<RecoveryExportOutcome.Failed>().firstOrNull()
            _uiState.update {
                it.copy(
                    exporting = false,
                    selection = emptySet(),
                    message = when {
                        failed == 0 -> "Exported $succeeded file(s)"
                        succeeded == 0 -> "Export failed: ${firstFailure?.message ?: "unknown reason"}"
                        else -> "Exported $succeeded, $failed failed"
                    },
                )
            }
        }
    }

    /** Clears the export history. Never touches the recovered files themselves. */
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.update { it.copy(message = "Export history cleared") }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
