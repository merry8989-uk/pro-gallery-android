package com.progallery.app.ui.screens.capability

import androidx.lifecycle.ViewModel
import com.progallery.app.data.capability.Capability
import com.progallery.app.data.capability.CapabilityMatrix
import com.progallery.app.data.capability.FormatCapability
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CapabilityUiState(
    val entries: List<FormatCapability> = emptyList(),
    val query: String = "",
    val verdict: Capability? = null,
) {
    val visible: List<FormatCapability> get() = CapabilityMatrix.search(entries, query, verdict)
    val counts: Map<Capability, Int> get() = CapabilityMatrix.counts(entries)
}

/**
 * What this device can and cannot open.
 *
 * The list is built once from the same tables the players and readers use, so the answer here and
 * the behaviour out there cannot drift apart. Nothing is asked of the device — there is no
 * permission to check and nothing to load.
 */
@HiltViewModel
class CapabilityViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(CapabilityUiState(entries = CapabilityMatrix.all()))
    val state: StateFlow<CapabilityUiState> = _state.asStateFlow()

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setVerdict(verdict: Capability?) = _state.update {
        it.copy(verdict = if (it.verdict == verdict) null else verdict)
    }
}
