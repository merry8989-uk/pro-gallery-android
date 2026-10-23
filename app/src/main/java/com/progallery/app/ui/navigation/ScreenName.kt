package com.progallery.app.ui.navigation

/**
 * One name per screen, for the log.
 *
 * The diagnostics from a real device showed the same screen logged under **two** names —
 * `settings` and `Settings`, `audio` and `Audio`, `photos` and `Photos`, `documents` and
 * `Documents`. The navigation host logged the **route**, and two screens logged a **display name**
 * of their own, so every count in the weekly review was split in half. The data used to decide what
 * to improve was itself the thing that most needed improving.
 *
 * This is the one place a route becomes a name. It is pure, so a test can assert that every route
 * has one and that no two routes share one.
 */
object ScreenName {

    /**
     * The canonical name for a route, or null for a route that is not a screen.
     *
     * A route carrying arguments (`recovery?start={start}`) is matched on the part before the `?`,
     * because the arguments are not part of which screen it is.
     */
    fun of(route: String?): String? {
        val bare = route?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: return null
        return NAMES[bare]
    }

    /**
     * Every screen, by the name it is logged under.
     *
     * The names are the ones a person would recognise from the app rather than the route strings:
     * `AccessDiagnostics` rather than `permission_diagnostics`, because this is what appears in a
     * log they might read.
     */
    private val NAMES: Map<String, String> = mapOf(
        Routes.HOME to "Home",
        Routes.PHOTOS to "Photos",
        Routes.VIDEOS to "Videos",
        Routes.ALBUMS to "Albums",
        Routes.AUDIO to "Audio",
        Routes.DOCUMENTS to "Documents",
        Routes.ALL_FILES to "AllFiles",
        Routes.FAVOURITES to "Favourites",
        Routes.TRASH to "Trash",
        Routes.SEARCH to "Search",
        Routes.SETTINGS to "Settings",
        Routes.PERMISSION_DIAGNOSTICS to "AccessDiagnostics",
        Routes.ANDROID_FOLDERS to "AndroidFolders",
        Routes.RECOVERY to "Recovery",
        Routes.DEEP_SCAN to "DeepScan",
        Routes.SCAN_SCOPE to "ScanScope",
        Routes.PDF_VIEWER to "PdfViewer",
        Routes.AUDIO_EDITOR to "AudioEditor",
        Routes.STREAM to "Stream",
    )

    /** Every name, for a test to assert uniqueness against. */
    fun all(): Map<String, String> = NAMES
}
