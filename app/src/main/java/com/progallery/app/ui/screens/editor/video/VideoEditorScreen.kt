package com.progallery.app.ui.screens.editor.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.progallery.app.edit.video.ResolutionPreset
import com.progallery.app.util.MediaFormatUtils
import androidx.media3.common.util.UnstableApi
import com.progallery.app.data.playback.rememberPlaybackEngine

// Two different opt-in mechanisms are at work here: Material 3 uses Kotlin's
// @RequiresOptIn, Media3's UnstableApi uses the AndroidX one. Both annotations are
// named OptIn, so they are written fully qualified — importing one would shadow the
// other and silently disable it.
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    uri: String,
    onBack: () -> Unit,
    viewModel: VideoEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uri) { viewModel.load(uri) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    // Through the engine, so a preview here falls back to a software decoder exactly as the
    // player does — an editor preview is still a player, and it had the same missing fallback.
    val engine = rememberPlaybackEngine()
    val player = remember { engine.build() }
    LaunchedEffect(state.uri) {
        state.uri?.let {
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Video Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )

            Text(
                "Trim: ${MediaFormatUtils.formatDuration(state.edit.trimStartMs)} – " +
                    MediaFormatUtils.formatDuration(state.edit.trimEndMs) +
                    " (${MediaFormatUtils.formatDuration(state.edit.trimDurationMs)})",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.durationMs > 0) {
                RangeSlider(
                    value = state.edit.trimStartMs.toFloat()..state.edit.trimEndMs.toFloat(),
                    onValueChange = { range ->
                        viewModel.setTrimRange(range.start.toLong(), range.endInclusive.toLong())
                    },
                    valueRange = 0f..state.durationMs.toFloat(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mute audio", modifier = Modifier.weight(1f))
                Switch(checked = state.edit.mute, onCheckedChange = { viewModel.setMute(it) })
            }

            Text("Rotation", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 90, 180, 270).forEach { degrees ->
                    FilterChip(
                        onClick = { viewModel.setRotation(degrees) },
                        selected = state.edit.rotationDegrees == degrees,
                        label = { Text("$degrees°") },
                    )
                }
            }

            Text("Resolution", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResolutionPreset.entries.forEach { preset ->
                    FilterChip(
                        onClick = { viewModel.setResolution(preset) },
                        selected = state.edit.resolution == preset,
                        label = { Text(preset.label) },
                    )
                }
            }

            if (state.exporting) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Exporting… ${state.progress}%")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.startExport() }, enabled = !state.exporting) { Text("Export") }
                if (state.exporting) {
                    OutlinedButton(onClick = { viewModel.cancelExport() }) { Text("Cancel") }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "Export writes an H.264 MP4 to Movies/ProGallery. Unsupported codecs are reported above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
