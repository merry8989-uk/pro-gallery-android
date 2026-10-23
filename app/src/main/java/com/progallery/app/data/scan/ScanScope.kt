package com.progallery.app.data.scan

import com.progallery.app.data.model.MediaType
import com.progallery.app.data.settings.AppSettings

/**
 * One line of the scope: something that will or will not be looked at, and why.
 *
 * [included] is the whole point. The master prompt asks for a scan that shows **which locations will
 * actually be scanned before it starts** — and the honest version of that is a list where the things
 * that will *not* be scanned are as visible as the things that will.
 */
data class ScopeLine(
    val label: String,
    val detail: String,
    val included: Boolean,
)

/** Where the scope's "start" should take the person, given what they have switched on. */
enum class ScanTarget {
    /** Nothing is switched on, so there is nowhere honest to go. */
    NONE,

    /** The recovery engine's quick scan: the system's index plus the app's own. */
    RECOVERY,

    /** The deep scan: the folders the person granted, and the app-media index. */
    DEEP_SCAN,
}

/**
 * What a scan would actually do, worked out before it starts.
 *
 * This exists because Settings described a scan and offered no way to run one, and because the two
 * questions a person asks before a scan — *what will it look at* and *what will it miss* — were
 * answerable only by reading the code.
 *
 * It is **pure and built from the real state**, not from the settings alone: the folders are the
 * ones actually granted, and the Android-media line depends on whether this device lets the app
 * read that folder. A scope that listed a folder the app cannot open would be worse than no scope.
 */
data class ScanScope(
    val methods: List<ScopeLine>,
    val kinds: List<String>,
    val folders: List<String>,
    val sizeRange: String,
    val limits: List<ScopeLine>,
    val notes: List<String>,
) {
    /** Whether there is anything to scan at all. */
    val canStart: Boolean
        get() = methods.any { it.included } && kinds.isNotEmpty()

    /**
     * Where "start" goes.
     *
     * Quick scan and the deep scan are different engines, so the scope has to pick one rather than
     * pretend a single button can run both. The order is deliberate: the quick scan is the cheap one
     * and the one that answers "what is in my library", so it wins when both are on.
     */
    val target: ScanTarget
        get() = when {
            !canStart -> ScanTarget.NONE
            methods.first { it.label == QUICK }.included -> ScanTarget.RECOVERY
            else -> ScanTarget.DEEP_SCAN
        }

    companion object {
        const val QUICK = "Quick scan"
        const val DEEP = "Deep media scan"
        const val SAF = "Granted folders"
        const val CACHE = "Thumbnails and caches"
    }
}

/** Builds the scope from the settings and the device's real state. */
object ScanScopeBuilder {

    /**
     * @param grantedFolders the folders the person has actually granted, by name.
     * @param allFilesAccess whether Android has given the app "All files access".
     * @param androidMediaReachable whether `Android/media` can be read on this device — on Android
     *   11 and later, without All files access, it cannot, and the scope must say so rather than
     *   list it as something that will be scanned.
     */
    fun build(
        settings: AppSettings,
        grantedFolders: List<String>,
        allFilesAccess: Boolean,
        androidMediaReachable: Boolean,
    ): ScanScope {
        val methods = listOf(
            ScopeLine(
                label = ScanScope.QUICK,
                detail = "The system's media index, and anything in it that the gallery is not " +
                    "already showing.",
                included = settings.quickScanEnabled,
            ),
            ScopeLine(
                label = ScanScope.DEEP,
                detail = "Every folder you have granted, walked file by file.",
                included = settings.deepScanEnabled,
            ),
            ScopeLine(
                label = ScanScope.SAF,
                detail = if (grantedFolders.isEmpty()) {
                    "No folder has been granted, so there is nothing here to walk."
                } else {
                    "${grantedFolders.size} folder(s): ${grantedFolders.joinToString(", ")}"
                },
                included = settings.safScanEnabled,
            ),
            ScopeLine(
                label = ScanScope.CACHE,
                detail = "Thumbnails and cached previews in the folders you can reach.",
                included = settings.recoveryThumbnailCacheEnabled,
            ),
        )

        val kinds = settings.enabledKinds
            .filter { it != MediaType.ALL }
            .map { it.name.lowercase() }

        val limits = listOf(
            ScopeLine("File sizes", settings.sizeRangeLabel, true),
            ScopeLine(
                "Results",
                "stops at ${settings.maxScanResults}",
                true,
            ),
            ScopeLine(
                "Folder depth",
                "up to ${settings.maxRecursionDepth} levels deep",
                true,
            ),
            ScopeLine(
                "Signature reading",
                if (settings.signatureDetectionEnabled) {
                    "the first ${settings.signatureReadBytes} bytes of each file"
                } else {
                    "off — files are classified by their extension and MIME type alone"
                },
                settings.signatureDetectionEnabled,
            ),
            ScopeLine(
                "Duplicates",
                if (settings.duplicateDetectionEnabled) "found by size, then a sampled hash, then a full hash" else "off",
                settings.duplicateDetectionEnabled,
            ),
            ScopeLine(
                "Hidden files",
                if (settings.includeHiddenFiles) "included" else "skipped",
                settings.includeHiddenFiles,
            ),
        )

        // The notes are the honest part: each one is a reason the scan will do less than a person
        // might expect, said before it starts rather than discovered afterwards.
        val notes = buildList {
            if (!settings.anyScanModeEnabled) {
                add("No scan method is switched on, so a scan cannot start. Turn on Quick scan or Deep media scan first.")
            }
            if (!settings.anyCategoryEnabled) {
                add("Every file category is switched off, so a scan would find nothing. Turn at least one on.")
            }
            if (settings.safScanEnabled && grantedFolders.isEmpty()) {
                add("Folder scanning is on but no folder has been granted, so it has nothing to walk. Add a folder on the Deep scan screen.")
            }
            if (!settings.includeAppMediaInGallery) {
                add("The app media index is off, so media kept by messaging apps is not merged into the gallery.")
            }
            if (!androidMediaReachable) {
                add("`Android/media` cannot be read on this device without All files access, so files there are not in scope.")
            }
            if (!allFilesAccess && grantedFolders.isNotEmpty()) {
                add("Without All files access, only the folders you granted are readable — the rest of the device is not.")
            }
            if (!settings.signatureDetectionEnabled) {
                add("Signature detection is off, so a file whose extension is wrong will be classified by that wrong extension.")
            }
        }

        return ScanScope(
            methods = methods,
            kinds = kinds,
            folders = grantedFolders,
            sizeRange = settings.sizeRangeLabel,
            limits = limits,
            notes = notes,
        )
    }
}
