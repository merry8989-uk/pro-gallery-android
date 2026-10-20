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

@HiltViewModel
class SettingsViewModel @Inject constructor(
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
