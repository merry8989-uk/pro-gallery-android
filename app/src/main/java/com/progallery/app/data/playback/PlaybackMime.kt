package com.progallery.app.data.playback

/**
 * What the app tells Media3 a file is.
 *
 * Every player used to call `MediaItem.fromUri`, leaving the format to be sniffed. Sniffing is
 * good and usually right — but it is a guess made from the first bytes, and it has two weak
 * spots: a container whose header is ambiguous, and a content URI that carries no extension at
 * all. A hint removes the guess in exactly those cases.
 *
 * **A wrong hint is worse than no hint.** Telling Media3 the wrong thing makes it pick the wrong
 * extractor and fail on a file that would otherwise have played. So the table below holds only
 * mappings that are certain, and everything else returns null — which means "sniff it", the
 * behaviour that was there before.
 *
 * Pure, so every mapping is unit tested.
 */
object PlaybackMime {

    /**
     * Extensions whose type is not in doubt.
     *
     * Deliberately short. An entry is added when the mapping is certain, not when it is likely.
     */
    private val CERTAIN = mapOf(
        // video containers
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mov" to "video/quicktime",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "avi" to "video/x-msvideo",
        "flv" to "video/x-flv",
        "f4v" to "video/mp4",
        "3gp" to "video/3gpp",
        "3g2" to "video/3gpp2",
        "ts" to "video/mp2t",
        "m2ts" to "video/mp2t",
        // audio containers
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "m4b" to "audio/mp4",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "oga" to "audio/ogg",
        "opus" to "audio/ogg",
        "ogg" to "audio/ogg",
        "amr" to "audio/amr",
        "ac3" to "audio/ac3",
        "mka" to "audio/x-matroska",
        // adaptive streams — these are playlists, and the type decides the source
        "m3u8" to "application/x-mpegURL",
        "m3u" to "application/x-mpegURL",
        "mpd" to "application/dash+xml",
    )

    /**
     * The MIME hint for an item, or null to let Media3 sniff.
     *
     * The item's own type wins when it is a real media type, because that came from the system
     * rather than from a file name. A generic type is not a real answer — `application/octet-stream`
     * and the catch-all `video/*` say nothing about which extractor to use — so those are treated
     * as absent rather than passed on.
     */
    fun hintFor(mimeType: String?, displayName: String?, uri: String?): String? {
        val declared = mimeType?.trim()?.lowercase()
        if (declared != null && isUsable(declared)) return declared

        val extension = extensionOf(displayName) ?: extensionOf(uri) ?: return null
        return CERTAIN[extension]
    }

    /** Whether a declared type actually says which extractor to use. */
    fun isUsable(mimeType: String): Boolean {
        if (mimeType.isBlank()) return false
        if (mimeType == "application/octet-stream" || mimeType == "*/*") return false
        // A wildcard subtype names no format.
        if (mimeType.endsWith("/*")) return false
        return mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/") ||
            mimeType.startsWith("application/")
    }

    /**
     * The extension of a name or address, ignoring a query string.
     *
     * A stream address carries its format before the query — `clip.mp4?token=…` is an MP4 — and
     * reading past the `?` would look for an extension in the token.
     */
    fun extensionOf(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val withoutQuery = value.substringBefore('?').substringBefore('#')
        val lastSegment = withoutQuery.substringAfterLast('/')
        val dot = lastSegment.lastIndexOf('.')
        if (dot <= 0 || dot == lastSegment.length - 1) return null
        return lastSegment.substring(dot + 1).lowercase().takeIf { it.length in 1..8 && it.all { c -> c.isLetterOrDigit() } }
    }
}
