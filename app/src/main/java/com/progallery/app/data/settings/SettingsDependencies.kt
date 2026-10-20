package com.progallery.app.data.settings

/**
 * The rules that keep the switches honest.
 *
 * A setting that is on while the thing it needs is off would be a lie: the switch
 * would claim one thing and the app would do another. Every change is passed through
 * [resolve], so a dependent is turned **off** with its prerequisite instead of being
 * left claiming to be active.
 *
 * Two deliberate non-rules:
 *
 *  * Leaving Advanced mode does **not** switch advanced settings off. It hides them.
 *    Silently discarding a configuration the user built would be worse than hiding it,
 *    and the settings are still honoured.
 *  * Nothing here ever turns something **on**. Resolution only removes claims, so it
 *    can never surprise the user by enabling a feature they did not choose.
 */
object SettingsDependencies {

    /**
     * Returns [settings] with every impossible combination removed.
     *
     * Idempotent: resolving an already-resolved configuration changes nothing.
     */
    fun resolve(settings: AppSettings): AppSettings {
        var result = settings

        // Deep scan walks folders the user granted, so it is meaningless without
        // folder access.
        if (!result.safScanEnabled && result.deepScanEnabled) {
            result = result.copy(deepScanEnabled = false)
        }

        val anyScanMode = result.quickScanEnabled || result.deepScanEnabled || result.safScanEnabled

        // Signature identification and duplicate detection only happen during a scan.
        if (!anyScanMode) {
            if (result.signatureDetectionEnabled) {
                result = result.copy(signatureDetectionEnabled = false)
            }
            if (result.duplicateDetectionEnabled) {
                result = result.copy(duplicateDetectionEnabled = false)
            }
        }

        // Duplicates are only found by walking folders.
        if (!result.deepScanEnabled && !result.safScanEnabled && result.duplicateDetectionEnabled) {
            result = result.copy(duplicateDetectionEnabled = false)
        }

        // Hidden and extensionless files only turn up in a folder walk.
        val folderWalk = result.deepScanEnabled || result.safScanEnabled
        if (!folderWalk && result.includeHiddenFiles) {
            result = result.copy(includeHiddenFiles = false)
        }

        // Per-kind previews are a refinement of thumbnail generation, not an
        // alternative to it.
        if (!result.generateThumbnails) {
            if (result.videoThumbnails || result.audioArtwork || result.documentPreviews) {
                result = result.copy(
                    videoThumbnails = false,
                    audioArtwork = false,
                    documentPreviews = false,
                )
            }
        }

        // Export needs something to export from.
        if (!result.recoveryQuickScanEnabled && !result.recoveryThumbnailCacheEnabled &&
            result.exportEnabled
        ) {
            result = result.copy(exportEnabled = false)
        }

        // Keeping a history of what was exported needs the log that records it.
        if (!result.saveDiagnosticLogs && result.includePathsInLogs) {
            result = result.copy(includePathsInLogs = false)
        }

        return result
    }

    /**
     * What [resolve] changed, in plain words, so the screen can say why a switch
     * flipped instead of leaving the user puzzled.
     */
    fun explain(before: AppSettings, after: AppSettings): List<String> {
        val notes = ArrayList<String>()
        if (before.deepScanEnabled && !after.deepScanEnabled) {
            notes += "Deep media scan is off because folder access is off."
        }
        if (before.signatureDetectionEnabled && !after.signatureDetectionEnabled) {
            notes += "Signature detection is off because no scan method is on."
        }
        if (before.duplicateDetectionEnabled && !after.duplicateDetectionEnabled) {
            notes += "Duplicate detection is off because it needs a scan method."
        }
        if (before.includeHiddenFiles && !after.includeHiddenFiles) {
            notes += "Including hidden files is off because no folder walk is on."
        }
        if ((before.videoThumbnails || before.audioArtwork || before.documentPreviews) &&
            !(after.videoThumbnails || after.audioArtwork || after.documentPreviews)
        ) {
            notes += "Per-type previews are off because thumbnail generation is off."
        }
        if (before.exportEnabled && !after.exportEnabled) {
            notes += "Export is off because no recovery scan is on."
        }
        if (before.includePathsInLogs && !after.includePathsInLogs) {
            notes += "Including file paths in logs is off because diagnostic logging is off."
        }
        return notes
    }
}
