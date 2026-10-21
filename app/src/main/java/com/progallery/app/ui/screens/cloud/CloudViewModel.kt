package com.progallery.app.ui.screens.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.cloud.CloudDocument
import com.progallery.app.data.cloud.CloudGrantStore
import com.progallery.app.data.cloud.CloudKind
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
    /** Folders already chosen, which open straight away with no further step. */
    val granted: List<GrantedCloudTree> = emptyList(),
    val title: String? = null,
    val treeUri: String? = null,
    val trail: List<CloudStep> = emptyList(),
    val documents: List<CloudDocument> = emptyList(),
    val browsing: Boolean = false,
    val error: String? = null,
    /** True while a newly chosen folder is being named and opened. */
    val opening: Boolean = false,
)

/**
 * Browsing cloud folders, inside the app.
 *
 * **How a folder gets here.** The user picks it once through the system's own folder picker —
 * the same screen that lists every installed service in its sidebar. That is the supported
 * way for an app to reach a cloud provider: the provider app keeps its account and its
 * session, and the platform hands this app a folder it may read. The app never sees a
 * password and never signs anything in, which is exactly why there is nothing to sign into
 * again later: the grant is persisted, and the provider's own login was never ours to hold.
 */
@HiltViewModel
class CloudViewModel @Inject constructor(
    private val browser: DocumentTreeBrowser,
    private val grants: CloudGrantStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CloudUiState())
    val state: StateFlow<CloudUiState> = _state.asStateFlow()

    private val _open = MutableStateFlow<CloudOpenRequest?>(null)
    val open: StateFlow<CloudOpenRequest?> = _open.asStateFlow()

    init {
        viewModelScope.launch {
            grants.observe().collect { list -> _state.update { it.copy(granted = list) } }
        }
    }

    /** Called after the system picker returns and the permission has been persisted. */
    fun folderChosen(treeUri: String) {
        viewModelScope.launch {
            _state.update { it.copy(opening = true, error = null) }
            // The picker hands back an address, not a name, so the provider is asked.
            val name = browser.displayName(treeUri) ?: treeUri.substringAfterLast('/').ifBlank { "Cloud folder" }
            grants.remember(treeUri, name)
            _state.update { it.copy(opening = false) }
            openTree(treeUri, name)
        }
    }

    fun openGranted(granted: GrantedCloudTree) {
        if (browser.hasPersistedGrant(granted.treeUri)) {
            openTree(granted.treeUri, granted.title)
        } else {
            // Access was removed outside the app, so the entry is dropped rather than shown
            // as something that would fail when tapped.
            viewModelScope.launch {
                grants.forget(granted.treeUri)
                _state.update {
                    it.copy(error = "Access to that folder was removed, so it was forgotten.")
                }
            }
        }
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
                    _state.update {
                        it.copy(
                            browsing = false,
                            documents = emptyList(),
                            error = if (failure is DocumentTreeBrowser.GrantRequired) {
                                "This service needs the folder to be chosen again."
                            } else {
                                failure.message ?: "That folder could not be listed."
                            },
                        )
                    }
                },
            )
        }
    }
}
