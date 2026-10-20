package com.progallery.app.ui.navigation

/** Type-safe-ish route constants for the Compose navigation graph. */
object Routes {
    const val HOME = "home"
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val VIDEOS = "videos"
    const val FAVOURITES = "favourites"
    const val TRASH = "trash"
    const val DEEP_SCAN = "deep_scan"
    const val SEARCH = "search"
    const val VIEWER = "viewer"
    const val VIDEO_PLAYER = "video_player"
    const val PHOTO_EDITOR = "photo_editor"
    const val VIDEO_EDITOR = "video_editor"
    const val ALBUM_DETAIL = "album_detail"
    const val CUSTOM_ALBUM_DETAIL = "custom_album"

    fun albumDetail(bucketId: Long, name: String) = "$ALBUM_DETAIL/$bucketId/${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun customAlbumDetail(albumId: Long, name: String) = "$CUSTOM_ALBUM_DETAIL/$albumId/${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun viewer(uri: String, mediaType: String) = "$VIEWER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}&type=$mediaType"
    fun videoPlayer(uri: String) = "$VIDEO_PLAYER?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    fun photoEditor(uri: String) = "$PHOTO_EDITOR?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
    fun videoEditor(uri: String) = "$VIDEO_EDITOR?uri=${java.net.URLEncoder.encode(uri, "UTF-8")}"
}
