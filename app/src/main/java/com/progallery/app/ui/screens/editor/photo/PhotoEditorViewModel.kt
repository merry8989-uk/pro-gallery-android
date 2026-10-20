package com.progallery.app.ui.screens.editor.photo

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.edit.photo.BitmapEditor
import com.progallery.app.edit.photo.CropRect
import com.progallery.app.edit.photo.EditParams
import com.progallery.app.edit.photo.ExportFormat
import com.progallery.app.edit.photo.PhotoEditState
import com.progallery.app.edit.photo.PhotoExporter
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoEditorUiState(
    val source: Bitmap? = null,
    val preview: Bitmap? = null,
    val editState: PhotoEditState = PhotoEditState(),
    val showOriginal: Boolean = false,
    val loading: Boolean = true,
    val exporting: Boolean = false,
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 95,
    val message: String? = null,
    val error: String? = null,
    /** MIME type of the file being edited, used to guard in-place overwrites. */
    val originalMimeType: String? = null,
    /** Write consent awaiting the platform's result, when overwriting. */
    val pendingWrite: PendingIntent? = null,
) {
    val params: EditParams get() = editState.current
    val displayed: Bitmap? get() = if (showOriginal) source else preview

    /**
     * Overwriting in place is only safe when the output format matches the
     * original file's format: rewriting a JPEG as PNG would leave the file
     * mislabelled. Anything else must be saved as a copy.
     */
    val canOverwrite: Boolean
        get() = originalMimeType != null && format.mimeType == originalMimeType
}

@HiltViewModel
class PhotoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bitmapEditor: BitmapEditor,
    private val exporter: PhotoExporter,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _state = MutableStateFlow(PhotoEditorUiState())
    val uiState: StateFlow<PhotoEditorUiState> = _state.asStateFlow()

    private var sourceUri: Uri? = null
    private var renderJob: Job? = null

    private val params: EditParams get() = _state.value.editState.current

    fun load(uriString: String) {
        val uri = Uri.parse(java.net.URLDecoder.decode(uriString, "UTF-8"))
        sourceUri = uri
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val bitmap = bitmapEditor.decodeOriented(context, uri, maxDimension = 1600)
            if (bitmap == null) {
                _state.update { it.copy(loading = false, error = "Could not open this image") }
                return@launch
            }
            val originalMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            // Default the export format to the original's, so the safe path is
            // also the default path.
            val defaultFormat = ExportFormat.entries.firstOrNull { it.mimeType == originalMime }
                ?: ExportFormat.JPEG
            _state.update {
                it.copy(
                    source = bitmap,
                    preview = bitmap,
                    loading = false,
                    originalMimeType = originalMime,
                    format = defaultFormat,
                )
            }
        }
    }

    /** Commits new params and re-renders the preview (debounced). */
    fun commit(params: EditParams) {
        _state.update { it.copy(editState = it.editState.commit(params)) }
        scheduleRender()
    }

    fun setBrightness(value: Float) = commit(params.copy(brightness = value))
    fun setContrast(value: Float) = commit(params.copy(contrast = value))
    fun setSaturation(value: Float) = commit(params.copy(saturation = value))
    fun setWarmth(value: Float) = commit(params.copy(warmth = value))
    fun setExposure(value: Float) = commit(params.copy(exposure = value))
    fun setBlur(value: Float) = commit(params.copy(blurRadius = value))
    fun setSharpen(value: Float) = commit(params.copy(sharpenAmount = value))
    fun toggleGrayscale() = commit(params.copy(grayscale = !params.grayscale, sepia = false))
    fun toggleSepia() = commit(params.copy(sepia = !params.sepia, grayscale = false))
    fun rotateLeft() = commit(params.copy(rotationDegrees = (params.rotationDegrees - 90 + 360) % 360))
    fun rotateRight() = commit(params.copy(rotationDegrees = (params.rotationDegrees + 90) % 360))
    fun flipHorizontal() = commit(params.copy(flipHorizontal = !params.flipHorizontal))
    fun flipVertical() = commit(params.copy(flipVertical = !params.flipVertical))

    /**
     * Applies an aspect preset using the source image's real dimensions and the
     * current rotation, so the exported crop actually has the requested ratio.
     */
    fun applyAspect(ratio: Float?) {
        val source = _state.value.source ?: return
        val crop = CropMath.cropRectFor(
            ratio = ratio,
            imageWidth = source.width,
            imageHeight = source.height,
            rotationDegrees = params.rotationDegrees,
        )
        commit(params.copy(crop = crop))
    }

    fun undo() {
        _state.update { it.copy(editState = it.editState.undo()) }
        scheduleRender()
    }

    fun redo() {
        _state.update { it.copy(editState = it.editState.redo()) }
        scheduleRender()
    }

    fun reset() {
        _state.update { it.copy(editState = it.editState.reset()) }
        scheduleRender()
    }

    fun toggleShowOriginal() = _state.update { it.copy(showOriginal = !it.showOriginal) }

    fun setFormat(format: ExportFormat) = _state.update { it.copy(format = format) }
    fun setQuality(quality: Int) = _state.update { it.copy(quality = quality.coerceIn(1, 100)) }

    private fun scheduleRender() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            delay(120)
            val source = _state.value.source ?: return@launch
            val rendered = bitmapEditor.render(source, _state.value.params)
            _state.update { it.copy(preview = rendered) }
        }
    }

    fun export(overwriteOriginal: Boolean = false) {
        if (overwriteOriginal && !_state.value.canOverwrite) {
            // Never rewrite a file in a format that does not match its type.
            _state.update {
                it.copy(
                    message = "Original is ${_state.value.originalMimeType ?: "an unknown format"} — saved as a copy instead",
                )
            }
            exportInternal(overwrite = false)
            return
        }

        if (overwriteOriginal) {
            // Android 11+ requires the user's consent to modify media the app
            // does not own. Ask first; apply only after approval.
            val request = sourceUri?.let { mediaActions.writeRequest(listOf(it)) }
            if (request != null) {
                _state.update { it.copy(pendingWrite = request) }
                return
            }
        }
        exportInternal(overwrite = overwriteOriginal)
    }

    fun onWriteConsent(approved: Boolean) {
        _state.update { it.copy(pendingWrite = null) }
        if (approved) {
            exportInternal(overwrite = true)
        } else {
            _state.update { it.copy(message = "Overwrite cancelled — the original is unchanged") }
        }
    }

    private fun exportInternal(overwrite: Boolean) {
        val source = _state.value.source ?: return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true) }
            // Re-render at full resolution from a fresh decode for maximum quality.
            val full = sourceUri?.let { bitmapEditor.decodeOriented(context, it, maxDimension = 4096) } ?: source
            val rendered = bitmapEditor.render(full, _state.value.params)
            val name = "ProGallery_${System.currentTimeMillis()}"
            val uri = exporter.export(
                bitmap = rendered,
                displayName = name,
                format = _state.value.format,
                quality = _state.value.quality,
                overwriteUri = if (overwrite) sourceUri else null,
            )
            _state.update {
                it.copy(
                    exporting = false,
                    message = when {
                        uri == null -> "Export failed"
                        overwrite -> "Original updated in place"
                        else -> "Saved to Pictures/ProGallery"
                    },
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
