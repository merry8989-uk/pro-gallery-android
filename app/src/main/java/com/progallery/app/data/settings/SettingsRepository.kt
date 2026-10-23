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
    /**
     * Applies a change, then resolves it.
     *
     * Every write goes through [SettingsDependencies.resolve], so a setting whose
     * prerequisite is off is switched off with it rather than left claiming to be
     * active. That is what keeps the switches from lying.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { preferences ->
            // Resolved, not just sanitised: a setting whose prerequisite is off is
            // switched off with it, so no switch can claim to be doing something it
            // is not.
            val next = SettingsDependencies.resolve(transform(read(preferences)).sanitized())
            write(preferences, next)
        }
    }

    /** Restores defaults, keeping the first-run flag so the prompt doesn't return. */
    /**
     * The user's settings as versioned JSON, ready to write to a file the user
     * chooses. Contains no paths, URIs or tokens.
     */
    suspend fun exportJson(): String = SettingsExport.encode(current())

    /**
     * Applies an exported file.
     *
     * Nothing is written until the file has parsed and its format version is
     * understood, so a bad file cannot leave the settings half-applied.
     */
    suspend fun importJson(json: String): Result<AppSettings> =
        SettingsExport.decode(json).onSuccess { imported ->
            update { imported }
        }

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
        val signatureDepth = stringPreferencesKey("signature_depth")
        val detectCorruptMedia = booleanPreferencesKey("detect_corrupt")
        val detectWrongExtensions = booleanPreferencesKey("detect_wrong_ext")
        val extractExif = booleanPreferencesKey("extract_exif")
        val minFileSize = longPreferencesKey("min_file_size_bytes")
        val maxFileSize = longPreferencesKey("max_file_size_bytes")
        val thumbnailCacheLimit = intPreferencesKey("thumbnail_cache_limit_mb")
        val scanBatchSize = intPreferencesKey("scan_batch_size")
        val videoThumbnails = booleanPreferencesKey("video_thumbnails")
        val audioArtwork = booleanPreferencesKey("audio_artwork")
        val documentPreviews = booleanPreferencesKey("document_previews")
        val thumbnailQuality = stringPreferencesKey("thumbnail_quality")
        val thumbnailMaxDimension = intPreferencesKey("thumbnail_max_dimension")
        val signatureThumbnails = booleanPreferencesKey("signature_thumbnails")
        val cacheThumbnails = booleanPreferencesKey("cache_thumbnails")
        val generateDuringScan = booleanPreferencesKey("generate_during_scan")
        val showResolution = booleanPreferencesKey("show_resolution")
        val recoveryQuickScanEnabled = booleanPreferencesKey("recovery_quick_scan")
        val recoveryThumbnailCacheEnabled = booleanPreferencesKey("recovery_thumbnail_cache")
        val exportEnabled = booleanPreferencesKey("export_enabled")
        val exportVerifyAfterCopy = booleanPreferencesKey("export_verify_after_copy")
        val exportDuplicatePolicy = stringPreferencesKey("export_duplicate_policy")
        val exportMaxConcurrentJobs = intPreferencesKey("export_max_jobs")
        val exportConfirmBeforeExport = booleanPreferencesKey("export_confirm")
        val exportAskForDestination = booleanPreferencesKey("export_ask_destination")
        val exportPreserveNames = booleanPreferencesKey("export_preserve_names")
        val exportHistoryRetentionDays = intPreferencesKey("export_history_days")
        val keepActivityHistory = booleanPreferencesKey("keep_activity_history")
        val saveDiagnosticLogs = booleanPreferencesKey("save_diagnostic_logs")
        val includePathsInLogs = booleanPreferencesKey("include_paths_in_logs")
        val autoClearLogsAfterDays = intPreferencesKey("auto_clear_logs_days")

        val performanceMode = stringPreferencesKey("performance_mode")
        val maxConcurrentTasks = intPreferencesKey("max_concurrent_tasks")
        val pauseWhenBackgrounded = booleanPreferencesKey("pause_backgrounded")

        val showRootStatus = booleanPreferencesKey("show_root_status")

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
            signatureDepth = enumOrDefault(p[Keys.signatureDepth], SignatureDepth.entries, d.signatureDepth),
            detectCorruptMedia = p[Keys.detectCorruptMedia] ?: d.detectCorruptMedia,
            detectWrongExtensions = p[Keys.detectWrongExtensions] ?: d.detectWrongExtensions,
            extractExif = p[Keys.extractExif] ?: d.extractExif,
            minFileSizeBytes = p[Keys.minFileSize] ?: d.minFileSizeBytes,
            maxFileSizeBytes = p[Keys.maxFileSize] ?: d.maxFileSizeBytes,
            thumbnailCacheLimitMb = p[Keys.thumbnailCacheLimit] ?: d.thumbnailCacheLimitMb,
            scanBatchSize = p[Keys.scanBatchSize] ?: d.scanBatchSize,
            videoThumbnails = p[Keys.videoThumbnails] ?: d.videoThumbnails,
            audioArtwork = p[Keys.audioArtwork] ?: d.audioArtwork,
            documentPreviews = p[Keys.documentPreviews] ?: d.documentPreviews,
            thumbnailQuality = enumOrDefault(p[Keys.thumbnailQuality], ThumbnailQuality.entries, d.thumbnailQuality),
            thumbnailMaxDimension = p[Keys.thumbnailMaxDimension] ?: d.thumbnailMaxDimension,
            signatureThumbnails = p[Keys.signatureThumbnails] ?: d.signatureThumbnails,
            cacheThumbnails = p[Keys.cacheThumbnails] ?: d.cacheThumbnails,
            generateDuringScan = p[Keys.generateDuringScan] ?: d.generateDuringScan,
            showResolution = p[Keys.showResolution] ?: d.showResolution,
            recoveryQuickScanEnabled = p[Keys.recoveryQuickScanEnabled] ?: d.recoveryQuickScanEnabled,
            recoveryThumbnailCacheEnabled = p[Keys.recoveryThumbnailCacheEnabled] ?: d.recoveryThumbnailCacheEnabled,
            exportEnabled = p[Keys.exportEnabled] ?: d.exportEnabled,
            exportVerifyAfterCopy = p[Keys.exportVerifyAfterCopy] ?: d.exportVerifyAfterCopy,
            exportDuplicatePolicy = enumOrDefault(p[Keys.exportDuplicatePolicy], ExportDuplicatePolicy.entries, d.exportDuplicatePolicy),
            exportMaxConcurrentJobs = p[Keys.exportMaxConcurrentJobs] ?: d.exportMaxConcurrentJobs,
            exportConfirmBeforeExport = p[Keys.exportConfirmBeforeExport] ?: d.exportConfirmBeforeExport,
            exportAskForDestination = p[Keys.exportAskForDestination] ?: d.exportAskForDestination,
            exportPreserveNames = p[Keys.exportPreserveNames] ?: d.exportPreserveNames,
            exportHistoryRetentionDays = p[Keys.exportHistoryRetentionDays] ?: d.exportHistoryRetentionDays,
            keepActivityHistory = p[Keys.keepActivityHistory] ?: d.keepActivityHistory,
            saveDiagnosticLogs = p[Keys.saveDiagnosticLogs] ?: d.saveDiagnosticLogs,
            includePathsInLogs = p[Keys.includePathsInLogs] ?: d.includePathsInLogs,
            autoClearLogsAfterDays = p[Keys.autoClearLogsAfterDays] ?: d.autoClearLogsAfterDays,
            performanceMode = enumOrDefault(p[Keys.performanceMode], PerformanceMode.entries, d.performanceMode),
            maxConcurrentTasks = p[Keys.maxConcurrentTasks] ?: d.maxConcurrentTasks,
            pauseWhenBackgrounded = p[Keys.pauseWhenBackgrounded] ?: d.pauseWhenBackgrounded,
            showRootStatus = p[Keys.showRootStatus] ?: d.showRootStatus,
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
        p[Keys.signatureDepth] = s.signatureDepth.name
        p[Keys.detectCorruptMedia] = s.detectCorruptMedia
        p[Keys.detectWrongExtensions] = s.detectWrongExtensions
        p[Keys.extractExif] = s.extractExif
        p[Keys.minFileSize] = s.minFileSizeBytes
        p[Keys.maxFileSize] = s.maxFileSizeBytes
        p[Keys.thumbnailCacheLimit] = s.thumbnailCacheLimitMb
        p[Keys.scanBatchSize] = s.scanBatchSize
        p[Keys.videoThumbnails] = s.videoThumbnails
        p[Keys.audioArtwork] = s.audioArtwork
        p[Keys.documentPreviews] = s.documentPreviews
        p[Keys.thumbnailQuality] = s.thumbnailQuality.name
        p[Keys.thumbnailMaxDimension] = s.thumbnailMaxDimension
        p[Keys.signatureThumbnails] = s.signatureThumbnails
        p[Keys.cacheThumbnails] = s.cacheThumbnails
        p[Keys.generateDuringScan] = s.generateDuringScan
        p[Keys.showResolution] = s.showResolution
        p[Keys.recoveryQuickScanEnabled] = s.recoveryQuickScanEnabled
        p[Keys.recoveryThumbnailCacheEnabled] = s.recoveryThumbnailCacheEnabled
        p[Keys.exportEnabled] = s.exportEnabled
        p[Keys.exportVerifyAfterCopy] = s.exportVerifyAfterCopy
        p[Keys.exportDuplicatePolicy] = s.exportDuplicatePolicy.name
        p[Keys.exportMaxConcurrentJobs] = s.exportMaxConcurrentJobs
        p[Keys.exportConfirmBeforeExport] = s.exportConfirmBeforeExport
        p[Keys.exportAskForDestination] = s.exportAskForDestination
        p[Keys.exportPreserveNames] = s.exportPreserveNames
        p[Keys.exportHistoryRetentionDays] = s.exportHistoryRetentionDays
        p[Keys.keepActivityHistory] = s.keepActivityHistory
        p[Keys.saveDiagnosticLogs] = s.saveDiagnosticLogs
        p[Keys.includePathsInLogs] = s.includePathsInLogs
        p[Keys.autoClearLogsAfterDays] = s.autoClearLogsAfterDays
        p[Keys.performanceMode] = s.performanceMode.name
        p[Keys.maxConcurrentTasks] = s.maxConcurrentTasks
        p[Keys.pauseWhenBackgrounded] = s.pauseWhenBackgrounded
        p[Keys.showRootStatus] = s.showRootStatus
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
