package com.progallery.app.data.settings

import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.data.recovery.RecoveryTab

/**
 * How aggressively scanning uses the device.
 *
 * A mode is a **preset that writes real numbers**, not a label. Before this, the selector was
 * stored, exported and shown while nothing read it — the numbers below were the only thing the
 * scanner ever saw, so choosing "Battery saver" changed nothing at all.
 *
 * [CUSTOM] is the one that does not write anything: it means "use the individual controls as they
 * are", which is why they are the settings a person edits and these are the settings a person
 * picks.
 */
enum class PerformanceMode(val label: String) {
    BATTERY_SAVER("Battery saver"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance"),
    CUSTOM("Custom"),
    ;

    /** The numbers this mode stands for, or null for [CUSTOM], which stands for the user's own. */
    val policy: PerformancePolicy?
        get() = when (this) {
            BATTERY_SAVER -> PerformancePolicy(
                concurrentTasks = 2,
                batchSize = 250,
                // A battery saver that keeps scanning while the app is in the background is not a
                // battery saver.
                pauseWhenBackgrounded = true,
            )
            BALANCED -> PerformancePolicy(concurrentTasks = 4, batchSize = 500, pauseWhenBackgrounded = false)
            PERFORMANCE -> PerformancePolicy(concurrentTasks = 8, batchSize = 1_000, pauseWhenBackgrounded = false)
            CUSTOM -> null
        }
}

/** What a [PerformanceMode] actually means, in numbers the scanner can use. */
data class PerformancePolicy(
    val concurrentTasks: Int,
    val batchSize: Int,
    val pauseWhenBackgrounded: Boolean,
)

/** App theme selection. */
/**
 * What export does when the destination already holds a file of that name.
 *
 * Overwriting is deliberately **not** offered: silently replacing a file the user
 * already has is not something a recovery tool should do.
 */
enum class ExportDuplicatePolicy(val label: String) {
    ASK("Ask me each time"),
    RENAME("Keep both (add a number)"),
    SKIP("Skip the file"),
    ;

    /**
     * Whether the exporter can decide this one on its own.
     *
     * `ASK` cannot: it needs an answer from the person, which is why the export path has to stop
     * and wait rather than choose. Everything else is a rule the exporter applies by itself.
     */
    val decidesItself: Boolean get() = this != ASK
}

/**
 * How much of a file is read to work out what it is.
 *
 * A preset over a byte count, not a second setting: the scanner reads [AppSettings.effectiveSignatureBytes].
 */
/**
 * How large a decoded preview may be.
 *
 * A preset over a pixel count, not a second setting: the decoder reads
 * [AppSettings.effectiveThumbnailDimension].
 */
enum class ThumbnailQuality(val label: String, val dimension: Int?) {
    LOW("Low", 256),
    BALANCED("Balanced", 512),
    HIGH("High", 1024),
    CUSTOM("Custom", null),
}

enum class SignatureDepth(val label: String, val bytes: Int?) {
    SAFE("Safe", 512),
    BALANCED("Balanced", 4_096),
    EXTENDED("Extended", 32_768),
    CUSTOM("Custom", null),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    /**
     * Whether to draw dark, given what the system says.
     *
     * The choice used to reach nothing: `MainActivity` called the theme with no argument, so the
     * app was always whatever the system was and the control was decorative. This is the one place
     * that answers the question, and it is pure so it is tested rather than trusted.
     */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
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
     * How deep identification looks, as a preset over [signatureReadBytes].
     *
     * The number is the thing the scanner actually uses; this is the thing a person chooses. Same
     * shape as the performance mode, and for the same reason: "Balanced" is a decision somebody can
     * make, and "4096" is not. Touching the number moves this to Custom.
     */
    val signatureDepth: SignatureDepth = SignatureDepth.BALANCED,

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

    /**
     * Look for files that are truncated, empty, or whose name disagrees with their bytes.
     *
     * Off means a scan reports what it found and says nothing about whether it is intact — which
     * is a legitimate choice, because reading the ends of every file costs time.
     */
    val detectCorruptMedia: Boolean = true,

    /** Report a file whose extension does not match what its bytes say it is. */
    val detectWrongExtensions: Boolean = true,

    /** Read the capture date and camera from a picture's EXIF. Never its location. */
    val extractExif: Boolean = true,

    /**
     * How large a decoded preview may be, as a preset over [thumbnailMaxDimension].
     *
     * The number is what the decoder uses; this is what a person chooses. Same shape as the
     * performance mode and the signature depth, and for the same reason.
     */
    val thumbnailQuality: ThumbnailQuality = ThumbnailQuality.BALANCED,

    /** The longest edge of a decoded preview, when the quality is Custom. */
    val thumbnailMaxDimension: Int = 512,

    /**
     * Whether a file identified **only from its bytes** may show a real preview.
     *
     * Those files have no extension to trust and no system index entry, so their tiles fell back to
     * an icon even when the bytes said they were a picture. Decoding one is exactly as safe as
     * decoding any other image; the only reason it was not done is that the policy had no branch
     * for it.
     */
    val signatureThumbnails: Boolean = true,

    /**
     * Whether decoded previews are kept in the memory and disk caches.
     *
     * Off means decoding again every time a tile scrolls back into view — a real trade of speed for
     * storage, which is why it is a choice rather than a fixed behaviour.
     */
    val cacheThumbnails: Boolean = true,

    /**
     * Whether a scan decodes previews for what it finds, instead of leaving them until a tile is
     * displayed. Bounded, and it obeys the same per-kind policy and the same pause gate.
     */
    val generateDuringScan: Boolean = false,

    /**
     * Which result tab the Recovery screen opens on.
     *
     * A person who always looks at photos first should not have to say so every time.
     */
    val defaultRecoveryTab: RecoveryTab = RecoveryTab.ALL,

    /**
     * Whether the app taps back.
     *
     * **Off by default**, because haptics are the kind of thing that should be asked for rather
     * than discovered — a phone that buzzes without being asked is a phone people turn things off
     * on. Nothing here vibrates until this is on.
     */
    val hapticsEnabled: Boolean = false,

    /**
     * A denser result grid: more columns, and no size line under each tile.
     *
     * The name stays either way — it is how a file is recognised — but the size and the type are
     * the first things worth giving up when the point is to see more at once.
     */
    val compactResultCards: Boolean = false,

    /**
     * Stop a scan while the battery is low.
     *
     * Off by default: the background pause is opt-in too, and a scan somebody started is usually a
     * scan they want finished. On, it waits rather than cancels — and it says so.
     */
    val pauseOnLowBattery: Boolean = false,

    /**
     * Stop a scan while the device is thermally throttled.
     *
     * The honest reason is not politeness to the phone: a hot device throttles, so a scan that keeps
     * going runs slower *and* makes it hotter, which is the worst of both.
     */
    val pauseOnThermalPressure: Boolean = false,

    /** Check the copy against the source size before reporting success. */
    val exportVerifyAfterCopy: Boolean = true,

    /** What to do when the destination already has a file of that name. */
    val exportDuplicatePolicy: ExportDuplicatePolicy = ExportDuplicatePolicy.RENAME,

    /** How many files may be copied at once. */
    val exportMaxConcurrentJobs: Int = 2,

    /** Ask before anything is written, so an accidental tap cannot start a copy. */
    val exportConfirmBeforeExport: Boolean = true,

    /**
     * Ask which folder every time, or reuse the last one.
     *
     * When this is off the app still asks once — there is no way to export without a destination,
     * and inventing one would be worse than asking.
     */
    val exportAskForDestination: Boolean = true,

    /** Keep the file's own name. Off means the app marks what it writes, as it always did. */
    val exportPreserveNames: Boolean = true,

    /** How long the export history is kept. Purged when the Recovery screen is opened. */
    val exportHistoryRetentionDays: Int = 90,

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

    /*
     * What the scanner actually uses.
     *
     * The mode and the individual controls are two ways of saying the same thing, and only one of
     * them can win. When a mode is chosen it wins; when Custom is chosen the controls win. Reading
     * the raw fields anywhere would quietly go back to the old bug — a control that is stored,
     * shown, and ignored — so every consumer reads these instead.
     */

    /** How large a preview is actually decoded, after the quality has had its say. */
    val effectiveThumbnailDimension: Int
        get() = thumbnailQuality.dimension ?: thumbnailMaxDimension

    /** How many bytes identification actually reads, after the depth has had its say. */
    val effectiveSignatureBytes: Int
        get() = signatureDepth.bytes ?: signatureReadBytes

    /** How many files may be worked on at once. */
    val effectiveConcurrency: Int
        get() = performanceMode.policy?.concurrentTasks ?: maxConcurrentTasks

    /** How many rows are read before the index is published again. */
    val effectiveBatchSize: Int
        get() = performanceMode.policy?.batchSize ?: scanBatchSize

    /** Whether scanning should wait while the app is in the background. */
    val effectivePauseWhenBackgrounded: Boolean
        get() = performanceMode.policy?.pauseWhenBackgrounded ?: pauseWhenBackgrounded

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
            exportHistoryRetentionDays = exportHistoryRetentionDays.coerceIn(1, 3_650),
            thumbnailMaxDimension = thumbnailMaxDimension.coerceIn(64, 4_096),
            autoClearLogsAfterDays = autoClearLogsAfterDays.coerceIn(1, 365),
            // Experimental and root-related features stay off unless explicitly
            // enabled by the user; a sanitized copy never turns them on.
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
            exportConfirmBeforeExport = Defaults.exportConfirmBeforeExport,
            exportAskForDestination = Defaults.exportAskForDestination,
            exportPreserveNames = Defaults.exportPreserveNames,
            exportHistoryRetentionDays = Defaults.exportHistoryRetentionDays,
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
