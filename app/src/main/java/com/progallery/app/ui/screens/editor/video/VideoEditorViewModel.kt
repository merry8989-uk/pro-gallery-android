package com.progallery.app.ui.screens.editor.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.edit.video.ResolutionPreset
import com.progallery.app.edit.video.VideoEditState
import com.progallery.app.edit.MediaExportState
import com.progallery.app.edit.MediaTrimmer
import com.progallery.app.edit.TrimKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.progallery.app.util.runCatchingCancellable

data class VideoEditorUiState(
    val uri: Uri? = null,
    val durationMs: Long = 0,
    val edit: VideoEditState = VideoEditState(),
    val exporting: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trimmer: MediaTrimmer,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoEditorUiState())
    val uiState: StateFlow<VideoEditorUiState> = _state.asStateFlow()

    private var exportJob: Job? = null

    /**
     * Shows the preview immediately and reads the duration on an IO thread —
     * `MediaMetadataRetriever` is synchronous and blocking it on the main thread
     * froze the screen on a large file.
     */
    fun load(uriString: String) {
        val uri = Uri.parse(java.net.URLDecoder.decode(uriString, "UTF-8"))
        _state.update { it.copy(uri = uri) }

        viewModelScope.launch {
            val duration = withContext(Dispatchers.IO) { readDurationMs(uri) }
            _state.update {
                it.copy(
                    durationMs = duration,
                    edit = it.edit.copy(trimStartMs = 0, trimEndMs = duration),
                )
            }
        }
    }

    private fun readDurationMs(uri: Uri): Long = runCatchingCancellable {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    fun setTrimRange(startMs: Long, endMs: Long) = _state.update {
        it.copy(edit = it.edit.copy(trimStartMs = startMs, trimEndMs = endMs))
    }

    fun setMute(mute: Boolean) = _state.update { it.copy(edit = it.edit.copy(mute = mute)) }

    fun setRotation(degrees: Int) = _state.update { it.copy(edit = it.edit.copy(rotationDegrees = degrees)) }

    fun setResolution(preset: ResolutionPreset) = _state.update { it.copy(edit = it.edit.copy(resolution = preset)) }

    fun startExport() {
        val uri = _state.value.uri ?: return
        if (exportJob?.isActive == true) return
        val normalized = _state.value.edit.normalized(_state.value.durationMs)
        _state.update { it.copy(exporting = true, progress = 0, error = null) }
        exportJob = viewModelScope.launch {
            trimmer.export(
                source = uri,
                edit = normalized,
                outputName = "ProGallery_${System.currentTimeMillis()}",
                kind = TrimKind.VIDEO,
            ).collect { exportState ->
                when (exportState) {
                    is MediaExportState.Progress ->
                        _state.update { it.copy(progress = exportState.percent) }
                    is MediaExportState.Success ->
                        _state.update {
                            it.copy(
                                exporting = false,
                                progress = 100,
                                message = "Saved to Movies/ProGallery",
                            )
                        }
                    is MediaExportState.Failure ->
                        _state.update {
                            it.copy(exporting = false, error = exportState.message)
                        }
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _state.update { it.copy(exporting = false, progress = 0, message = "Export cancelled") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
