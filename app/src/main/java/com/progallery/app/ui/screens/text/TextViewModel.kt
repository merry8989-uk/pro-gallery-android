package com.progallery.app.ui.screens.text

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.text.JsonPretty
import com.progallery.app.data.text.TextDecoder
import com.progallery.app.data.text.TextFormats
import com.progallery.app.data.text.TextKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.progallery.app.data.documents.DocumentFormats
import com.progallery.app.data.documents.DocumentText
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName
import com.progallery.app.data.eventlog.OpenFailure

data class TextUiState(
    val loading: Boolean = true,
    val kind: TextKind = TextKind.PLAIN,
    val text: String = "",
    /** True when the tidied form is on screen. */
    val tidy: Boolean = false,
    val encoding: String = "",
    /** True when the encoding had to be guessed, so the screen can say so. */
    val guessed: Boolean = false,
    /** True when the file was longer than the reader holds. */
    val truncated: Boolean = false,
    /** Set when the file could not be read as text at all. */
    val error: String? = null,
    /** Set when tidying was asked for and the file turned out not to be tidy-able. */
    val tidyError: String? = null,
) {
    /** Whether tidying is worth offering for this file. */
    val canTidy: Boolean get() = TextFormats.canTidy(kind)
}

/**
 * Reads a document as text.
 *
 * The app had no reader for these at all: `.json`, `.csv`, `.log` and a hundred code
 * extensions were classified as documents and then handed to "open with another app", which
 * frequently found nothing. Reading them is cheap and is the difference between a screen of
 * icons and a screen of files you can actually open.
 */
@HiltViewModel
class TextViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventLog: EventLogStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TextUiState())
    val state: StateFlow<TextUiState> = _state.asStateFlow()

    /** The original text, kept so tidying can be turned off again. */
    private var original: String = ""

    fun load(uri: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, tidy = false, tidyError = null) }

            val extension = extensionOf(name)
            val kind = TextFormats.kind(extension)
            val bytes = withContext(Dispatchers.IO) { readBounded(uri) }

            if (bytes == null) {
                // Said on the screen **and** recorded, because a diagnostics export is the one thing
                // meant to explain what is broken, and this failure used to leave no trace in it.
                viewModelScope.launch {
                    eventLog.log(
                        EventName.ERROR_STATE_SHOWN,
                        screen = "TextViewer",
                        details = OpenFailure.details(
                            viewer = "text",
                            extension = extension,
                            reason = "the file could not be read",
                            uriReadable = false,
                        ),
                    )
                }
                _state.update {
                    it.copy(loading = false, kind = kind, error = "This file could not be opened.")
                }
                return@launch
            }

            // A document is a zip container, so it goes through its own reader rather than being
            // decoded as bytes — the text reader refuses these formats for exactly that reason.
            if (DocumentFormats.canRead(extension)) {
                val document = withContext(Dispatchers.IO) {
                    runCatching { DocumentText.read(extension, bytes) }
                }
                document.fold(
                    onSuccess = { text ->
                        original = text
                        _state.update {
                            it.copy(
                                loading = false,
                                kind = TextKind.DOCUMENT,
                                text = text,
                                // No encoding to report: the text was extracted from XML, not
                                // decoded from bytes, and saying "UTF-8" here would be noise.
                                encoding = "",
                                guessed = false,
                                truncated = false,
                            )
                        }
                    },
                    onFailure = { failure ->
                        _state.update {
                            it.copy(
                                loading = false,
                                kind = TextKind.DOCUMENT,
                                error = failure.message ?: "This document could not be read.",
                            )
                        }
                    },
                )
                return@launch
            }

            TextDecoder.decode(bytes).fold(
                onSuccess = { decoded ->
                    original = decoded.text
                    _state.update {
                        it.copy(
                            loading = false,
                            kind = kind,
                            text = decoded.text,
                            encoding = decoded.encoding,
                            guessed = decoded.guessed,
                            truncated = decoded.truncated,
                        )
                    }
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            kind = kind,
                            error = failure.message ?: "This file could not be read as text.",
                        )
                    }
                },
            )
        }
    }

    /** Turns the tidied form on or off. */
    fun toggleTidy() {
        val current = _state.value
        if (!current.canTidy) return

        if (current.tidy) {
            _state.update { it.copy(tidy = false, text = original, tidyError = null) }
            return
        }

        JsonPretty.indent(original).fold(
            onSuccess = { pretty ->
                _state.update { it.copy(tidy = true, text = pretty, tidyError = null) }
            },
            onFailure = { failure ->
                // Tidying a file that is not valid JSON would mangle it, so it is refused and
                // the reason is shown rather than the file being reformatted wrongly.
                _state.update {
                    it.copy(tidyError = failure.message ?: "This file could not be tidied.")
                }
            },
        )
    }

    fun consumeTidyError() {
        _state.update { it.copy(tidyError = null) }
    }

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0 || dot == name.length - 1) "" else name.substring(dot + 1)
    }

    /**
     * Reads at most what the reader holds, plus one byte so truncation can be detected.
     *
     * Bounded on purpose: a gigabyte of logs must not be pulled into memory to show the first
     * screenful of it.
     */
    private fun readBounded(uri: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            val buffer = ByteArray(TextDecoder.MAX_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            buffer.copyOf(total)
        }
    }.getOrNull()
}
