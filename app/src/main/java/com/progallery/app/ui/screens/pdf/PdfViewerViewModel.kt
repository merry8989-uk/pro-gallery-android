package com.progallery.app.ui.screens.pdf

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.ui.components.sameUri
import com.progallery.app.util.FileUtils
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PdfUiState(
    val item: MediaItem? = null,
    val pageCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    /** Write consent awaiting the platform's result, for Move. */
    val pendingWrite: PendingIntent? = null,
    val pendingWriteTarget: String? = null,
)

/**
 * Renders a PDF with the platform [PdfRenderer], so no third-party PDF library
 * is needed. Pages are rendered on demand and the renderer is closed with the
 * ViewModel.
 */
@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaActions: MediaActions,
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _state.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    /** PdfRenderer is not thread-safe: one page at a time. */
    private val renderMutex = Mutex()

    fun load(uriString: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val uri = Uri.parse(java.net.URLDecoder.decode(uriString, "UTF-8"))
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    _state.update { it.copy(loading = false, error = "Could not open this PDF") }
                    return@launch
                }
                descriptor = pfd
                val pdfRenderer = PdfRenderer(pfd)
                renderer = pdfRenderer
                _state.update {
                    it.copy(
                        pageCount = pdfRenderer.pageCount,
                        loading = false,
                        item = resolveItem(uri),
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(loading = false, error = "This file could not be read as a PDF")
                }
            }
            _folders.value = repository.folders()
        }
    }

    /**
     * Finds the indexed item for this URI so actions carry the right name and
     * MIME type; falls back to a minimal item for a file the index has not seen.
     */
    private suspend fun resolveItem(uri: Uri): MediaItem {
        val indexed = repository.currentSnapshot().firstOrNull { sameUri(it.uri.toString(), uri.toString()) }
        if (indexed != null) return indexed
        return MediaItem(
            id = uri.toString().hashCode().toLong(),
            uri = uri,
            displayName = FileUtils.displayName(context, uri),
            mimeType = context.contentResolver.getType(uri) ?: "application/pdf",
            sizeBytes = FileUtils.size(context, uri),
            dateModifiedSeconds = 0,
            width = 0,
            height = 0,
            durationMs = 0,
            bucketId = 0,
            bucketName = "PDF",
            relativePath = null,
            mediaType = MediaType.DOCUMENT,
            source = MediaSource.MEDIA_STORE,
        )
    }

    /** Renders one page to a bitmap, scaled to [targetWidth] pixels wide. */
    suspend fun renderPage(index: Int, targetWidth: Int = 1400): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val pdfRenderer = renderer ?: return@withLock null
            try {
                pdfRenderer.openPage(index).use { page ->
                    val scale = targetWidth.toFloat() / page.width.toFloat()
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun share(item: MediaItem) = mediaActions.launchShare(listOf(item))

    /** No backend: hands the file to an app that can upload it. */
    fun upload(item: MediaItem): Boolean = mediaActions.launchOpenWithChooser(item, "Upload with")

    fun requestCopy(item: MediaItem, target: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { mediaActions.copyTo(item, target) }
            _state.update {
                it.copy(
                    message = if (ok) "Copied to $target" else "Could not copy — this needs All files access",
                )
            }
            repository.refresh()
        }
    }

    fun requestMove(item: MediaItem, target: String) {
        val request = mediaActions.writeRequest(listOf(item.uri))
        if (request != null) {
            _state.update { it.copy(pendingWrite = request, pendingWriteTarget = target) }
        } else {
            applyMove(item, target)
        }
    }

    fun onWriteConsent(approved: Boolean) {
        val target = _state.value.pendingWriteTarget
        val item = _state.value.item
        _state.update { it.copy(pendingWrite = null, pendingWriteTarget = null) }
        if (approved && target != null && item != null) {
            applyMove(item, target)
        } else if (!approved) {
            _state.update { it.copy(message = "Move cancelled — nothing changed") }
        }
    }

    private fun applyMove(item: MediaItem, target: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { mediaActions.applyMove(item, target) }
            _state.update {
                it.copy(message = if (ok) "Moved to $target" else "Move failed")
            }
            repository.refresh()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
        super.onCleared()
    }
}
