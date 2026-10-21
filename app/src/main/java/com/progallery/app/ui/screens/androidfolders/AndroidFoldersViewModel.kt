package com.progallery.app.ui.screens.androidfolders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.androidfolders.AndroidFolderScanner
import com.progallery.app.data.androidfolders.AndroidFolderTarget
import com.progallery.app.data.androidfolders.Reachability
import com.progallery.app.data.deepscan.ScanState
import com.progallery.app.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.progallery.app.data.index.IndexedMediaRepository

data class AndroidFoldersUiState(
    val reachability: List<Reachability> = emptyList(),
    val selectedTargets: Set<AndroidFolderTarget> = emptySet(),
    val scanState: ScanState = ScanState.Idle,
    val hasAllFilesAccess: Boolean = false,
    val scanning: Boolean = false,
)

@HiltViewModel
class AndroidFoldersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: AndroidFolderScanner,
    private val indexedMedia: IndexedMediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AndroidFoldersUiState())
    val uiState: StateFlow<AndroidFoldersUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    /** Re-evaluated on resume so a permission granted in Settings is picked up. */
    fun refreshAccess() {
        val reachability = scanner.reachability()
        _uiState.update { current ->
            val stillValid = current.selectedTargets.filter { target ->
                reachability.any { it.target == target && it.reachable }
            }.toSet()
            current.copy(
                reachability = reachability,
                hasAllFilesAccess = PermissionUtils.hasAllFilesAccess(context),
                // Preselect everything reachable the first time.
                selectedTargets = if (current.reachability.isEmpty()) {
                    reachability.filter { it.reachable }.map { it.target }.toSet()
                } else {
                    stillValid
                },
            )
        }
    }

    fun toggleTarget(target: AndroidFolderTarget) = _uiState.update { current ->
        val selected = current.selectedTargets.toMutableSet()
        if (!selected.add(target)) selected.remove(target)
        current.copy(selectedTargets = selected)
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        val targets = _uiState.value.selectedTargets.toList()
        if (targets.isEmpty()) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(scanning = true) }
            scanner.scan(targets)
                .catch { t ->
                    _uiState.update {
                        it.copy(
                            scanState = ScanState.Failed(t.message ?: "The scan stopped unexpectedly."),
                            scanning = false,
                        )
                    }
                }
                .collect { state ->
                _uiState.update {
                    it.copy(
                        scanState = state,
                        scanning = state is ScanState.Scanning,
                    )
                }

                // What the scan found becomes the app's own index, so these files appear in
                // the gallery among the photos rather than only on this screen. Replaced
                // rather than appended: a scan is a statement about what is there now, so a
                // file the user deleted must leave the index with it.
                if (state is ScanState.Completed) {
                    runCatching { indexedMedia.replaceFromScan(state.media) }
                }
            }
            _uiState.update { it.copy(scanning = false) }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(scanning = false, scanState = ScanState.Idle) }
    }
}
