package com.progallery.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.ViewerContext
import com.progallery.app.ui.components.sameUri
import com.progallery.app.ui.screens.viewer.ViewerPager
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.progallery.app.data.media.PlaybackFailure
import com.progallery.app.util.runCatchingCancellable

/**
 * Backs the video player, including moving between videos.
 *
 * The playlist is the videos that were on screen, in the order they were on
 * screen, so sliding moves to the next video in the grid's own line rather than
 * the next one in the library's order.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val mediaActions: MediaActions,
    private val viewerContext: ViewerContext,
) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _item = MutableStateFlow<MediaItem?>(null)
    val item: StateFlow<MediaItem?> = _item.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var collectionJob: Job? = null

    /** Builds the playlist around [uriString], the video the user tapped. */
    fun configure(uriString: String) {
        collectionJob?.cancel()
        val decoded = java.net.URLDecoder.decode(uriString, "UTF-8")

        // Prefer exactly what the grid was showing.
        val onScreen = ViewerPager.itemsFor(viewerContext.current(), MediaType.VIDEO)
        if (onScreen.any { sameUri(it.uri.toString(), decoded) }) {
            _items.value = onScreen
            _item.value = onScreen.firstOrNull { sameUri(it.uri.toString(), decoded) }
            return
        }

        // Opened without a grid behind it: fall back to the library.
        collectionJob = viewModelScope.launch {
            repository.observeMedia(MediaType.VIDEO).collect { videos ->
                val list = ViewerPager.itemsFor(videos, MediaType.VIDEO)
                _items.value = list
                _item.value = list.firstOrNull { sameUri(it.uri.toString(), decoded) } ?: _item.value
            }
        }
    }

    /** The video currently on screen, so the action bar acts on the right one. */
    fun setCurrent(item: MediaItem?) {
        _item.value = item
    }

    fun share() {
        _item.value?.let { mediaActions.launchShare(listOf(it)) }
    }

    /** Hands the video to an installed editing app for advanced work. */
    fun advancedEdit() {
        val current = _item.value ?: return
        val opened = mediaActions.launchAdvancedEdit(current.uri, current.mimeType)
        if (!opened) {
            _message.value = "No installed app can edit ${current.displayName}"
        }
    }

    /**
     * Surfaces a playback failure.
     *
     * The name Media3 gives is technical, so the message says what it means for the user
     * and what the likely cause is, rather than leaving a black screen unexplained.
     */
    /** The action the user was offered for the last failure, if any. */
    private val _failureAction = MutableStateFlow(PlaybackFailure.Action.NONE)
    val failureAction: StateFlow<PlaybackFailure.Action> = _failureAction.asStateFlow()

    /**
     * Reports a playback failure in terms of the actual reason.
     *
     * Every failure used to be blamed on the codec, which was wrong for most of them — a file
     * that could not be opened was described as an unsupported codec. The file's name is
     * included, because a playlist of thirty-two videos needs to say which one failed.
     */
    fun reportPlaybackFailure(errorCode: Int, fileName: String?) {
        val explanation = PlaybackFailure.explain(errorCode)
        _message.value = PlaybackFailure.message(errorCode, fileName)
        _failureAction.value = explanation.action
    }

    /**
     * Re-reads the library and tries the current video again.
     *
     * Offered when a file could not be opened: the entry may simply be stale, and re-reading
     * the index is the honest first thing to try.
     */
    fun rescanAndRetry() {
        viewModelScope.launch {
            _failureAction.value = PlaybackFailure.Action.NONE
            runCatchingCancellable { repository.refresh() }
            configure(_item.value?.uri?.toString() ?: return@launch)
        }
    }

    /** Hands the current video to another app. */
    fun openCurrentWithAnotherApp() {
        val current = _item.value ?: return
        _failureAction.value = PlaybackFailure.Action.NONE
        if (!mediaActions.launchOpen(current)) {
            _message.value = "No installed app can open ${current.displayName}"
        }
    }

    fun consumeFailureAction() {
        _failureAction.value = PlaybackFailure.Action.NONE
    }

    fun consumeMessage() {
        _message.value = null
    }
}
