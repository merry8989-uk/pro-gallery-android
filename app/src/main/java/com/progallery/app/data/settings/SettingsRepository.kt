package com.progallery.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.progallery.app.data.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "progallery_settings")

/**
 * Persists [AppSettings] with DataStore Preferences.
 *
 * Every key is typed and has a default, so a missing or newly introduced
 * setting simply falls back rather than throwing. Values read back are run
 * through [AppSettings.sanitized], so a hand-edited or corrupted preference can
 * never push the scanner out of bounds.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { cause ->
            // A corrupt preferences file must not break the app: fall back to
            // defaults rather than crashing on every read.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { read(it).sanitized() }

    /** Current value, for one-shot reads. */
    suspend fun current(): AppSettings = settings.first()

    /** Applies [transform] and persists the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { preferences ->
            val next = transform(read(preferences)).sanitized()
            write(preferences, next)
        }
    }

    /** Restores defaults, keeping the first-run flag so the prompt doesn't return. */
    suspend fun resetToDefaults() {
        context.settingsStore.edit { preferences ->
            write(preferences, AppSettings.Defaults.copy(hasSeenDefaultGalleryPrompt = true))
        }
    }

    suspend fun markDefaultGalleryPromptSeen() {
        update { it.copy(hasSeenDefaultGalleryPrompt = true) }
    }

    // ---------------------------------------------------------------- keys

    private object Keys {
        val advancedMode = booleanPreferencesKey("advanced_mode")

        val quickScan = booleanPreferencesKey("scan_quick")
        val deepScan = booleanPreferencesKey("scan_deep")
        val safScan = booleanPreferencesKey("scan_saf")
        val signatureDetection = booleanPreferencesKey("scan_signature")
        val duplicateDetection = booleanPreferencesKey("scan_duplicates")

        val scanImages = booleanPreferencesKey("kind_images")
        val scanVideos = booleanPreferencesKey("kind_videos")
        val scanAudio = booleanPreferencesKey("kind_audio")
        val scanDocuments = booleanPreferencesKey("kind_documents")
        val scanArchives = booleanPreferencesKey("kind_archives")
        val scanOther = booleanPreferencesKey("kind_other")

        val thumbnails = booleanPreferencesKey("thumbnails")
        val showFileNames = booleanPreferencesKey("show_names")
        val showFileSize = booleanPreferencesKey("show_size")

        val includeHidden = booleanPreferencesKey("include_hidden")
        val includeExtensionless = booleanPreferencesKey("include_extensionless")
        val maxRecursionDepth = intPreferencesKey("max_recursion_depth")
        val maxScanResults = intPreferencesKey("max_scan_results")
        val signatureReadBytes = intPreferencesKey("signature_read_bytes")
        val minFileSize = longPreferencesKey("min_file_size_bytes")
        val maxFileSize = longPreferencesKey("max_file_size_bytes")
        val thumbnailCacheLimit = intPreferencesKey("thumbnail_cache_limit_mb")

        val performanceMode = stringPreferencesKey("performance_mode")
        val maxConcurrentTasks = intPreferencesKey("max_concurrent_tasks")
        val pauseWhenBackgrounded = booleanPreferencesKey("pause_backgrounded")

        val showRootStatus = booleanPreferencesKey("show_root_status")
        val experimental = booleanPreferencesKey("experimental")

        val themeMode = stringPreferencesKey("theme_mode")
        val defaultLayout = stringPreferencesKey("default_layout")
        val gridSize = stringPreferencesKey("grid_size")
        val defaultSort = stringPreferencesKey("default_sort")

        val seenDefaultGalleryPrompt = booleanPreferencesKey("seen_default_gallery_prompt")
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings.Defaults
        return AppSettings(
            advancedMode = p[Keys.advancedMode] ?: d.advancedMode,
            quickScanEnabled = p[Keys.quickScan] ?: d.quickScanEnabled,
            deepScanEnabled = p[Keys.deepScan] ?: d.deepScanEnabled,
            safScanEnabled = p[Keys.safScan] ?: d.safScanEnabled,
            signatureDetectionEnabled = p[Keys.signatureDetection] ?: d.signatureDetectionEnabled,
            duplicateDetectionEnabled = p[Keys.duplicateDetection] ?: d.duplicateDetectionEnabled,
            scanImages = p[Keys.scanImages] ?: d.scanImages,
            scanVideos = p[Keys.scanVideos] ?: d.scanVideos,
            scanAudio = p[Keys.scanAudio] ?: d.scanAudio,
            scanDocuments = p[Keys.scanDocuments] ?: d.scanDocuments,
            scanArchives = p[Keys.scanArchives] ?: d.scanArchives,
            scanOtherFiles = p[Keys.scanOther] ?: d.scanOtherFiles,
            generateThumbnails = p[Keys.thumbnails] ?: d.generateThumbnails,
            showFileNames = p[Keys.showFileNames] ?: d.showFileNames,
            showFileSize = p[Keys.showFileSize] ?: d.showFileSize,
            includeHiddenFiles = p[Keys.includeHidden] ?: d.includeHiddenFiles,
            includeExtensionlessFiles = p[Keys.includeExtensionless] ?: d.includeExtensionlessFiles,
            maxRecursionDepth = p[Keys.maxRecursionDepth] ?: d.maxRecursionDepth,
            maxScanResults = p[Keys.maxScanResults] ?: d.maxScanResults,
            signatureReadBytes = p[Keys.signatureReadBytes] ?: d.signatureReadBytes,
            minFileSizeBytes = p[Keys.minFileSize] ?: d.minFileSizeBytes,
            maxFileSizeBytes = p[Keys.maxFileSize] ?: d.maxFileSizeBytes,
            thumbnailCacheLimitMb = p[Keys.thumbnailCacheLimit] ?: d.thumbnailCacheLimitMb,
            performanceMode = enumOrDefault(p[Keys.performanceMode], PerformanceMode.entries, d.performanceMode),
            maxConcurrentTasks = p[Keys.maxConcurrentTasks] ?: d.maxConcurrentTasks,
            pauseWhenBackgrounded = p[Keys.pauseWhenBackgrounded] ?: d.pauseWhenBackgrounded,
            showRootStatus = p[Keys.showRootStatus] ?: d.showRootStatus,
            experimentalMethodsEnabled = p[Keys.experimental] ?: d.experimentalMethodsEnabled,
            themeMode = enumOrDefault(p[Keys.themeMode], ThemeMode.entries, d.themeMode),
            defaultLayout = enumOrDefault(p[Keys.defaultLayout], LayoutMode.entries, d.defaultLayout),
            gridSize = enumOrDefault(p[Keys.gridSize], GridSize.entries, d.gridSize),
            defaultSort = enumOrDefault(p[Keys.defaultSort], SortOrder.entries, d.defaultSort),
            hasSeenDefaultGalleryPrompt = p[Keys.seenDefaultGalleryPrompt] ?: d.hasSeenDefaultGalleryPrompt,
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        p[Keys.advancedMode] = s.advancedMode
        p[Keys.quickScan] = s.quickScanEnabled
        p[Keys.deepScan] = s.deepScanEnabled
        p[Keys.safScan] = s.safScanEnabled
        p[Keys.signatureDetection] = s.signatureDetectionEnabled
        p[Keys.duplicateDetection] = s.duplicateDetectionEnabled
        p[Keys.scanImages] = s.scanImages
        p[Keys.scanVideos] = s.scanVideos
        p[Keys.scanAudio] = s.scanAudio
        p[Keys.scanDocuments] = s.scanDocuments
        p[Keys.scanArchives] = s.scanArchives
        p[Keys.scanOther] = s.scanOtherFiles
        p[Keys.thumbnails] = s.generateThumbnails
        p[Keys.showFileNames] = s.showFileNames
        p[Keys.showFileSize] = s.showFileSize
        p[Keys.includeHidden] = s.includeHiddenFiles
        p[Keys.includeExtensionless] = s.includeExtensionlessFiles
        p[Keys.maxRecursionDepth] = s.maxRecursionDepth
        p[Keys.maxScanResults] = s.maxScanResults
        p[Keys.signatureReadBytes] = s.signatureReadBytes
        p[Keys.minFileSize] = s.minFileSizeBytes
        p[Keys.maxFileSize] = s.maxFileSizeBytes
        p[Keys.thumbnailCacheLimit] = s.thumbnailCacheLimitMb
        p[Keys.performanceMode] = s.performanceMode.name
        p[Keys.maxConcurrentTasks] = s.maxConcurrentTasks
        p[Keys.pauseWhenBackgrounded] = s.pauseWhenBackgrounded
        p[Keys.showRootStatus] = s.showRootStatus
        p[Keys.experimental] = s.experimentalMethodsEnabled
        p[Keys.themeMode] = s.themeMode.name
        p[Keys.defaultLayout] = s.defaultLayout.name
        p[Keys.gridSize] = s.gridSize.name
        p[Keys.defaultSort] = s.defaultSort.name
        p[Keys.seenDefaultGalleryPrompt] = s.hasSeenDefaultGalleryPrompt
    }

    /** Parses an enum by name, falling back when the stored value is unknown. */
    private fun <T : Enum<T>> enumOrDefault(raw: String?, values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == raw } ?: fallback
}
