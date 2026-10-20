package com.progallery.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.ui.components.sameUri
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves the playing video so the player can offer share and advanced-edit.
 * Basic trimming stays in the in-app editor.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _item = MutableStateFlow<MediaItem?>(null)
    val item: StateFlow<MediaItem?> = _item.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load(uriString: String) {
        viewModelScope.launch {
            val decoded = java.net.URLDecoder.decode(uriString, "UTF-8")
            _item.value = repository.currentSnapshot()
                .firstOrNull { sameUri(it.uri.toString(), decoded) }
        }
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

    fun consumeMessage() {
        _message.value = null
    }
}
