package com.progallery.app.ui.screens.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.cloud.CloudDocument
import com.progallery.app.data.cloud.CloudGrantStore
import com.progallery.app.data.cloud.CloudKind
import com.progallery.app.data.cloud.CloudRoot
import com.progallery.app.data.cloud.CloudRootsReader
import com.progallery.app.data.cloud.DocumentTreeBrowser
import com.progallery.app.data.cloud.GrantedCloudTree
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A file the user tapped, ready to be handed to the right in-app viewer. */
data class CloudOpenRequest(val uri: String, val name: String, val kind: CloudKind)

/** One step of the folder trail, so "up" knows where it is going. */
data class CloudStep(val documentId: String, val name: String)

data class CloudUiState(
    val loading: Boolean = true,
    /** The services installed on this device. The device's own storage is left out. */
    val roots: List<CloudRoot> = emptyList(),
    /** Folders already granted once, so they open straight away. */
    val granted: List<GrantedCloudTree> = emptyList(),
    val title: String? = null,
    val treeUri: String? = null,
    val trail: List<CloudStep> = emptyList(),
    val documents: List<CloudDocument> = emptyList(),
    val browsing: Boolean = false,
    val error: String? = null,
    /** A provider that wants its folder granted before it will open. */
    val needsGrant: CloudRoot? = null,
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val rootsReader: CloudRootsReader,
    private val browser: DocumentTreeBrowser,
    private val grants: CloudGrantStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CloudUiState())
    val state: StateFlow<CloudUiState> = _state.asStateFlow()

    /** A file to open, with the kind that decides which in-app viewer takes it. */
    private val _open = MutableStateFlow<CloudOpenRequest?>(null)
    val open: StateFlow<CloudOpenRequest?> = _open.asStateFlow()

    init {
        viewModelScope.launch {
            grants.observe().collect { list -> _state.update { it.copy(granted = list) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val services = rootsReader.roots().filterNot { it.isDeviceStorage }
            _state.update { it.copy(loading = false, roots = services) }
        }
    }

    /** Opens a service at the folder that was already granted, if there is one. */
    fun openRoot(root: CloudRoot) {
        val remembered = _state.value.granted.firstOrNull { it.treeUri == root.treeUri }
        if (remembered != null && browser.hasPersistedGrant(root.treeUri)) {
            openTree(root.treeUri, root.title)
        } else {
            // The provider has not been granted yet, so the screen offers it once.
            _state.update { it.copy(needsGrant = root) }
        }
    }

    fun openGranted(granted: GrantedCloudTree) {
        if (browser.hasPersistedGrant(granted.treeUri)) {
            openTree(granted.treeUri, granted.title)
        } else {
            // The grant was revoked outside the app, so it is dropped rather than shown.
            viewModelScope.launch {
                grants.forget(granted.treeUri)
                _state.update { it.copy(error = "That folder's access was removed, so it was forgotten.") }
            }
        }
    }

    /** Called after the system folder picker returns, once the permission is persisted. */
    fun granted(treeUri: String, title: String) {
        viewModelScope.launch {
            grants.remember(treeUri, title)
            _state.update { it.copy(needsGrant = null) }
            openTree(treeUri, title)
        }
    }

    fun dismissGrant() {
        _state.update { it.copy(needsGrant = null) }
    }

    fun forget(treeUri: String) {
        viewModelScope.launch {
            grants.forget(treeUri)
            if (_state.value.treeUri == treeUri) closeFolder()
        }
    }

    fun openDocument(document: CloudDocument) {
        if (document.isDirectory) {
            val tree = _state.value.treeUri ?: return
            _state.update {
                it.copy(
                    trail = it.trail + CloudStep(document.documentId, document.name),
                    documents = emptyList(),
                )
            }
            load(tree, document.documentId)
        } else {
            _open.value = CloudOpenRequest(document.uri, document.name, document.kind)
        }
    }

    fun goUp() {
        val current = _state.value
        val tree = current.treeUri ?: return
        if (current.trail.isEmpty()) {
            closeFolder()
            return
        }
        val trail = current.trail.dropLast(1)
        _state.update { it.copy(trail = trail, documents = emptyList()) }
        load(tree, trail.lastOrNull()?.documentId)
    }

    fun closeFolder() {
        _state.update {
            it.copy(title = null, treeUri = null, trail = emptyList(), documents = emptyList(), error = null)
        }
    }

    fun consumeOpen() {
        _open.value = null
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    private fun openTree(treeUri: String, title: String) {
        _state.update {
            it.copy(title = title, treeUri = treeUri, trail = emptyList(), documents = emptyList(), error = null)
        }
        load(treeUri, null)
    }

    private fun load(treeUri: String, parentDocumentId: String?) {
        viewModelScope.launch {
            _state.update { it.copy(browsing = true, error = null) }
            browser.list(treeUri, parentDocumentId).fold(
                onSuccess = { documents ->
                    _state.update { it.copy(browsing = false, documents = documents) }
                },
                onFailure = { failure ->
                    // A grant that the provider has started refusing is offered again rather
                    // than reported as a plain error.
                    val wantsGrant = failure is DocumentTreeBrowser.GrantRequired
                    _state.update {
                        it.copy(
                            browsing = false,
                            documents = emptyList(),
                            error = if (wantsGrant) null else (failure.message ?: "That folder could not be listed."),
                        )
                    }
                },
            )
        }
    }
}
