package com.progallery.app.data.settings

import com.progallery.app.data.model.SortOrder

/**
 * Settings export and import.
 *
 * The file is a flat, versioned JSON object. It deliberately holds **only the user's
 * choices**: no file paths, no content URIs, no SAF grants, no tokens. A test asserts
 * that, because an export is a file the user will share or keep.
 *
 * Import is tolerant in the ways that matter — unknown keys are ignored (so a file
 * written by a newer build still loads), missing keys fall back to the current
 * defaults — and strict about the format version, so a file from a future format is
 * refused rather than silently half-applied.
 *
 * Generated from the [AppSettings] field list; `SettingsExportTest` round-trips every
 * field, so a new setting that is not exported fails the build.
 */
object SettingsExport {

    const val FORMAT_VERSION = 1
    private const val KEY_FORMAT = "formatVersion"

    /** The user's choices as versioned JSON. */
    fun encode(settings: AppSettings): String = FlatJson.encode(
        linkedMapOf<String, Any?>(
            KEY_FORMAT to FORMAT_VERSION.toLong(),
        "advancedMode" to settings.advancedMode,
        "quickScanEnabled" to settings.quickScanEnabled,
        "deepScanEnabled" to settings.deepScanEnabled,
        "safScanEnabled" to settings.safScanEnabled,
        "signatureDetectionEnabled" to settings.signatureDetectionEnabled,
        "duplicateDetectionEnabled" to settings.duplicateDetectionEnabled,
        "scanImages" to settings.scanImages,
        "scanVideos" to settings.scanVideos,
        "scanAudio" to settings.scanAudio,
        "scanDocuments" to settings.scanDocuments,
        "scanArchives" to settings.scanArchives,
        "scanOtherFiles" to settings.scanOtherFiles,
        "generateThumbnails" to settings.generateThumbnails,
        "thumbnailCacheLimitMb" to settings.thumbnailCacheLimitMb.toLong(),
        "showFileNames" to settings.showFileNames,
        "showFileSize" to settings.showFileSize,
        "includeHiddenFiles" to settings.includeHiddenFiles,
        "includeExtensionlessFiles" to settings.includeExtensionlessFiles,
        "maxRecursionDepth" to settings.maxRecursionDepth.toLong(),
        "maxScanResults" to settings.maxScanResults.toLong(),
        "signatureReadBytes" to settings.signatureReadBytes.toLong(),
        "minFileSizeBytes" to settings.minFileSizeBytes,
        "maxFileSizeBytes" to settings.maxFileSizeBytes,
        "performanceMode" to settings.performanceMode.name,
        "maxConcurrentTasks" to settings.maxConcurrentTasks.toLong(),
        "scanBatchSize" to settings.scanBatchSize.toLong(),
        "pauseWhenBackgrounded" to settings.pauseWhenBackgrounded,
        "showRootStatus" to settings.showRootStatus,
        "experimentalMethodsEnabled" to settings.experimentalMethodsEnabled,
        "themeMode" to settings.themeMode.name,
        "defaultLayout" to settings.defaultLayout.name,
        "gridSize" to settings.gridSize.name,
        "defaultSort" to settings.defaultSort.name,
        "hasSeenDefaultGalleryPrompt" to settings.hasSeenDefaultGalleryPrompt,
        ),
    )

    /**
     * Reads an export back.
     *
     * Fails with a readable message on malformed input or an unsupported format
     * version, and never returns settings that break the app's own invariants —
     * the result is passed through [AppSettings.sanitized].
     */
    fun decode(json: String): Result<AppSettings> {
        val map = try {
            FlatJson.decode(json)
        } catch (t: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("This is not a valid settings file.", t))
        }

        val version = (map[KEY_FORMAT] as? Long)?.toInt()
            ?: return Result.failure(IllegalArgumentException("This file has no format version, so it was not written by ProGallery."))

        if (version > FORMAT_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "This settings file is from a newer version of the app (format $version, this build reads $FORMAT_VERSION).",
                ),
            )
        }

        val defaults = AppSettings.Defaults
        val imported = AppSettings(
            advancedMode = bool(map, "advancedMode", defaults.advancedMode),
            quickScanEnabled = bool(map, "quickScanEnabled", defaults.quickScanEnabled),
            deepScanEnabled = bool(map, "deepScanEnabled", defaults.deepScanEnabled),
            safScanEnabled = bool(map, "safScanEnabled", defaults.safScanEnabled),
            signatureDetectionEnabled = bool(map, "signatureDetectionEnabled", defaults.signatureDetectionEnabled),
            duplicateDetectionEnabled = bool(map, "duplicateDetectionEnabled", defaults.duplicateDetectionEnabled),
            scanImages = bool(map, "scanImages", defaults.scanImages),
            scanVideos = bool(map, "scanVideos", defaults.scanVideos),
            scanAudio = bool(map, "scanAudio", defaults.scanAudio),
            scanDocuments = bool(map, "scanDocuments", defaults.scanDocuments),
            scanArchives = bool(map, "scanArchives", defaults.scanArchives),
            scanOtherFiles = bool(map, "scanOtherFiles", defaults.scanOtherFiles),
            generateThumbnails = bool(map, "generateThumbnails", defaults.generateThumbnails),
            thumbnailCacheLimitMb = int(map, "thumbnailCacheLimitMb", defaults.thumbnailCacheLimitMb),
            showFileNames = bool(map, "showFileNames", defaults.showFileNames),
            showFileSize = bool(map, "showFileSize", defaults.showFileSize),
            includeHiddenFiles = bool(map, "includeHiddenFiles", defaults.includeHiddenFiles),
            includeExtensionlessFiles = bool(map, "includeExtensionlessFiles", defaults.includeExtensionlessFiles),
            maxRecursionDepth = int(map, "maxRecursionDepth", defaults.maxRecursionDepth),
            maxScanResults = int(map, "maxScanResults", defaults.maxScanResults),
            signatureReadBytes = int(map, "signatureReadBytes", defaults.signatureReadBytes),
            minFileSizeBytes = long(map, "minFileSizeBytes", defaults.minFileSizeBytes),
            maxFileSizeBytes = long(map, "maxFileSizeBytes", defaults.maxFileSizeBytes),
            performanceMode = enum(map, "performanceMode", PerformanceMode.entries, defaults.performanceMode),
            maxConcurrentTasks = int(map, "maxConcurrentTasks", defaults.maxConcurrentTasks),
            scanBatchSize = int(map, "scanBatchSize", defaults.scanBatchSize),
            pauseWhenBackgrounded = bool(map, "pauseWhenBackgrounded", defaults.pauseWhenBackgrounded),
            showRootStatus = bool(map, "showRootStatus", defaults.showRootStatus),
            experimentalMethodsEnabled = bool(map, "experimentalMethodsEnabled", defaults.experimentalMethodsEnabled),
            themeMode = enum(map, "themeMode", ThemeMode.entries, defaults.themeMode),
            defaultLayout = enum(map, "defaultLayout", LayoutMode.entries, defaults.defaultLayout),
            gridSize = enum(map, "gridSize", GridSize.entries, defaults.gridSize),
            defaultSort = enum(map, "defaultSort", SortOrder.entries, defaults.defaultSort),
            hasSeenDefaultGalleryPrompt = bool(map, "hasSeenDefaultGalleryPrompt", defaults.hasSeenDefaultGalleryPrompt),
        )
        return Result.success(imported.sanitized())
    }

    private fun bool(map: Map<String, Any?>, key: String, fallback: Boolean): Boolean =
        map[key] as? Boolean ?: fallback

    private fun int(map: Map<String, Any?>, key: String, fallback: Int): Int =
        (map[key] as? Long)?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: fallback

    private fun long(map: Map<String, Any?>, key: String, fallback: Long): Long =
        map[key] as? Long ?: fallback

    private fun <T : Enum<T>> enum(
        map: Map<String, Any?>,
        key: String,
        values: List<T>,
        fallback: T,
    ): T {
        val raw = map[key] as? String ?: return fallback
        return values.firstOrNull { it.name == raw } ?: fallback
    }
}
