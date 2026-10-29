package com.progallery.app.ui.screens.player

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.progallery.app.data.media.PlaybackFailure
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName
import com.progallery.app.data.playback.PlaybackPositionRepository
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.data.repository.ViewerContext
import com.progallery.app.ui.screens.viewer.ViewerPager
import com.progallery.app.util.MediaActions
import com.progallery.app.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.progallery.app.ui.components.sameUri
import com.progallery.app.data.transfer.CopyProgress
import com.progallery.app.BuildConfig
import com.progallery.app.data.diagnostics.FileOpenDiagnostics

/**
 * The state of the one player.
 *
 * Video and audio share it because they share the player: the difference between them is the
 * surface and the actions offered, not the playback. Keeping two states would be keeping two
 * places for "is it playing" to be wrong.
 */
data class MediaPlayerUiState(
    val item: MediaItem? = null,
    /** What the pager may swipe through. One item when there is nothing to swipe to. */
    val items: List<MediaItem> = emptyList(),
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val failureAction: PlaybackFailure.Action = PlaybackFailure.Action.NONE,
    /** Move consent awaiting the platform's result. */
    val pendingWrite: PendingIntent? = null,
    val pendingWriteTarget: String? = null,
    /** Delete consent awaiting the platform's result. */
    val pendingDelete: PendingIntent? = null,
    val pendingTrashIds: List<Long> = emptyList(),
    /**
     * How far a copy has got, or null when none is running.
     *
     * Shown while it runs, because a large video used to look like a frozen app: the copy was a
     * blocking stream with no feedback and no way to stop it.
     */
    val copying: CopyProgress? = null,
) {
    /** Audio and video are told apart by the file itself, not by which screen opened it. */
    val isAudio: Boolean get() = item?.isAudio == true
}

/**
 * The logic of the one player.
 *
 * **It does not own the player.** The screen does, because the player has to be attached to a
 * `PlayerView`, released with the composition, and read for its position as it goes away — all of
 * which are the screen's to do. What lives here is everything that is not the player: what is
 * playing, what may be swiped to, what went wrong, and what the user can do about it.
 *
 * That split is what lets the audio path keep its queue and its copy/move/delete while the video
 * path keeps its pager and its trim — on one screen, over one player.
 */
@HiltViewModel
class MediaPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val trashRepository: TrashRepository,
    private val mediaActions: MediaActions,
    private val viewerContext: ViewerContext,
    private val playbackPositions: PlaybackPositionRepository,
    private val eventLog: EventLogStore,
) : ViewModel() {

    /** One id for this open attempt, so a record can be followed across events. */
    private val openEventId: String = java.util.UUID.randomUUID().toString()

    private val _state = MutableStateFlow(MediaPlayerUiState())
    val state: StateFlow<MediaPlayerUiState> = _state.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private var collectionJob: Job? = null

    /**
     * Resolves what was opened and what it can be swiped to.
     *
     * The item's own kind decides everything after this: a video joins the grid it was tapped in,
     * an audio file stands alone with its own actions.
     */
    fun configure(uriString: String) {
        collectionJob?.cancel()
        val decoded = runCatching {
            Uri.parse(java.net.URLDecoder.decode(uriString, "UTF-8")).toString()
        }.getOrNull() ?: uriString

        viewModelScope.launch {
            val resolved = repository.currentSnapshot().firstOrNull { it.uri.toString() == decoded }
            _folders.value = runCatching { repository.folders() }.getOrDefault(emptyList())

            if (resolved == null) {
                _state.update {
                    it.copy(loading = false, error = "This file is no longer available")
                }
                return@launch
            }

            _state.update { it.copy(item = resolved, loading = false, error = null) }

            if (!resolved.isVideo) {
                // Audio has no grid to swipe through: it is the file, opened on its own.
                _state.update { it.copy(items = listOf(resolved)) }
                return@launch
            }

            // Prefer exactly what the grid was showing, so swiping follows the order on screen.
            val onScreen = ViewerPager.itemsFor(viewerContext.current(), MediaType.VIDEO)
            if (onScreen.any { sameUri(it.uri.toString(), decoded) }) {
                _state.update { it.copy(items = onScreen) }
                return@launch
            }

            // Opened without a grid behind it: fall back to the library, still videos only.
            collectionJob = viewModelScope.launch {
                repository.observeMedia(MediaType.VIDEO).collect { videos ->
                    _state.update { it.copy(items = ViewerPager.itemsFor(videos, MediaType.VIDEO)) }
                }
            }
        }
    }

    /** The item currently on screen, so the actions act on the right one. */
    fun setCurrent(item: MediaItem?) = _state.update { it.copy(item = item) }

    /** Set once a file has been recorded as opened, so a session records one success, not hundreds. */
    private var openRecorded = false

    fun onPlayingChanged(isPlaying: Boolean) = _state.update { it.copy(playing = isPlaying) }

    fun onProgress(positionMs: Long, durationMs: Long) {
        // A duration means the container was read and a track was found: the file **did** open. That
        // is the denominator the failures are meaningless without, and it is recorded once per
        // attempt rather than on every tick of the progress bar.
        if (!openRecorded && durationMs > 0) {
            openRecorded = true
            recordOpened()
        }
        _state.update {
            it.copy(
                positionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else it.durationMs,
            )
        }
    }

    // ---- position memory -------------------------------------------------------------------

    suspend fun resumeAt(item: MediaItem): Long = playbackPositions.resumeAt(item.mediaKey)

    /** The success record, once per open attempt. */
    private fun recordOpened() {
        val item = _state.value.item
        viewModelScope.launch {
            eventLog.log(
                EventName.FILE_OPENED,
                details = FileOpenDiagnostics.opened(
                    feature = if (item?.isAudio == true) {
                        FileOpenDiagnostics.Feature.AUDIO_PLAYER
                    } else {
                        FileOpenDiagnostics.Feature.VIDEO_PLAYER
                    },
                    extension = item?.extension.orEmpty(),
                    mimeType = item?.mimeType,
                    bytes = item?.sizeBytes,
                    uriScheme = item?.uri?.scheme,
                    stage = FileOpenDiagnostics.Stage.PLAYBACK,
                    appVersion = BuildConfig.VERSION_NAME,
                    eventId = openEventId,
                ),
            )
        }
    }


    /**
     * Reads the position off the player and saves it.
     *
     * Launched on the view model's scope rather than the composition's, because the most important
     * save happens as the screen is going away and a coroutine started from the composition is
     * cancelled at exactly that moment.
     */
    fun savePositionOf(player: Player) {
        val current = _state.value.item ?: return
        val position = runCatching { player.currentPosition }.getOrNull() ?: return
        val duration = runCatching { player.duration }.getOrNull() ?: 0L
        val key = current.mediaKey
        viewModelScope.launch { playbackPositions.save(key, position, if (duration > 0) duration else 0L) }
    }

    // ---- failures --------------------------------------------------------------------------

    /**
     * Reports a playback failure in terms of the actual reason.
     *
     * Every failure used to be blamed on the codec, which was wrong for most of them — a file that
     * could not be opened was described as an unsupported codec. The file's name is included,
     * because a queue of thirty-two files needs to say which one failed.
     */
    fun reportPlaybackFailure(errorCode: Int, fileName: String?) {
        // The extension goes with it, because the message for an unreadable container is now read
        // from the capability matrix — so what the player says and what the capability screen says
        // cannot disagree.
        val item = _state.value.item
        val extension = item?.extension.orEmpty()

        // **What the file actually looks like on disk.** Without this the message had to hedge —
        // "moved or deleted, *or* it is in a folder Android does not let this app read" — because it
        // genuinely did not know. The three causes have three different fixes, and a person reading
        // one sentence cannot tell which they have.
        val fileState = fileStateOf(item?.uri?.toString().orEmpty())
        val explanation = PlaybackFailure.explain(
            errorCode = errorCode,
            extension = extension,
            fileState = fileState,
        )
        viewModelScope.launch {
            // The structured record the diagnostics spec asks for: which feature, which format,
            // which stage, and — only when something actually said why — which reason.
            eventLog.log(
                EventName.FILE_OPEN_FAILED,
                details = FileOpenDiagnostics.failed(
                    feature = if (item?.isAudio == true) {
                        FileOpenDiagnostics.Feature.AUDIO_PLAYER
                    } else {
                        FileOpenDiagnostics.Feature.VIDEO_PLAYER
                    },
                    extension = extension,
                    mimeType = item?.mimeType,
                    bytes = item?.sizeBytes,
                    uriScheme = item?.uri?.scheme,
                    stage = FileOpenDiagnostics.Stage.PLAYBACK,
                    // Media3 told us the code, so this is evidence rather than a guess.
                    reason = explanation.action.name.lowercase() + "_" + errorCode,
                    exception = null,
                    appVersion = BuildConfig.VERSION_NAME,
                    eventId = openEventId,
                ),
            )
            eventLog.log(
                EventName.PLAYBACK_FAILED,
                details = mapOf(
                    "error_code" to errorCode.toString(),
                    "reason" to explanation.action.name.lowercase(),
                    "extension" to extension.lowercase().ifBlank { "unknown" },
                    "kind" to (item?.mediaType?.name ?: "unknown").lowercase(),
                    // The scheme is the difference between an address that works and one that
                    // cannot: `content://` goes through the system index, `file://` is a raw path
                    // that scoped storage may refuse. A scan writes `file://`, and that is the first
                    // thing to look at when a listed video will not open.
                    "scheme" to (item?.uri?.scheme ?: "unknown"),
                    "file_state" to fileState.name.lowercase(),
                    "because" to explanation.reason.take(120),
                ),
            )
        }
        _state.update {
            it.copy(
                playing = false,
                loading = false,
                message = PlaybackFailure.message(errorCode, fileName, fileState = fileState),
                failureAction = explanation.action,
            )
        }
    }

    /**
     * Re-reads the library and tries again.
     *
     * Offered when a file could not be opened: the entry may simply be stale, and re-reading the
     * index is the honest first thing to try.
     */
    fun rescanAndRetry() {
        viewModelScope.launch {
            _state.update { it.copy(failureAction = PlaybackFailure.Action.NONE) }
            runCatchingCancellable { repository.refresh() }
            _state.value.item?.uri?.toString()?.let { configure(it) }
        }
    }

    /** Hands the current file to another app. */
    fun openCurrentWithAnotherApp() {
        val current = _state.value.item ?: return
        _state.update { it.copy(failureAction = PlaybackFailure.Action.NONE) }
        if (!mediaActions.launchOpen(current)) {
            _state.update { it.copy(message = "No installed app can open ${current.displayName}") }
        }
    }

    fun consumeFailureAction() = _state.update { it.copy(failureAction = PlaybackFailure.Action.NONE) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---- actions ---------------------------------------------------------------------------

    fun share() {
        _state.value.item?.let { mediaActions.launchShare(listOf(it)) }
    }

    /** Hands the file to an installed editing app for advanced work. */
    fun advancedEdit() {
        val current = _state.value.item ?: return
        if (!mediaActions.launchAdvancedEdit(current.uri, current.mimeType)) {
            _state.update { it.copy(message = "No installed app can edit ${current.displayName}") }
        }
    }

    fun requestCopy(target: String) {
        val item = _state.value.item ?: return
        // Held so `cancelCopy` has something to cancel. Without this the cancel button would call
        // `cancel()` on null and do nothing at all — which is the shape of a control that appears
        // to work and does not.
        copyJob?.cancel()
        copyJob = viewModelScope.launch {
            _state.update { it.copy(copying = CopyProgress.Starting) }
            // The progress is reported from inside the copy, so the bar moves with the bytes rather
            // than with a timer pretending to.
            val ok = mediaActions.copyTo(item, target) { progress ->
                _state.update { it.copy(copying = progress) }
            }
            _state.update { it.copy(copying = null) }
            copyJob = null
            _state.update {
                it.copy(message = if (ok) "Copied to $target" else "Could not copy to that folder")
            }
            repository.refresh()
        }
    }

    /**
     * Stops a copy that is running.
     *
     * The job is cancelled, and the copy's own `finally` deletes the pending row — so stopping it
     * leaves nothing behind rather than a half-written file that looks like a real one.
     */
    fun cancelCopy() {
        copyJob?.cancel()
        copyJob = null
    }

    private var copyJob: kotlinx.coroutines.Job? = null

    fun requestMove(target: String) {
        val item = _state.value.item ?: return
        val request = mediaActions.writeRequest(listOf(item.uri))
        if (request != null) {
            _state.update { it.copy(pendingWrite = request, pendingWriteTarget = target) }
        } else {
            applyMove(item, target)
        }
    }

    fun onWriteConsent(approved: Boolean) {
        val snapshot = _state.value
        val target = snapshot.pendingWriteTarget
        val item = snapshot.item
        _state.update { it.copy(pendingWrite = null, pendingWriteTarget = null) }
        when {
            approved && target != null && item != null -> applyMove(item, target)
            !approved -> _state.update { it.copy(message = "Move cancelled — nothing changed") }
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
     * Deletes the same recoverable way as the rest of the app: copy the bytes into the trash
     * first, then ask the platform to remove the original. A declined or failed removal rolls the
     * copy back.
     */
    fun requestDelete() {
        val item = _state.value.item ?: return
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
                    it.copy(
                        pendingDelete = request,
                        pendingTrashIds = outcome.succeeded.map { it.trashId },
                    )
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

    /**
     * Whether the file behind an address is there, readable, or gone.
     *
     * A `content://` address has no path to check, so it is reported as unknown rather than guessed
     * at. A `file://` address can be checked directly, and that check is the difference between
     * "re-scan, the address is stale" and "grant the folder".
     */
    private fun fileStateOf(uri: String): PlaybackFailure.FileState {
        val parsed = runCatching { android.net.Uri.parse(uri) }.getOrNull() ?: return PlaybackFailure.FileState.Unknown
        if (parsed.scheme != "file") return PlaybackFailure.FileState.Unknown
        val path = parsed.path ?: return PlaybackFailure.FileState.Unknown
        val file = java.io.File(path)
        return when {
            !file.exists() -> PlaybackFailure.FileState.Missing
            !file.canRead() -> PlaybackFailure.FileState.Unreadable
            else -> PlaybackFailure.FileState.Exists
        }
    }
}
