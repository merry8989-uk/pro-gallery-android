package com.progallery.app.ui.screens.appmedia

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.androidfolders.AndroidFolderPolicy
import com.progallery.app.data.androidfolders.AndroidFolderTarget
import com.progallery.app.data.androidfolders.Reachability
import com.progallery.app.data.appmedia.AppMediaDetector
import com.progallery.app.data.appmedia.AppMediaFolder
import com.progallery.app.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppMediaUiState(
    val loading: Boolean = true,
    val apps: List<AppMediaFolder> = emptyList(),
    /** Whether Android/media can be read here at all, and why not when it cannot. */
    val mediaReachability: Reachability? = null,
    val error: String? = null,
) {
    val withMedia: List<AppMediaFolder> get() = apps.filter { it.hasMedia }
    val withoutMedia: List<AppMediaFolder> get() = apps.filter { !it.hasMedia }
}

/**
 * Shows which messaging apps keep media on this device, and how much.
 *
 * The question it answers is the one a person actually has: *is my Telegram/Arattai/Hyped media
 * here or not* — rather than a folder tree they have to interpret.
 */
@HiltViewModel
class AppMediaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: AppMediaDetector,
) : ViewModel() {

    private val _state = MutableStateFlow(AppMediaUiState())
    val state: StateFlow<AppMediaUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val reachability = AndroidFolderPolicy
                .evaluate(hasAllFilesAccess = PermissionUtils.hasAllFilesAccess(context))
                .firstOrNull { it.target == AndroidFolderTarget.MEDIA }

            runCatching { detector.detect() }.fold(
                onSuccess = { apps ->
                    _state.update {
                        it.copy(loading = false, apps = apps, mediaReachability = reachability)
                    }
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            mediaReachability = reachability,
                            error = failure.message ?: "App media could not be read.",
                        )
                    }
                },
            )
        }
    }
}
