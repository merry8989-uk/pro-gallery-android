package com.progallery.app.ui.screens.scan

import android.content.Context
import com.progallery.app.data.androidfolders.AndroidFolderScanner
import com.progallery.app.data.androidfolders.AndroidFolderTarget
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.data.scan.ScanScope
import com.progallery.app.data.scan.ScanScopeBuilder
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.util.PermissionUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The scope, worked out from the settings **and the device**.
 *
 * The device half is what makes this worth having. The settings say what the person asked for; the
 * granted folders say what they actually gave the app; and the reachability check says what this
 * Android version will let it read. A scope built from the settings alone would promise to walk
 * folders that are not granted and to read `Android/media` on devices that refuse it.
 */
@HiltViewModel
class ScanScopeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scanDirectoryDao: ScanDirectoryDao,
    private val folderScanner: AndroidFolderScanner,
) : ViewModel() {

    val scope: StateFlow<ScanScope?> = combine(
        settingsRepository.settings,
        scanDirectoryDao.observeAll(),
    ) { settings, folders ->
        val allFiles = PermissionUtils.hasAllFilesAccess(context)
        val androidMedia = folderScanner
            .reachability()
            .firstOrNull { it.target == AndroidFolderTarget.MEDIA }
            ?.reachable
            ?: false
        ScanScopeBuilder.build(
            settings = settings,
            grantedFolders = folders.map { it.displayName },
            allFilesAccess = allFiles,
            androidMediaReachable = androidMedia,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
