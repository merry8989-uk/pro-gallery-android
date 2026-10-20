package com.progallery.app.ui.screens.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.diagnostics.DiagnosticLog
import com.progallery.app.data.stream.StreamLink
import com.progallery.app.data.stream.StreamLinkStore
import com.progallery.app.data.stream.StreamLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StreamUiState(
    val input: String = "",
    /** The reason a link was refused, in words the user can act on. */
    val error: String? = null,
)

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val store: StreamLinkStore,
    private val diagnosticLog: DiagnosticLog,
) : ViewModel() {

    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    val recent: StateFlow<List<StreamLink>> = store.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set when a link is ready to play; the screen navigates and then consumes it. */
    private val _play = MutableStateFlow<String?>(null)
    val play: StateFlow<String?> = _play.asStateFlow()

    fun setInput(value: String) {
        // Clearing the error as the user edits avoids a stale complaint hanging around.
        _state.update { it.copy(input = value, error = null) }
    }

    /** Checks the link and, if it is usable, asks to play it. */
    fun submit() {
        val raw = _state.value.input
        StreamLinks.parse(raw)
            .onSuccess { link ->
                viewModelScope.launch {
                    store.remember(link.url)
                    diagnosticLog.record("Stream requested", link.host)
                }
                _state.update { it.copy(input = "", error = null) }
                _play.value = link.url
            }
            .onFailure { failure ->
                _state.update { it.copy(error = failure.message ?: "That link could not be used.") }
            }
    }

    fun playAgain(link: StreamLink) {
        viewModelScope.launch {
            store.remember(link.url)
            diagnosticLog.record("Stream requested", link.host)
        }
        _play.value = link.url
    }

    fun consumePlay() {
        _play.value = null
    }

    fun forget(link: StreamLink) {
        viewModelScope.launch { store.forget(link.url) }
    }

    fun clearRecent() {
        viewModelScope.launch { store.clear() }
    }
}
