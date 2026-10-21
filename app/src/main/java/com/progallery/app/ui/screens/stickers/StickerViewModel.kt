package com.progallery.app.ui.screens.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.index.IndexedMediaRepository
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.stickers.StickerKind
import com.progallery.app.data.stickers.Stickers
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.progallery.app.util.MediaActions

/** One sticker, with what it is. */
data class StickerEntry(val item: MediaItem, val kind: StickerKind)

data class StickerUiState(
    val loading: Boolean = true,
    val stickers: List<StickerEntry> = emptyList(),
    /** True when the app's own index holds nothing at all, so the scan has not run. */
    val indexEmpty: Boolean = false,
    val error: String? = null,
) {
    val showable: List<StickerEntry> get() = stickers.filter { Stickers.canShow(it.kind) }
    val notShowable: List<StickerEntry> get() = stickers.filterNot { Stickers.canShow(it.kind) }
}

/**
 * The stickers the app has found.
 *
 * Stickers are read from the app's own index, which is what a scan of `Android/media` fills —
 * they are not in the system's index, which is the first of the three reasons they appeared to
 * be missing.
 */
@HiltViewModel
class StickerViewModel @Inject constructor(
    private val indexedMedia: IndexedMediaRepository,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _state = MutableStateFlow(StickerUiState())
    val state: StateFlow<StickerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { indexedMedia.all() }.fold(
                onSuccess = { indexed ->
                    val stickers = indexed
                        .filter { Stickers.isSticker(it.extension, it.relativePath) }
                        .map { StickerEntry(it, Stickers.kind(it.extension, it.relativePath)) }
                    _state.update {
                        it.copy(
                            loading = false,
                            stickers = stickers,
                            indexEmpty = indexed.isEmpty(),
                        )
                    }
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = failure.message ?: "The sticker list could not be read.",
                        )
                    }
                },
            )
        }
    }

    /**
     * Hands a sticker the app cannot draw to another app.
     *
     * Used for the formats there is no renderer for — a `.tgs` is an animation description, not
     * a picture — so the honest answer is a way to open it rather than a blank tile.
     */
    fun openWith(entry: StickerEntry) {
        mediaActions.launchOpen(entry.item)
    }

    /** Whether this entry should open in the player rather than the viewer. */
    fun opensInPlayer(entry: StickerEntry): Boolean =
        entry.kind == StickerKind.VIDEO || entry.item.mediaType == MediaType.VIDEO
}
