package com.progallery.app.ui.screens.audio

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.ui.components.FileActionBar
import com.progallery.app.ui.components.FileActionSpec
import com.progallery.app.ui.components.FileInfoDialog
import com.progallery.app.ui.components.FolderPickerDialog
import com.progallery.app.util.MediaActions
import com.progallery.app.util.MediaFormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.media3.common.PlaybackException
import com.progallery.app.data.media.PlaybackFailure

data class AudioPlayerUiState(
    val item: MediaItem? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val pendingWrite: PendingIntent? = null,
    val pendingWriteTarget: String? = null,
    /** Delete consent awaiting the platform's result. */
    val pendingDelete: PendingIntent? = null,
    val pendingTrashIds: List<Long> = emptyList(),
)

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaActions: MediaActions,
    private val repository: MediaRepository,
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _state.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private var player: ExoPlayer? = null

    fun load(uriString: String) {
        if (player != null) return
        viewModelScope.launch {
            val uri = Uri.parse(java.net.URLDecoder.decode(uriString, "UTF-8"))
            val item = repository.currentSnapshot()
                .firstOrNull { it.uri.toString() == uri.toString() }
            _state.update { it.copy(item = item, loading = item == null, error = if (item == null) "This audio file is no longer available" else null) }
            _folders.value = repository.folders()

            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    val duration = exo.duration.takeIf { it > 0 } ?: 0L
                    _state.update { it.copy(durationMs = duration) }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying) }
                }

                /**
                 * A file that cannot be decoded used to fail in silence: the player sat
                 * there doing nothing and the user had no way to tell why. The real reason
                 * is shown instead.
                 */
                override fun onPlayerError(error: PlaybackException) {
                    _state.update {
                        it.copy(
                            playing = false,
                            loading = false,
                            error = PlaybackFailure.message(error.errorCode, item?.displayName),
                        )
                    }
                }
            })
            exo.prepare()

            // Poll the position while playing; ExoPlayer has no position callback.
            while (true) {
                delay(500)
                val current = player ?: break
                _state.update {
                    it.copy(
                        positionMs = current.currentPosition,
                        durationMs = current.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                    )
                }
            }
        }
    }

    fun togglePlay() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun share(item: MediaItem) = mediaActions.launchShare(listOf(item))

    fun requestCopy(item: MediaItem, target: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { mediaActions.copyTo(item, target) }
            _state.update {
                it.copy(message = if (ok) "Copied to $target" else "Could not copy to that folder")
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
            _state.update { it.copy(message = if (ok) "Moved to $target" else "Move failed") }
            repository.refresh()
        }
    }

    /**
     * Deletes audio the same recoverable way as the rest of the app: copy the
     * bytes into the trash first, then ask the platform to remove the original.
     * A declined or failed removal rolls the copy back.
     */
    fun requestDelete(item: MediaItem) {
        viewModelScope.launch {
            val outcome = trashRepository.moveToTrash(listOf(item))
            if (outcome.succeeded.isEmpty()) {
                _state.update {
                    it.copy(
                        message = "Could not copy to Recently Deleted, so nothing was deleted. " +
                            outcome.explain(),
                    )
                }
                return@launch
            }
            val request = mediaActions.deleteRequest(listOf(item))
            if (request != null) {
                _state.update {
                    it.copy(pendingDelete = request, pendingTrashIds = outcome.succeeded.map { it.trashId })
                }
            } else {
                val ok = mediaActions.deleteDirect(listOf(item))
                if (ok) {
                    _state.update { it.copy(message = "Moved to Recently Deleted") }
                } else {
                    trashRepository.discard(outcome.succeeded.map { it.trashId })
                    _state.update { it.copy(message = "Could not delete — nothing was changed") }
                }
                repository.refresh()
            }
        }
    }

    fun onDeleteConsent(approved: Boolean) {
        val trashIds = _state.value.pendingTrashIds
        _state.update { it.copy(pendingDelete = null, pendingTrashIds = emptyList()) }
        if (approved) {
            _state.update { it.copy(message = "Moved to Recently Deleted") }
            viewModelScope.launch { repository.refresh() }
        } else {
            viewModelScope.launch {
                trashRepository.discard(trashIds)
                _state.update { it.copy(message = "Deletion cancelled — nothing was changed") }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        player?.release()
        player = null
        super.onCleared()
    }
}

/**
 * In-app audio player with the requested actions: edit, cut, share, move, copy
 * and info. "Edit" and "Cut" both open the trim editor — Edit lands on the full
 * editor, Cut goes straight to the cut controls.
 */
@Composable
fun AudioPlayerScreen(
    uri: String,
    onBack: () -> Unit,
    onEdit: (String, Boolean) -> Unit,
    viewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var copyOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    LaunchedEffect(uri) { viewModel.load(uri) }

    val writeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onWriteConsent(result.resultCode == android.app.Activity.RESULT_OK) }
    LaunchedEffect(state.pendingWrite) {
        state.pendingWrite?.let {
            writeLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(it.intentSender).build(),
            )
        }
    }
    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeleteConsent(result.resultCode == android.app.Activity.RESULT_OK) }
    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let {
            deleteLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(it.intentSender).build(),
            )
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    val item = state.item

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = item?.displayName ?: "Audio",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text(state.error!!, textAlign = TextAlign.Center)
                    else -> {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(96.dp),
                        )
                        Text(
                            text = item?.displayName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                        )
                        Text(
                            text = item?.let { MediaFormatUtils.formatBytes(it.sizeBytes) }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        IconButton(
                            onClick = { viewModel.togglePlay() },
                            modifier = Modifier.padding(top = 16.dp).size(72.dp),
                        ) {
                            Icon(
                                imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.playing) "Pause" else "Play",
                                modifier = Modifier.size(52.dp),
                            )
                        }
                    }
                }
            }

            if (state.error == null && item != null) {
                val duration = state.durationMs.coerceAtLeast(1)
                val position = if (seeking) seekValue.toLong() else state.positionMs
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = {
                            seeking = true
                            seekValue = it
                        },
                        onValueChangeFinished = {
                            viewModel.seekTo(seekValue.toLong())
                            seeking = false
                        },
                        valueRange = 0f..duration.toFloat(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = MediaFormatUtils.formatDuration(position),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = MediaFormatUtils.formatDuration(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                FileActionBar(
                    actions = listOf(
                        FileActionSpec(Icons.Filled.Edit, "Edit") { onEdit(item.uri.toString(), false) },
                        FileActionSpec(Icons.Filled.ContentCut, "Cut") { onEdit(item.uri.toString(), true) },
                        FileActionSpec(Icons.Filled.Share, "Share") { viewModel.share(item) },
                        FileActionSpec(Icons.Filled.DriveFileMove, "Move") { moveOpen = true },
                        FileActionSpec(Icons.Filled.ContentCopy, "Copy") { copyOpen = true },
                        FileActionSpec(Icons.Filled.Info, "Info") { infoOpen = true },
                        FileActionSpec(Icons.Filled.Delete, "Delete") { viewModel.requestDelete(item) },
                    ),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (copyOpen && item != null) {
        FolderPickerDialog(
            title = "Copy to folder",
            folders = folders,
            currentPath = item.relativePath ?: item.bucketName,
            confirmLabel = "Copy",
            onDismiss = { copyOpen = false },
            onConfirm = { target -> viewModel.requestCopy(item, target); copyOpen = false },
        )
    }
    if (moveOpen && item != null) {
        FolderPickerDialog(
            title = "Move to folder",
            folders = folders,
            currentPath = item.relativePath ?: item.bucketName,
            confirmLabel = "Move",
            onDismiss = { moveOpen = false },
            onConfirm = { target -> viewModel.requestMove(item, target); moveOpen = false },
        )
    }
    if (infoOpen && item != null) {
        FileInfoDialog(item = item, onDismiss = { infoOpen = false })
    }
}
