package com.progallery.app.data.webdav

/**
 * A WebDAV server the user added.
 *
 * The password is deliberately **not** part of this model: it is kept in the app's
 * private storage, is never written to the settings export, and is never logged.
 */
data class WebDavServer(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
)

/** One entry in a WebDAV collection. */
data class WebDavEntry(
    val url: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val mimeType: String?,
    val lastModifiedMillis: Long?,
) {
    /** True when this looks like something the app can play or show. */
    val isMedia: Boolean
        get() = mimeType?.startsWith("video/") == true ||
            mimeType?.startsWith("audio/") == true ||
            mimeType?.startsWith("image/") == true
}
