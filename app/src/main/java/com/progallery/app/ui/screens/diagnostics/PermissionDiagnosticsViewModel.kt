package com.progallery.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.diagnostics.DiagnosticLog
import com.progallery.app.data.permissions.AccessDiagnostics
import com.progallery.app.data.permissions.AccessRow
import com.progallery.app.data.permissions.AccessSnapshotReader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionDiagnosticsUiState(
    val loading: Boolean = true,
    val rows: List<AccessRow> = emptyList(),
    val headline: String = "",
    /** How many accesses are actually stopping the app. */
    val blockingCount: Int = 0,
)

@HiltViewModel
class PermissionDiagnosticsViewModel @Inject constructor(
    private val reader: AccessSnapshotReader,
    private val diagnosticLog: DiagnosticLog,
) : ViewModel() {

    private val _state = MutableStateFlow(PermissionDiagnosticsUiState())
    val state: StateFlow<PermissionDiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads the device state.
     *
     * Worth having a button for: "All files access" and the app-settings screen are
     * changed *outside* the app, so the answer can differ when the user comes back.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val rows = AccessDiagnostics.rows(reader.read())
            val blocking = AccessDiagnostics.blocking(rows)
            _state.update {
                it.copy(
                    loading = false,
                    rows = rows,
                    headline = AccessDiagnostics.headline(rows),
                    blockingCount = blocking.size,
                )
            }
            diagnosticLog.record("Access diagnostics opened", "${blocking.size} blocking")
        }
    }

    /** Records that the user went to fix something, so a report can say what was tried. */
    fun noteFixAttempt(row: AccessRow) {
        viewModelScope.launch { diagnosticLog.record("Access fix requested", row.id.name) }
    }
}
