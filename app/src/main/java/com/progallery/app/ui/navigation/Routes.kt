package com.progallery.app.ui.navigation

/** Type-safe-ish route constants for the Compose navigation graph. */
object Routes {
    const val HOME = "home"
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val AUDIO = "audio"
    const val DOCUMENTS = "documents"
    const val ALL_FILES = "all_files"
    const val SETTINGS = "settings"
    const val PERMISSION_DIAGNOSTICS = "permission_diagnostics"
    const val ANDROID_FOLDERS = "android_folders"
    const val RECOVERY = "recovery"
    const val PDF_VIEWER = "pdf_viewer"
    const val AUDIO_PLAYER = "audio_player"
    const val AUDIO_EDITOR = "audio_editor"
    const val VIDEOS = "videos"
    const val FAVOURITES = "favourites"
    const val TRASH = "trash"
    const val DEEP_SCAN = "deep_scan"
    const val SEARCH = "search"
    const val STREAM = "stream"
    const val STREAM_PLAYER = "stream_player"
    const val MEMORIES = "memories"
    const val APP_MEDIA = "app_media"
    const val STICKERS = "stickers"
    const val CAPABILITY = "capability"
    const val TEXT_VIEWER = "text_viewer"
    const val CLOUD = "cloud"
    const val CLOUD_VIEWER = "cloud_viewer"
    const val WEBDAV = "webdav"
    const val WEBDAV_PLAYER = "webdav_player"
    const val VIEWER = "viewer"
    const val VIDEO_PLAYER = "video_player"
    const val PHOTO_EDITOR = "photo_editor"
    const val VIDEO_EDITOR = "video_editor"
    const val ALBUM_DETAIL = "album_detail"
    const val CUSTOM_ALBUM_DETAIL = "custom_album"

    fun webDavPlayer(serverId: String, url: String) =
        "$WEBDAV_PLAYER?server=${java.net.URLEncoder.encode(serverId, "UTF-8")}" +
            "&url=${java.net.URLEncoder.encode(url, "UTF-8")}"

    fun textViewer(uri: String, title: String) =
        "$TEXT_VIEWER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}" +
            "&title=${java.net.URLEncoder.encode(title, "UTF-8")}"

    fun cloudViewer(uri: String, title: String) =
        "$CLOUD_VIEWER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}" +
            "&title=${java.net.URLEncoder.encode(title, "UTF-8")}"

    fun streamPlayer(url: String, title: String? = null) =
        "$STREAM_PLAYER?url=${java.net.URLEncoder.encode(url, "UTF-8")}" +
            (title?.let { "&title=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")

    fun albumDetail(bucketId: Long, name: String) = "$ALBUM_DETAIL/$bucketId/${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun customAlbumDetail(albumId: Long, name: String) = "$CUSTOM_ALBUM_DETAIL/$albumId/${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun viewer(uri: String, mediaType: String) = "$VIEWER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}&type=$mediaType"
    fun videoPlayer(uri: String) = "$VIDEO_PLAYER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    fun photoEditor(uri: String) = "$PHOTO_EDITOR?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    fun videoEditor(uri: String) = "$VIDEO_EDITOR?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    fun pdfViewer(uri: String) = "$PDF_VIEWER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    fun audioPlayer(uri: String) = "$AUDIO_PLAYER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    fun audioEditor(uri: String, cut: Boolean) = "$AUDIO_EDITOR?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}&cut=$cut"
}
