package com.progallery.app.data.appmedia

/**
 * Works out **which app a media file belongs to** from where it is stored.
 *
 * This is the honest way round: the folder on the device says which app wrote the file, so
 * nothing has to be guessed. A hardcoded list of package names can only ever cover the apps
 * someone thought of, and getting one wrong silently attributes a stranger's media to the wrong
 * app.
 *
 * Two layouts exist, and both are still found on real devices:
 *
 *  * **`Android/media/<package>/…`** — where apps have put their media since Android 11.
 *  * **Top-level `/WhatsApp/`, `/Telegram/` and similar** — the older layout, still present on
 *    devices that upgraded rather than reinstalled.
 *
 * Pure, so the parsing is tested rather than trusted.
 */
object AppMediaPaths {

    /** The `Android/media` parent, as it appears in a path. */
    private const val MEDIA_PARENT = "Android/media/"

    private const val EMULATED_ROOT = "/emulated/0/"
    private const val SDCARD_ROOT = "/sdcard/"

    /**
     * Top-level folders that belong to a known app, by their name on disk.
     *
     * Only used when a file sits outside `Android/media` — inside it, the folder name is
     * already the package and is used instead.
     */
    private val LEGACY_FOLDERS = mapOf(
        "WhatsApp" to "com.whatsapp",
        "WhatsApp Business" to "com.whatsapp.w4b",
        "Telegram" to "org.telegram.messenger",
    )

    /**
     * The package that owns [path], or null when the path is not an app's media.
     *
     * The segment immediately after `Android/media/` is the package name, which is why no list
     * of apps is needed for the modern layout.
     */
    fun packageOf(path: String): String? {
        val normalised = path.replace('\\', '/')
        val marker = normalised.indexOf(MEDIA_PARENT)
        if (marker < 0) return null

        val rest = normalised.substring(marker + MEDIA_PARENT.length)
        val segment = rest.substringBefore('/')
        return segment.takeIf { it.isNotBlank() && it.contains('.') }
    }

    /** True when the path is anywhere under an app's media folder. */
    fun isAppMedia(path: String): Boolean = packageOf(path) != null

    /**
     * The package behind a legacy top-level folder such as `/storage/emulated/0/WhatsApp/…`,
     * or null when the path is not one.
     */
    fun legacyPackageOf(path: String): String? {
        val normalised = path.replace('\\', '/')
        // Ignore anything already handled by the modern layout.
        if (packageOf(normalised) != null) return null

        // The first segment after the storage root. The root is stripped once: chaining
        // `substringAfter` calls would let the second one fall back to the whole path and
        // quietly undo the first, which is exactly the bug this replaced.
        val withoutRoot = when {
            normalised.contains(EMULATED_ROOT) -> normalised.substringAfter(EMULATED_ROOT)
            normalised.contains(SDCARD_ROOT) -> normalised.substringAfter(SDCARD_ROOT)
            else -> normalised
        }
        return LEGACY_FOLDERS[withoutRoot.substringBefore('/')]
    }

    /** Which app a file belongs to, whichever layout it is in. */
    fun ownerOf(path: String): String? = packageOf(path) ?: legacyPackageOf(path)
}
