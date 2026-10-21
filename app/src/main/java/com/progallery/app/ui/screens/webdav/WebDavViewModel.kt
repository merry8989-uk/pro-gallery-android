package com.progallery.app.ui.screens.webdav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.diagnostics.DiagnosticLog
import com.progallery.app.data.webdav.WebDavClient
import com.progallery.app.data.webdav.WebDavCredentialStore
import com.progallery.app.data.webdav.WebDavEntry
import com.progallery.app.data.webdav.WebDavServer
import com.progallery.app.data.webdav.WebDavXml
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebDavUiState(
    val servers: List<WebDavServer> = emptyList(),
    /** Null while the server list is showing. */
    val openServer: WebDavServer? = null,
    val folderUrl: String? = null,
    val entries: List<WebDavEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val canGoUp: Boolean = false,
)

@HiltViewModel
class WebDavViewModel @Inject constructor(
    private val store: WebDavCredentialStore,
    private val client: WebDavClient,
    private val diagnosticLog: DiagnosticLog,
) : ViewModel() {

    private val _state = MutableStateFlow(WebDavUiState())
    val state: StateFlow<WebDavUiState> = _state.asStateFlow()

    /** Set when a file is ready to play; the screen navigates and then consumes it. */
    private val _play = MutableStateFlow<Pair<String, String>?>(null)
    val play: StateFlow<Pair<String, String>?> = _play.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            store.observe().collect { servers -> _state.update { it.copy(servers = servers) } }
        }
    }

    fun addServer(name: String, url: String, username: String, password: String) {
        if (url.isBlank()) {
            _message.value = "A server needs an address."
            return
        }
        viewModelScope.launch {
            store.add(name, url, username, password)
            diagnosticLog.record("WebDAV server added", null)
            _message.value = "Server added"
        }
    }

    fun removeServer(server: WebDavServer) {
        viewModelScope.launch {
            store.remove(server.id)
            _state.update { current ->
                if (current.openServer?.id == server.id) {
                    current.copy(openServer = null, entries = emptyList(), folderUrl = null, error = null)
                } else {
                    current
                }
            }
        }
    }

    /** Opens a server at its root. */
    fun openServer(server: WebDavServer) {
        _state.update {
            it.copy(openServer = server, folderUrl = WebDavXml.asCollection(server.url), entries = emptyList())
        }
        load()
    }

    fun closeServer() {
        _state.update { it.copy(openServer = null, entries = emptyList(), folderUrl = null, error = null) }
    }

    /** Opens a folder, or asks to play a file. */
    fun openEntry(entry: WebDavEntry) {
        val server = _state.value.openServer ?: return
        if (entry.isDirectory) {
            _state.update { it.copy(folderUrl = WebDavXml.asCollection(entry.url)) }
            load()
        } else {
            diagnosticLog.record("WebDAV file opened", entry.mimeType)
            _play.value = server.id to entry.url
        }
    }

    fun goUp() {
        val current = _state.value.folderUrl ?: return
        val parent = WebDavXml.parentOf(current) ?: return
        _state.update { it.copy(folderUrl = parent) }
        load()
    }

    fun refresh() = load()

    fun consumePlay() {
        _play.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun load() {
        val current = _state.value
        val server = current.openServer ?: return
        val folder = current.folderUrl ?: return

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val credentials = store.credentials(server.id)
            if (credentials == null) {
                _state.update { it.copy(loading = false, error = "That server is no longer saved.") }
                return@launch
            }

            client.list(server, credentials.password, folder).fold(
                onSuccess = { entries ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            entries = sortForDisplay(entries),
                            canGoUp = WebDavXml.parentOf(folder) != null,
                        )
                    }
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            entries = emptyList(),
                            error = failure.message ?: "The folder could not be listed.",
                        )
                    }
                },
            )
        }
    }

    /** Folders first, then names — the order a file browser is read in. */
    private fun sortForDisplay(entries: List<WebDavEntry>): List<WebDavEntry> =
        entries.sortedWith(
            compareByDescending<WebDavEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
}
