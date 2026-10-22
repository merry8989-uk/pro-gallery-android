package com.progallery.app.data.settings

import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder

/** How aggressively scanning uses the device. */
enum class PerformanceMode(val label: String) {
    BATTERY_SAVER("Battery saver"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance"),
}

/** App theme selection. */
/**
 * What export does when the destination already holds a file of that name.
 *
 * Overwriting is deliberately **not** offered: silently replacing a file the user
 * already has is not something a recovery tool should do.
 */
enum class ExportDuplicatePolicy(val label: String) {
    RENAME("Keep both (add a number)"),
    SKIP("Skip the file"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** Default layout for media lists. */
enum class LayoutMode(val label: String) {
    GRID("Grid"),
    LIST("List"),
}

/** Grid density. */
enum class GridSize(val label: String, val columns: Int) {
    SMALL("Small", 2),
    MEDIUM("Medium", 3),
    LARGE("Large", 4),
    EXTRA_LARGE("Extra large", 5),
}

/**
 * Every user-configurable setting, with safe defaults.
 *
 * Rules this model enforces:
 *  - Dangerous/experimental options default to OFF.
 *  - Numeric values are clamped by [sanitized], so a bad imported value can
 *    never crash the scanner.
 *  - Behaviour is derived through [enabledKinds] / [scanCanStart] rather than
 *    scattered `if` checks, so one toggle has one meaning.
 */
data class AppSettings(
    /** Reveals the technical sections. Purely a visibility switch. */
    val advancedMode: Boolean = false,

    // ---- A. Scan modes -------------------------------------------------
    val quickScanEnabled: Boolean = true,
    val deepScanEnabled: Boolean = true,
    val safScanEnabled: Boolean = true,
    val signatureDetectionEnabled: Boolean = true,
    val duplicateDetectionEnabled: Boolean = true,

    // ---- A. File categories -------------------------------------------
    val scanImages: Boolean = true,
    val scanVideos: Boolean = true,
    val scanAudio: Boolean = true,
    val scanDocuments: Boolean = true,
    val scanArchives: Boolean = true,
    val scanOtherFiles: Boolean = true,

    // ---- B. Thumbnails and result cards -------------------------------
    val generateThumbnails: Boolean = true,
    /**
     * Upper bound for the app's own thumbnail cache, in MB. Applied when the app
     * starts, because Coil builds its cache once.
     */
    val thumbnailCacheLimitMb: Int = 256,
    val showFileNames: Boolean = true,
    val showFileSize: Boolean = true,

    // ---- C. Advanced detection ----------------------------------------
    val includeHiddenFiles: Boolean = false,
    val includeExtensionlessFiles: Boolean = true,
    val maxRecursionDepth: Int = 8,
    val maxScanResults: Int = 5000,
    /** How many leading bytes the signature detector reads. */
    val signatureReadBytes: Int = 4096,

    /**
     * Smallest file to show. Defaults to 1 byte, so nothing real is dropped for
     * being tiny — APKs, images, documents, all of it.
     */
    val minFileSizeBytes: Long = 1L,
    /**
     * Largest file to show. [Long.MAX_VALUE] means **no limit at all**.
     */
    val maxFileSizeBytes: Long = Long.MAX_VALUE,

    // ---- E. Performance ------------------------------------------------
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val maxConcurrentTasks: Int = 4,
    /**
     * How many index rows are read before the library is published again. A smaller
     * number paints sooner; a larger one does fewer passes.
     */
    val scanBatchSize: Int = 500,
    val pauseWhenBackgrounded: Boolean = false,

    // ---- G. Root and experimental (OFF by default) ---------------------
    /** Only reports whether the device is rooted. Never requests root. */
    val showRootStatus: Boolean = false,
    /**
     * Gates experimental detection. Enabling it does NOT unlock anything that
     * has not been implemented and tested.
     */
    val experimentalMethodsEnabled: Boolean = false,

    // ---- I. UI ----------------------------------------------------------
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultLayout: LayoutMode = LayoutMode.GRID,
    val gridSize: GridSize = GridSize.MEDIUM,
    val defaultSort: SortOrder = SortOrder.DATE_NEWEST,

    /** Show a real frame for videos. Off leaves the type icon. */
    val videoThumbnails: Boolean = true,

    /** Show embedded album art for audio. Off leaves the type icon. */
    val audioArtwork: Boolean = true,

    /** Show a first-page preview for documents. Off leaves the type icon. */
    val documentPreviews: Boolean = true,

    /** Show pixel size and duration on list rows, where they are known. */
    val showResolution: Boolean = false,

    /** Offer the Quick Scan on the Recovery screen. */
    val recoveryQuickScanEnabled: Boolean = true,

    /** Offer the thumbnail-and-cache scan. It finds preview artefacts, never originals. */
    val recoveryThumbnailCacheEnabled: Boolean = false,

    /** Allow recovered items to be written to a folder you choose. */
    val exportEnabled: Boolean = true,

    /** Check the copy against the source size before reporting success. */
    val exportVerifyAfterCopy: Boolean = true,

    /** What to do when the destination already has a file of that name. */
    val exportDuplicatePolicy: ExportDuplicatePolicy = ExportDuplicatePolicy.RENAME,

    /** How many files may be copied at once. */
    val exportMaxConcurrentJobs: Int = 2,

    /** Keep a local record of recent scans so they can be reviewed. */
    val keepActivityHistory: Boolean = false,

    /** Write a local diagnostics log. Nothing is ever sent anywhere. */
    val saveDiagnosticLogs: Boolean = false,

    /**
     * Whether anonymised usage counts are recorded on this device.
     *
     * On by default, and off with one switch. Only counts of a closed set of events, a device
     * model and timings — never a filename, a path, a search term or anything from the user's
     * content. Nothing is uploaded anywhere.
     */
    val usageDataEnabled: Boolean = true,

    /**
     * Whether the diagnostics log is sent to the server. **Off by default.**
     *
     * Off, and not on, because this is the one setting that moves data off the device. The
     * standard asks for consent, and consent that is assumed is not consent — the local log
     * works fully without it, so nothing is lost by defaulting to no.
     */
    val uploadDiagnosticsEnabled: Boolean = false,

    /**
     * Whether files the app found in app folders — the photos messaging apps keep under
     * `Android/media` — are merged into the gallery.
     *
     * On by default, because a photo the user can see in WhatsApp is a photo they expect to
     * find in their gallery. Off is a real switch: the index is not read at all, so the grid
     * goes back to exactly what the system reports.
     */
    val includeAppMediaInGallery: Boolean = true,

    /** Include file paths in the diagnostics log. Off by default, because a log is a file you may share. */
    val includePathsInLogs: Boolean = false,

    /** How long diagnostics are kept before they are cleared automatically. */
    val autoClearLogsAfterDays: Int = 7,

    // ---- First run ------------------------------------------------------
    val hasSeenDefaultGalleryPrompt: Boolean = false,
) {

    /**
     * The file kinds the scanner is allowed to collect. Disabling a category
     * removes it here, which is what stops it being discovered *and* stops its
     * thumbnails being generated.
     */
    val enabledKinds: Set<MediaType>
        get() = buildSet {
            if (scanImages) add(MediaType.IMAGE)
            if (scanVideos) add(MediaType.VIDEO)
            if (scanAudio) add(MediaType.AUDIO)
            if (scanDocuments) add(MediaType.DOCUMENT)
            if (scanArchives) add(MediaType.ARCHIVE)
            if (scanOtherFiles) add(MediaType.OTHER)
        }

    /** At least one discovery method is on. */
    val anyScanModeEnabled: Boolean
        get() = quickScanEnabled || deepScanEnabled || safScanEnabled

    /** At least one file category is on. */
    val anyCategoryEnabled: Boolean get() = enabledKinds.isNotEmpty()

    /** Whether a scan can meaningfully start at all. */
    val scanCanStart: Boolean get() = anyScanModeEnabled && anyCategoryEnabled

    /** True when there is no upper size limit. */
    val isMaxSizeUnlimited: Boolean
        get() = maxFileSizeBytes <= 0L || maxFileSizeBytes == Long.MAX_VALUE

    /**
     * Whether a file of [sizeBytes] is inside the configured range.
     *
     * A size of 0 or less means the provider did not report one — SAF reports 0
     * for "unknown" as well as for "empty" — so it is never treated as "too
     * small". Dropping a real file because its size was unreadable would be far
     * worse than showing an empty one.
     */
    fun acceptsSize(sizeBytes: Long): Boolean {
        if (sizeBytes <= 0L) return true
        if (sizeBytes < minFileSizeBytes) return false
        return isMaxSizeUnlimited || sizeBytes <= maxFileSizeBytes
    }

    /** Human-readable form of the range, for the settings summary. */
    val sizeRangeLabel: String
        get() = "${describeSize(minFileSizeBytes)} – " +
            if (isMaxSizeUnlimited) "unlimited" else describeSize(maxFileSizeBytes)

    private fun describeSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024L * 1024 * 1024)} GB"
    }

    /** Video discovery and video thumbnail jobs both depend on this. */
    val videoScanningEnabled: Boolean get() = scanVideos

    /** A short, honest summary of what a scan would actually look at. */
    fun summary(): String {
        if (!anyScanModeEnabled) return "No scan method is enabled"
        if (!anyCategoryEnabled) return "No file category is enabled"
        val methods = buildList {
            if (quickScanEnabled) add("Quick")
            if (deepScanEnabled || safScanEnabled) add("Deep")
        }.joinToString(" + ")
        val kinds = enabledKinds
            .filter { it != MediaType.ALL }
            .joinToString(", ") { it.name.lowercase() }
        return "$methods scan · $kinds"
    }

    /**
     * Clamps every numeric value into a safe range. Imported or corrupted
     * values therefore cannot crash or hang the scanner.
     */
    fun sanitized(): AppSettings {
        val minSize = minFileSizeBytes.coerceIn(1L, 1_073_741_824L)
        val maxSize = if (maxFileSizeBytes <= 0L) {
            Long.MAX_VALUE
        } else {
            maxFileSizeBytes.coerceAtLeast(minSize)
        }
        return copy(
            minFileSizeBytes = minSize,
            maxFileSizeBytes = maxSize,
            maxRecursionDepth = maxRecursionDepth.coerceIn(1, 32),
            maxScanResults = maxScanResults.coerceIn(50, 50_000),
            signatureReadBytes = signatureReadBytes.coerceIn(64, 65_536),
            maxConcurrentTasks = maxConcurrentTasks.coerceIn(1, 16),
            thumbnailCacheLimitMb = thumbnailCacheLimitMb.coerceIn(32, 4096),
            scanBatchSize = scanBatchSize.coerceIn(100, 5_000),
            exportMaxConcurrentJobs = exportMaxConcurrentJobs.coerceIn(1, 8),
            autoClearLogsAfterDays = autoClearLogsAfterDays.coerceIn(1, 365),
            // Experimental and root-related features stay off unless explicitly
            // enabled by the user; a sanitized copy never turns them on.
            experimentalMethodsEnabled = experimentalMethodsEnabled,
        )
    }

    /**
     * Restores one section to its defaults, leaving everything else untouched.
     *
     * The sections are the ones the Settings screen groups its rows under, so
     * "Reset" on a heading does exactly what that heading promises. `advancedMode` is
     * deliberately never reset: it is the switch that reveals the section, and
     * flipping it while the user is looking at it would be disorienting. Unknown
     * section names change nothing.
     */
    fun resetSection(section: String): AppSettings = when (section) {
        "Scanning" -> copy(
            quickScanEnabled = Defaults.quickScanEnabled,
            deepScanEnabled = Defaults.deepScanEnabled,
            safScanEnabled = Defaults.safScanEnabled,
            signatureDetectionEnabled = Defaults.signatureDetectionEnabled,
            duplicateDetectionEnabled = Defaults.duplicateDetectionEnabled,
        )
        "File types" -> copy(
            scanImages = Defaults.scanImages,
            scanVideos = Defaults.scanVideos,
            scanAudio = Defaults.scanAudio,
            scanDocuments = Defaults.scanDocuments,
            scanArchives = Defaults.scanArchives,
            scanOtherFiles = Defaults.scanOtherFiles,
        )
        "Thumbnails & result cards" -> copy(
            generateThumbnails = Defaults.generateThumbnails,
            thumbnailCacheLimitMb = Defaults.thumbnailCacheLimitMb,
            showFileNames = Defaults.showFileNames,
            showFileSize = Defaults.showFileSize,
            videoThumbnails = Defaults.videoThumbnails,
            audioArtwork = Defaults.audioArtwork,
            documentPreviews = Defaults.documentPreviews,
            showResolution = Defaults.showResolution,
        )
        "Recovery & export" -> copy(
            recoveryQuickScanEnabled = Defaults.recoveryQuickScanEnabled,
            recoveryThumbnailCacheEnabled = Defaults.recoveryThumbnailCacheEnabled,
            exportEnabled = Defaults.exportEnabled,
            exportVerifyAfterCopy = Defaults.exportVerifyAfterCopy,
            exportDuplicatePolicy = Defaults.exportDuplicatePolicy,
            exportMaxConcurrentJobs = Defaults.exportMaxConcurrentJobs,
        )
        "Appearance" -> copy(
            themeMode = Defaults.themeMode,
            defaultLayout = Defaults.defaultLayout,
            gridSize = Defaults.gridSize,
            defaultSort = Defaults.defaultSort,
        )
        "Detection" -> copy(
            includeHiddenFiles = Defaults.includeHiddenFiles,
            includeExtensionlessFiles = Defaults.includeExtensionlessFiles,
            experimentalMethodsEnabled = Defaults.experimentalMethodsEnabled,
        )
        "Scan limits" -> copy(
            maxRecursionDepth = Defaults.maxRecursionDepth,
            maxScanResults = Defaults.maxScanResults,
            signatureReadBytes = Defaults.signatureReadBytes,
            minFileSizeBytes = Defaults.minFileSizeBytes,
            maxFileSizeBytes = Defaults.maxFileSizeBytes,
        )
        "Performance" -> copy(
            performanceMode = Defaults.performanceMode,
            maxConcurrentTasks = Defaults.maxConcurrentTasks,
            scanBatchSize = Defaults.scanBatchSize,
            pauseWhenBackgrounded = Defaults.pauseWhenBackgrounded,
        )
        "Diagnostics & experimental" -> copy(
            showRootStatus = Defaults.showRootStatus,
            keepActivityHistory = Defaults.keepActivityHistory,
            saveDiagnosticLogs = Defaults.saveDiagnosticLogs,
            includePathsInLogs = Defaults.includePathsInLogs,
            autoClearLogsAfterDays = Defaults.autoClearLogsAfterDays,
        )
        else -> this
    }

    companion object {
        /** Safe defaults, used by "Reset to defaults". */
        val Defaults = AppSettings()
    }
}
