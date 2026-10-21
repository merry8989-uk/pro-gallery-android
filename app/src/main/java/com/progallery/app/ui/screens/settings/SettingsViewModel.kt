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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.progallery.app.data.settings.SettingsDependencies
import com.progallery.app.data.diagnostics.DiagnosticLog
import com.progallery.app.BuildConfig
import com.progallery.app.data.update.UpdateCheck
import com.progallery.app.data.update.UpdateChecker
import com.progallery.app.data.update.UpdateDownloader
import com.progallery.app.data.update.UpdateInstaller
import com.progallery.app.data.usage.UsageDigest
import com.progallery.app.data.usage.UsageStore

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val thumbnailCache: ThumbnailCacheManager,
    private val diagnosticLog: DiagnosticLog,
    private val updateChecker: UpdateChecker,
    private val updateDownloader: UpdateDownloader,
    private val updateInstaller: UpdateInstaller,
    private val usageStore: UsageStore,
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

    /**
     * The update check.
     *
     * There is no automatic check and no background request: the app asks the releases feed
     * only when the user presses the button. Nothing about the device is sent — the request
     * is a plain GET for the project's latest release.
     */
    /** What the in-app download is doing, so the dialog can show it rather than a browser. */
    sealed interface UpdateDownloadUi {
        data object Idle : UpdateDownloadUi

        /** [percent] is -1 when the server did not say how large the file is. */
        data class Working(val percent: Int) : UpdateDownloadUi
        data class Ready(val file: java.io.File, val versionName: String) : UpdateDownloadUi
        data class Failed(val reason: String) : UpdateDownloadUi
    }

    data class UpdateUiState(
        val checking: Boolean = false,
        val download: UpdateDownloadUi = UpdateDownloadUi.Idle,
        /** Whether Android will let this app ask for an install yet. */
        val canInstallPackages: Boolean = false,
        val result: UpdateCheck? = null,
        val currentVersion: String = BuildConfig.VERSION_NAME,
        /**
         * True when this copy was installed by Play, where updates arrive on their own and
         * downloading an APK by hand would fight the store.
         */
        val installedFromPlay: Boolean = false,
    )

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** Asks the releases feed whether a newer version exists. */
    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.update {
                it.copy(
                    checking = true,
                    download = UpdateDownloadUi.Idle,
                    canInstallPackages = updateInstaller.canInstallPackages(),
                )
            }
            val result = updateChecker.check(BuildConfig.VERSION_NAME)
            _updateState.update {
                it.copy(
                    checking = false,
                    result = result,
                    installedFromPlay = installedFromPlay(),
                )
            }
        }
    }

    /** The seven-day review, loaded when it is asked for. */
    private val _usageDigest = MutableStateFlow<UsageDigest?>(null)
    val usageDigest: StateFlow<UsageDigest?> = _usageDigest.asStateFlow()

    fun loadUsageDigest() {
        viewModelScope.launch { _usageDigest.value = usageStore.digest() }
    }

    fun clearUsageDigest() {
        _usageDigest.value = null
    }

    /** Writes the local usage file where the user chooses. Nothing is sent anywhere. */
    fun exportUsageTo(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(usageStore.asText().toByteArray())
                }
            }.onFailure {
                _settingsMessage.value = "The usage file could not be written."
            }
        }
    }

    fun clearUsage() {
        viewModelScope.launch {
            usageStore.clear()
            _usageDigest.value = null
            _settingsMessage.value = "Usage data deleted."
        }
    }

    fun dismissUpdateCheck() {
        _updateState.update { it.copy(result = null, download = UpdateDownloadUi.Idle) }
    }

    /**
     * Fetches the update **here**, rather than handing the address to a browser.
     *
     * The file is checked against this app's own signing key before it is offered, which is
     * only possible because it is fetched in the app.
     */
    fun downloadUpdate() {
        val release = (updateState.value.result as? UpdateCheck.Available)?.release ?: return
        val url = release.apkUrl ?: return

        viewModelScope.launch {
            _updateState.update { it.copy(download = UpdateDownloadUi.Working(0)) }

            when (val outcome = updateDownloader.download(url, release.displayVersion) { percent ->
                _updateState.update { it.copy(download = UpdateDownloadUi.Working(percent)) }
            }) {
                is UpdateDownloader.Outcome.Ready -> _updateState.update {
                    it.copy(
                        download = UpdateDownloadUi.Ready(outcome.file, outcome.versionName),
                        canInstallPackages = updateInstaller.canInstallPackages(),
                    )
                }

                is UpdateDownloader.Outcome.Failed -> _updateState.update {
                    it.copy(download = UpdateDownloadUi.Failed(outcome.reason))
                }
            }
        }
    }

    /** Hands the verified file to Android's own installer. */
    fun installDownloaded() {
        val ready = (updateState.value.download as? UpdateDownloadUi.Ready) ?: return
        updateInstaller.install(ready.file).onFailure { failure ->
            _updateState.update {
                it.copy(
                    download = UpdateDownloadUi.Failed(
                        failure.message ?: "The installer could not be opened.",
                    ),
                )
            }
        }
    }

    /** Re-read whether installs are allowed, for when the user returns from the setting. */
    fun refreshInstallPermission() {
        _updateState.update { it.copy(canInstallPackages = updateInstaller.canInstallPackages()) }
    }

    /** The system screen where "install unknown apps" is turned on for this app. */
    fun installPermissionIntent(): android.content.Intent = updateInstaller.installPermissionSettings()

    /** Whether the store owns this install, so an update should come from there. */
    private fun installedFromPlay(): Boolean = runCatching {
        val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_PACKAGE
    }.getOrDefault(false)

    private companion object {
        const val PLAY_PACKAGE = "com.android.vending"
    }

    private val _settingsMessage = MutableStateFlow<String?>(null)
    val settingsMessage: StateFlow<String?> = _settingsMessage.asStateFlow()

    fun consumeSettingsMessage() {
        _settingsMessage.value = null
    }

    /** Writes the diagnostics log to the file the user chose. */
    fun saveDiagnosticsTo(uri: Uri) {
        viewModelScope.launch {
            val enabled = diagnosticLog.isEnabled()
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    if (!enabled) {
                        error("Diagnostics are switched off, so there is nothing to save.")
                    }
                    val text = diagnosticLog.read()
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: error("the chosen file could not be opened")
                    out.use { it.write(text.toByteArray()) }
                }
            }
            _settingsMessage.value = outcome.fold(
                onSuccess = { "Diagnostics saved" },
                onFailure = { "Could not save diagnostics: ${it.message ?: "unknown reason"}" },
            )
        }
    }

    /** Empties the diagnostics log. */
    fun clearDiagnostics() {
        viewModelScope.launch {
            diagnosticLog.clear()
            _settingsMessage.value = "Diagnostics cleared"
        }
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

    /**
     * Applies a change and, when the dependency rules had to switch something off with
     * it, says so. A switch that flips on its own without explanation looks like a bug.
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = settingsRepository.current()
            settingsRepository.update(transform)
            val after = settingsRepository.current()
            val notes = SettingsDependencies.explain(before, after)
            if (notes.isNotEmpty()) {
                _settingsMessage.value = notes.joinToString(" ")
            }
        }
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
