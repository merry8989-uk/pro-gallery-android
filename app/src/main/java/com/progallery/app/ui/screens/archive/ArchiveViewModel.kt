package com.progallery.app.ui.screens.archive

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.archive.ArchiveEntry
import com.progallery.app.data.archive.ArchiveExtractor
import com.progallery.app.data.archive.ArchiveReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the archive screen shows. */
data class ArchiveUiState(
    val title: String = "Archive",
    val entries: List<ArchiveEntry> = emptyList(),
    val loading: Boolean = true,
    /** Set when the archive cannot be read, in words rather than a stack trace. */
    val error: String? = null,
    /** How far an extraction has got, or null when none is running. */
    val extracting: Pair<Int, Int>? = null,
    val message: String? = null,
)

/**
 * Browsing and unpacking an archive.
 *
 * The listing is read **streaming**, so opening a two-gigabyte zip shows its contents rather than
 * running out of memory. Extraction needs a folder the person chose, because a `.zip` cannot be
 * unpacked into `MediaStore` — it has no folders to unpack into — and the screen asks for one rather
 * than picking silently.
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val extractor: ArchiveExtractor,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val uriString: String = savedState.get<String>("uri").orEmpty()
    private val titleArg: String = savedState.get<String>("title").orEmpty()

    private val _state = MutableStateFlow(ArchiveUiState(title = titleArg.ifBlank { "Archive" }))
    val uiState: StateFlow<ArchiveUiState> = _state.asStateFlow()

    private val archiveUri: Uri? = runCatching { Uri.parse(uriString) }.getOrNull()

    init {
        load()
    }

    fun load() {
        val uri = archiveUri ?: run {
            _state.update { it.copy(loading = false, error = "That archive has no address.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ArchiveReader.list {
                        extractorOpen(uri)
                    }
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { entries -> it.copy(entries = entries, loading = false) },
                    onFailure = { failure ->
                        it.copy(loading = false, error = failure.message ?: "That archive could not be read.")
                    },
                )
            }
        }
    }

    /**
     * Unpacks into a folder the person picked.
     *
     * The tree is taken as a parameter rather than remembered: unpacking somewhere is a deliberate
     * act, and a remembered destination would silently scatter a second archive into the folder the
     * first one went to.
     */
    fun extractInto(treeUri: Uri) {
        val uri = archiveUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(extracting = 0 to _state.value.entries.count { !it.isDirectory }, message = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        android.provider.DocumentsContract.getTreeDocumentId(treeUri),
                    )
                    extractor.extract(
                        open = { extractorOpen(uri) },
                        parentDocumentUri = parent,
                        onProgress = { written, total ->
                            _state.update { it.copy(extracting = written to total) }
                        },
                    )
                }
            }
            _state.update {
                val summary = result.fold(
                    onSuccess = { r ->
                        buildString {
                            append("Extracted ${r.written} of ${r.written + r.skipped} files")
                            if (r.failures.isNotEmpty()) {
                                append(". ")
                                append(r.failures.take(2).joinToString("; "))
                            } else if (r.skipped > 0) {
                                append(". ${r.skipped} could not be written.")
                            }
                        }
                    },
                    onFailure = { f -> "Extraction failed: ${f.message ?: "unknown reason"}" },
                )
                it.copy(extracting = null, message = summary)
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Opening the archive again for a second pass. Streaming, so nothing is held between them. */
    private fun extractorOpen(uri: Uri): java.io.InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
}
