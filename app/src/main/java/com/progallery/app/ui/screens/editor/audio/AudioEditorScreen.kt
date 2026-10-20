package com.progallery.app.ui.screens.editor.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.edit.MediaExportState
import com.progallery.app.edit.MediaTrimmer
import com.progallery.app.edit.TrimKind
import com.progallery.app.edit.video.VideoEditState
import com.progallery.app.util.MediaFormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AudioEditorUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val durationMs: Long = 0,
    val edit: VideoEditState = VideoEditState(),
    val exporting: Boolean = false,
    val progress: Int = 0,
    val playing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AudioEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trimmer: MediaTrimmer,
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AudioEditorUiState())
    val uiState: StateFlow<AudioEditorUiState> = _state.asStateFlow()

    private var exportJob: Job? = null
    private var player: ExoPlayer? = null

    fun load(uriString: String) {
        if (_state.value.uri != null) return
        viewModelScope.launch {
            val uri = Uri.parse(java.net.URLDecoder.decode(uriString, "UTF-8"))
            val duration = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                    } finally {
                        retriever.release()
                    }
                }.getOrDefault(0L)
            }
            val name = repository.currentSnapshot()
                .firstOrNull { it.uri.toString() == uri.toString() }
                ?.displayName ?: "audio"
            _state.update {
                it.copy(
                    uri = uri,
                    fileName = name,
                    durationMs = duration,
                    edit = VideoEditState(trimStartMs = 0, trimEndMs = duration),
                    error = if (duration <= 0) "Could not read this audio file" else null,
                )
            }
        }
    }

    fun setTrimRange(startMs: Long, endMs: Long) {
        _state.update { it.copy(edit = it.edit.copy(trimStartMs = startMs, trimEndMs = endMs)) }
        // Rebuild the preview so it plays exactly the selected range.
        player?.let { exo ->
            val uri = _state.value.uri ?: return@let
            exo.setMediaItem(clipped(uri, startMs, endMs))
            exo.prepare()
        }
    }

    /** Plays only the selected range, so the trim can be heard before exporting. */
    fun togglePreview() {
        val uri = _state.value.uri ?: return
        val exo = player ?: ExoPlayer.Builder(context).build().also { built ->
            player = built
            built.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying) }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    // Stop at the end of the selection rather than playing on.
                    if (state == Player.STATE_ENDED) _state.update { it.copy(playing = false) }
                }
            })
        }
        if (exo.isPlaying) {
            exo.pause()
        } else {
            val edit = _state.value.edit
            exo.setMediaItem(clipped(uri, edit.trimStartMs, edit.trimEndMs))
            exo.prepare()
            exo.seekTo(0)
            exo.play()
        }
    }

    private fun clipped(uri: Uri, startMs: Long, endMs: Long): androidx.media3.common.MediaItem {
        val builder = androidx.media3.common.MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(if (endMs > 0) endMs else Long.MAX_VALUE)
                    .build(),
            )
        return builder.build()
    }

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
                kind = TrimKind.AUDIO,
            ).collect { exportState ->
                when (exportState) {
                    is MediaExportState.Progress ->
                        _state.update { it.copy(progress = exportState.percent) }
                    is MediaExportState.Success ->
                        _state.update {
                            it.copy(exporting = false, progress = 100, message = "Saved to Music/ProGallery")
                        }
                    is MediaExportState.Failure ->
                        _state.update { it.copy(exporting = false, error = exportState.message) }
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

    override fun onCleared() {
        player?.release()
        player = null
        super.onCleared()
    }
}

/**
 * Audio editor: pick a start and end point and export the trimmed clip. The
 * preview plays exactly the selected range so the cut can be checked by ear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorScreen(
    uri: String,
    startInCutMode: Boolean = false,
    onBack: () -> Unit,
    viewModel: AudioEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uri) { viewModel.load(uri) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }
    DisposableEffect(Unit) { onDispose { } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (startInCutMode) "Cut audio" else "Audio editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = state.fileName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            when {
                state.error != null -> Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                state.durationMs <= 0 -> CircularProgressIndicator()

                else -> {
                    Text(
                        text = "Selection: ${MediaFormatUtils.formatDuration(state.edit.trimStartMs)} – " +
                            MediaFormatUtils.formatDuration(state.edit.trimEndMs) +
                            "  (${MediaFormatUtils.formatDuration(state.edit.trimDurationMs)})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    RangeSlider(
                        value = state.edit.trimStartMs.toFloat()..state.edit.trimEndMs.toFloat(),
                        onValueChange = { range ->
                            viewModel.setTrimRange(range.start.toLong(), range.endInclusive.toLong())
                        },
                        valueRange = 0f..state.durationMs.toFloat(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(MediaFormatUtils.formatDuration(0), style = MaterialTheme.typography.labelSmall)
                        Text(
                            MediaFormatUtils.formatDuration(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    OutlinedButton(onClick = { viewModel.togglePreview() }) {
                        Icon(
                            imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Text(if (state.playing) "  Pause preview" else "  Preview selection")
                    }

                    if (state.exporting) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Exporting… ${state.progress}%")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.startExport() }, enabled = !state.exporting) {
                            Text(if (startInCutMode) "Cut & save" else "Export")
                        }
                        if (state.exporting) {
                            OutlinedButton(onClick = { viewModel.cancelExport() }) { Text("Cancel") }
                        }
                    }

                    Text(
                        "The trimmed clip is saved to Music/ProGallery. The original file is untouched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
