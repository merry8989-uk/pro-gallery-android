package com.progallery.app.data.update

/** A release as read from the project's releases feed. */
data class UpdateRelease(
    /** The tag as published, e.g. `v1.16.0`. */
    val tagName: String,
    /** The tag without its leading `v`, so it can be compared with the installed name. */
    val versionName: String,
    val title: String?,
    val notes: String?,
    /** The release page, always present. */
    val pageUrl: String,
    /** The installable APK asset, when the release carries one. */
    val apkUrl: String?,
    val prerelease: Boolean,
) {
    /** The version as a person would say it. */
    val displayVersion: String get() = versionName.ifBlank { tagName }
}

/** Reads the releases feed's reply. */
object ReleaseJson {

    fun parse(text: String): Result<UpdateRelease> {
        val root = try {
            Json.objectAt(Json.parse(text))
        } catch (t: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("The update server's reply could not be read.", t))
        } ?: return Result.failure(IllegalArgumentException("The update server's reply was not in the expected form."))

        val tag = Json.stringAt(root, "tag_name")
            ?: return Result.failure(IllegalArgumentException("The latest release has no version tag."))
        val page = Json.stringAt(root, "html_url")
            ?: return Result.failure(IllegalArgumentException("The latest release has no page address."))

        return Result.success(
            UpdateRelease(
                tagName = tag,
                versionName = tag.trim().removePrefix("v").removePrefix("V"),
                title = Json.stringAt(root, "name"),
                notes = Json.stringAt(root, "body"),
                pageUrl = page,
                apkUrl = apkUrl(root),
                prerelease = Json.booleanAt(root, "prerelease") ?: false,
            ),
        )
    }

    /**
     * The installable asset.
     *
     * A release carries more than one file — the signed bundle for Play, and the APK for
     * sideloading. Only the APK can be installed by hand, so that is what is looked for, and
     * the debug build is preferred because that is what the app's own releases ship for
     * sideloading.
     */
    private fun apkUrl(root: Map<String, Any?>): String? {
        val assets = Json.arrayAt(root["assets"]) ?: return null
        val files = assets.mapNotNull { Json.objectAt(it) }.mapNotNull { asset ->
            val name = Json.stringAt(asset, "name") ?: return@mapNotNull null
            val url = Json.stringAt(asset, "browser_download_url") ?: return@mapNotNull null
            name to url
        }
        return files.firstOrNull { it.first.endsWith(".apk", ignoreCase = true) && it.first.contains("debug") }?.second
            ?: files.firstOrNull { it.first.endsWith(".apk", ignoreCase = true) }?.second
    }
}
