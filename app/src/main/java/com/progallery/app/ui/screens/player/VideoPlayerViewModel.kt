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
    fun reportPlaybackFailure(errorCodeName: String) {
        _message.value = "This video could not be played ($errorCodeName). The codec inside " +
            "it may not be supported by this device."
    }

    fun consumeMessage() {
        _message.value = null
    }
}
