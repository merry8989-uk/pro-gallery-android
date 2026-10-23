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
import com.progallery.app.data.recovery.RecoveryScanMode
import com.progallery.app.data.recovery.RecoveryScanState
import com.progallery.app.data.recovery.RecoverySort
import com.progallery.app.data.recovery.RecoveryTab
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.data.settings.ThumbnailPolicy
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
import com.progallery.app.data.diagnostics.DiagnosticLog
import com.progallery.app.data.settings.ExportDuplicatePolicy
import com.progallery.app.data.recovery.ExportDestination
import com.progallery.app.data.recovery.ExportDestinationStore

data class RecoveryUiState(
    val scanState: RecoveryScanState = RecoveryScanState.Idle,
    val tab: RecoveryTab = RecoveryTab.ALL,
    val sort: RecoverySort = RecoverySort.RELEVANCE,
    val viewMode: ViewMode = ViewMode.GRID,
    val query: String = "",
    val selection: Set<String> = emptySet(),
    val thumbnailPolicy: ThumbnailPolicy = ThumbnailPolicy(),
    /** Which scan modes Settings allows; the screen offers only those. */
    val allowedModes: Set<RecoveryScanMode> = RecoveryScanMode.entries.toSet(),
    /** Export can be switched off entirely in Settings. */
    val exportEnabled: Boolean = true,
    /** Whether to confirm before writing, and whether to ask where each time. */
    val exportConfirmBeforeExport: Boolean = true,
    val exportAskForDestination: Boolean = true,
    val mode: RecoveryScanMode = RecoveryScanMode.QUICK,
    val previewing: RecoveryResult? = null,
    val exporting: Boolean = false,
    val message: String? = null,
    /** Results waiting on an answer, because the duplicate policy is "Ask" and a name clashed. */
    val pendingDecision: List<RecoveryResult> = emptyList(),
    /** A denser grid, from Settings. */
    val compactCards: Boolean = false,
    /** Whether a tap buzzes. Off unless the person asked for it. */
    val hapticsEnabled: Boolean = false,
    /** Set the moment a tab is tapped, so the default only ever applies to an untouched screen. */
    val tabChosenByHand: Boolean = false,
) {
    val scanning: Boolean get() = scanState is RecoveryScanState.Scanning

    val results: List<RecoveryResult>
        get() = (scanState as? RecoveryScanState.Completed)?.results.orEmpty()

    /** What the grid or list actually shows, after tab, search and sort. */
    val visible: List<RecoveryResult> get() = RecoveryQuery.apply(results, tab, query, sort)

    val selectionMode: Boolean get() = selection.isNotEmpty()
    val selectedResults: List<RecoveryResult> get() = results.filter { it.id in selection }

    /**
     * Exportable: available files, plus thumbnails — which are real files, just not
     * the original. A preview-only result that could not be read is not exportable.
     */
    val exportableSelection: List<RecoveryResult> get() = selectedResults.filter { it.canExport }
}

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecoveryRepository,
    private val exporter: RecoveryExporter,
    private val deepScanRepository: DeepScanRepository,
    private val mediaActions: MediaActions,
    settingsRepository: SettingsRepository,
    private val diagnosticLog: DiagnosticLog,
    private val destinations: ExportDestinationStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    /** Export history, newest first. */
    val history: StateFlow<List<RecoveryHistoryEntity>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var scanJob: Job? = null

    /** The folder the last export went to, when there is one. */
    val rememberedDestination: StateFlow<ExportDestination?> = destinations.remembered
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // The history retention the settings describe, actually applied. `purgeHistory` existed
        // with a 90-day default and **nothing called it**, so the history grew for ever while a
        // setting was about to be added to control a purge that never ran.
        viewModelScope.launch {
            runCatching {
                val days = settingsRepository.current().exportHistoryRetentionDays
                repository.purgeHistory(days)
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { current ->
                    val allowed = buildSet {
                        if (settings.recoveryQuickScanEnabled) add(RecoveryScanMode.QUICK)
                        if (settings.recoveryThumbnailCacheEnabled) add(RecoveryScanMode.THUMBNAIL_CACHE)
                    }
                    current.copy(
                        thumbnailPolicy = ThumbnailPolicy.from(settings),
                        allowedModes = allowed,
                        exportEnabled = settings.exportEnabled,
                        exportConfirmBeforeExport = settings.exportConfirmBeforeExport,
                        exportAskForDestination = settings.exportAskForDestination,
                        // Only while the screen has not been touched: a tab somebody chose on this
                        // visit must not be replaced by the setting on the next emission.
                        tab = if (current.tabChosenByHand) current.tab else settings.defaultRecoveryTab,
                        compactCards = settings.compactResultCards,
                        hapticsEnabled = settings.hapticsEnabled,
                        // Never leave the screen pointing at a mode that is no longer
                        // offered, or the scan button would do nothing.
                        mode = if (current.mode in allowed) current.mode else allowed.firstOrNull() ?: current.mode,
                    )
                }
            }
        }
    }

    fun setMode(mode: RecoveryScanMode) = _uiState.update { it.copy(mode = mode) }

    /**
     * Answers the "a file of that name is already there" question.
     *
     * The answer applies to this run only. The person was asked about this folder and these files,
     * not about how exports should behave from now on — so the setting is left alone rather than
     * being changed behind their back.
     */
    fun answerDecision(policy: ExportDuplicatePolicy, destinationTree: Uri) {
        val waiting = _uiState.value.pendingDecision
        if (waiting.isEmpty()) return
        _uiState.update { it.copy(pendingDecision = emptyList(), selection = waiting.map { r -> r.item.mediaKey }.toSet()) }
        exportSelection(destinationTree, policyOverride = policy)
    }

    fun dismissDecision() = _uiState.update { it.copy(pendingDecision = emptyList()) }

    /**
     * Remembers the folder an export went to.
     *
     * The grant is taken here rather than by the picker, because a URI permission that is not taken
     * is not held after the process ends — remembering it without this would remember a folder the
     * app could no longer open.
     */
    suspend fun rememberDestination(tree: Uri, displayName: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        destinations.remember(ExportDestination(tree.toString(), displayName))
    }

    /** Called when a remembered folder turns out to be gone or unreadable. */
    suspend fun forgetDestination() = destinations.forget()

    fun startScan() {
        if (scanJob?.isActive == true) return
        _uiState.update {
            it.copy(scanState = RecoveryScanState.Scanning("Starting", 0), selection = emptySet())
        }
        val mode = _uiState.value.mode
        scanJob = viewModelScope.launch {
            repository.scan(mode)
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

    fun setTab(tab: RecoveryTab) = _uiState.update {
        it.copy(tab = tab, tabChosenByHand = true)
    }
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
    fun exportSelection(destinationTree: Uri, policyOverride: ExportDuplicatePolicy? = null) {
        val targets = _uiState.value.exportableSelection
        if (targets.isEmpty()) {
            _uiState.update { it.copy(message = "Nothing exportable is selected") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, pendingDecision = emptyList()) }
            // Remembered only once it has worked, so a folder that turned out to be unreadable is
            // not remembered as the place to go next time.
            val outcomes = exporter.exportAll(targets, destinationTree, policyOverride)
            diagnosticLog.record("Export run", "${outcomes.size} item(s)")
            val succeeded = outcomes.count { it is RecoveryExportOutcome.Success }
            val skipped = outcomes.count { it is RecoveryExportOutcome.Skipped }
            val asked = outcomes.count { it is RecoveryExportOutcome.NeedsDecision }
            val failed = outcomes.size - succeeded - skipped - asked
            val firstFailure = outcomes.filterIsInstance<RecoveryExportOutcome.Failed>().firstOrNull()

            // The items the exporter was not allowed to decide. They are matched back to their
            // results by name, because that is what the question is about.
            val waiting = outcomes.filterIsInstance<RecoveryExportOutcome.NeedsDecision>()
                .mapNotNull { decision -> targets.firstOrNull { it.item.displayName == decision.displayName } }

            _uiState.update {
                it.copy(
                    exporting = false,
                    selection = if (asked > 0) it.selection else emptySet(),
                    pendingDecision = waiting,
                    message = when {
                        failed == 0 && skipped == 0 && asked == 0 -> "Exported $succeeded file(s)"
                        asked > 0 -> "$asked file(s) already exist in that folder. What should happen?"
                        failed == 0 -> "Exported $succeeded, skipped $skipped already present"
                        succeeded == 0 && skipped == 0 && asked == 0 ->
                            "Export failed: ${firstFailure?.message ?: "unknown reason"}"
                        else -> "Exported $succeeded, skipped $skipped, $failed failed"
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
