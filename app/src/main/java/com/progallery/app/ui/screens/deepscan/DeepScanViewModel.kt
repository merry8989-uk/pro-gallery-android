package com.progallery.app.ui.screens.deepscan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.deepscan.DeepScanRepository
import com.progallery.app.data.deepscan.ScanState
import com.progallery.app.data.local.entity.ScanDirectoryEntity
import com.progallery.app.data.settings.SettingsRepository
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

data class DeepScanUiState(
    val directories: List<ScanDirectoryEntity> = emptyList(),
    val scanState: ScanState = ScanState.Idle,
)

@HiltViewModel
class DeepScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DeepScanRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeepScanUiState())
    val uiState: StateFlow<DeepScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeAuthorisedDirectories().collect { dirs ->
                _state.update { it.copy(directories = dirs) }
            }
        }
    }

    fun addDirectory(treeUri: Uri) {
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: "Folder"
        viewModelScope.launch {
            val persisted = repository.addAuthorisedDirectory(treeUri, name)
            if (!persisted) {
                _state.update {
                    it.copy(
                        scanState = ScanState.Failed(
                            "Android did not keep access to \"$name\". Please pick the folder again.",
                        ),
                    )
                }
            }
        }
    }

    fun removeDirectory(treeUri: String) {
        viewModelScope.launch { repository.removeAuthorisedDirectory(treeUri) }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            // Explain rather than silently doing nothing when the user has
            // disabled the feature that this screen drives.
            val settings = settingsRepository.current()
            when {
                !settings.deepScanEnabled && !settings.safScanEnabled ->
                    _state.update {
                        it.copy(scanState = ScanState.Failed("Deep scan is turned off in Settings."))
                    }

                !settings.anyCategoryEnabled ->
                    _state.update {
                        it.copy(
                            scanState = ScanState.Failed(
                                "Every file category is disabled in Settings, so there is nothing to look for.",
                            ),
                        )
                    }

                else -> repository.scanAll()
                    // Belt and braces: even if the flow itself failed, the screen
                    // must reach a terminal state rather than stay on "Scanning".
                    .catch { t ->
                        _state.update {
                            it.copy(
                                scanState = ScanState.Failed(
                                    t.message ?: "The scan stopped unexpectedly.",
                                ),
                            )
                        }
                    }
                    .collect { scanState ->
                        _state.update { it.copy(scanState = scanState) }
                    }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanState = ScanState.Idle) }
    }

    fun reset() = _state.update { it.copy(scanState = ScanState.Idle) }
}
