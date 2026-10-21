package com.progallery.app.data.usage

/**
 * The complete set of things this app is able to record.
 *
 * **Closed on purpose, and it is the whole privacy guarantee.** A record is built from these
 * enums and numbers only, so there is no field a filename, a folder path, a search term or a
 * person's name could be written into — not because the code is careful, but because the type
 * has nowhere to put one. That is a property a test can check, and `UsagePrivacyTest` does.
 */
enum class UsageEvent(val id: String) {
    APP_OPENED("app.opened"),
    FEATURE_OPENED("feature.opened"),
    SEARCH_PERFORMED("search.performed"),
    ALBUM_CREATED("album.created"),
    FAVOURITE_ADDED("favourite.added"),
    UPDATE_CHECKED("update.checked"),
    UPDATE_DOWNLOADED("update.downloaded"),
    RECOVERY_RUN("recovery.run"),
    DEEP_SCAN_RUN("deepscan.run"),
    EXPORT_RUN("export.run"),
    SETTINGS_CHANGED("settings.changed"),
    PERMISSION_REFUSED("permission.refused"),
}

/** Which part of the app something happened in. Also closed. */
enum class FeatureId(val id: String) {
    HOME("home"),
    PHOTOS("photos"),
    VIDEOS("videos"),
    AUDIO("audio"),
    DOCUMENTS("documents"),
    ALL_FILES("all_files"),
    ALBUMS("albums"),
    FAVOURITES("favourites"),
    MEMORIES("memories"),
    APP_MEDIA("app_media"),
    STICKERS("stickers"),
    ANDROID_FOLDERS("android_folders"),
    CLOUD("cloud"),
    WEBDAV("webdav"),
    STREAM("stream"),
    SEARCH("search"),
    RECOVERY("recovery"),
    TRASH("trash"),
    DEEP_SCAN("deep_scan"),
    SETTINGS("settings"),
    VIEWER("viewer"),
    PLAYER("player"),
    EDITOR("editor"),
}

/** Operations whose duration is measured. Closed, so no operation name can be invented. */
enum class PerfOperation(val id: String) {
    LIBRARY_LOAD("library.load"),
    SEARCH("search"),
    THUMBNAIL("thumbnail"),
    SCAN("scan"),
    MEMORIES("memories"),
    APP_MEDIA("app_media"),
    STICKERS("stickers"),
}

/**
 * The part of the app a navigation route belongs to, or null when the route is not a place.
 *
 * Most routes are already named after their feature. The rest are the viewers, players and
 * editors, which are entered from a feature and belong to it — a photo opened from Photos is
 * Photos being used.
 *
 * A pure mapping, tested rather than trusted: a screen recorded under the wrong feature would
 * quietly make the whole review wrong, and nothing on screen would look broken.
 */
fun featureForRoute(route: String?): FeatureId? {
    val clean = route?.substringBefore('?')?.substringBefore('/')?.trim().orEmpty()
    if (clean.isEmpty()) return null

    FeatureId.entries.firstOrNull { it.id == clean }?.let { return it }

    return when (clean) {
        "viewer", "pdf_viewer", "text_viewer" -> FeatureId.VIEWER
        "video_player", "audio_player", "stream_player", "webdav_player" -> FeatureId.PLAYER
        "photo_editor", "video_editor", "audio_editor" -> FeatureId.EDITOR
        "cloud_viewer" -> FeatureId.CLOUD
        "album_detail", "custom_album" -> FeatureId.ALBUMS
        "permission_diagnostics" -> FeatureId.SETTINGS
        else -> null
    }
}
