package com.progallery.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.settings.AppSettings
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.util.MediaFormatUtils
import com.progallery.app.util.RootDetector
import com.progallery.app.util.ThumbnailCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val thumbnailCache: ThumbnailCacheManager,
) : ViewModel() {

    private val _cacheSizeBytes = MutableStateFlow(0L)
    /** Bytes currently held in the app's own thumbnail cache. */
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _cacheMessage = MutableStateFlow<String?>(null)
    val cacheMessage: StateFlow<String?> = _cacheMessage.asStateFlow()

    /** Reads the current cache size. Called when the screen appears. */
    fun refreshCacheSize() {
        _cacheSizeBytes.value = thumbnailCache.sizeBytes()
    }

    /** Clears the app's own thumbnail cache. Never touches the user's files. */
    fun clearThumbnailCache() {
        viewModelScope.launch {
            val freed = thumbnailCache.clear()
            _cacheSizeBytes.value = thumbnailCache.sizeBytes()
            _cacheMessage.value = "Freed ${MediaFormatUtils.formatBytes(freed)}"
        }
    }

    fun consumeCacheMessage() {
        _cacheMessage.value = null
    }

    private val _settingsMessage = MutableStateFlow<String?>(null)
    val settingsMessage: StateFlow<String?> = _settingsMessage.asStateFlow()

    fun consumeSettingsMessage() {
        _settingsMessage.value = null
    }

    /** Restores one section to its defaults, leaving every other section alone. */
    fun resetSection(section: String) {
        viewModelScope.launch {
            settingsRepository.update { it.resetSection(section) }
            _settingsMessage.value = "\"$section\" reset to defaults"
        }
    }

    /** Writes the settings to the file the user chose. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val json = settingsRepository.exportJson()
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: error("the chosen file could not be opened")
                    out.use { it.write(json.toByteArray()) }
                }
            }
            _settingsMessage.value = outcome.fold(
                onSuccess = { "Settings exported" },
                onFailure = { "Export failed: ${it.message ?: "unknown reason"}" },
            )
        }
    }

    /**
     * Applies a settings file the user chose.
     *
     * The file is read and validated before anything is written, so a bad file leaves
     * the current settings untouched.
     */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("the chosen file could not be opened")
                    input.use { it.readBytes().decodeToString() }
                }
            }
            val outcome = text.fold(
                onSuccess = { json -> settingsRepository.importJson(json).map { } },
                onFailure = { Result.failure(it) },
            )
            _settingsMessage.value = outcome.fold(
                onSuccess = { "Settings imported" },
                onFailure = { it.message ?: "Import failed" },
            )
        }
    }

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Defaults)

    /** Null until the check has run; never requests root. */
    private val _rootStatus = MutableStateFlow<String?>(null)
    val rootStatus: StateFlow<String?> = _rootStatus.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { settingsRepository.resetToDefaults() }
    }

    fun checkRootStatus() {
        viewModelScope.launch {
            _rootStatus.value = "Checking…"
            _rootStatus.value = RootDetector.describe(RootDetector.isProbablyRooted())
        }
    }
}
