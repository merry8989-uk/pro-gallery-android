package com.progallery.app.util

import java.util.Locale

/** Formatting and MIME helpers shared across the app. */
object MediaFormatUtils {

    private val IMAGE_MIME_PREFIX = "image/"
    private val VIDEO_MIME_PREFIX = "video/"

    /** MIME types the gallery will surface (device support still varies by codec). */
    val SUPPORTED_IMAGE_MIME = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif",
        "image/heic", "image/heif", "image/avif", "image/bmp",
    )
    val SUPPORTED_VIDEO_MIME = setOf(
        "video/mp4", "video/x-matroska", "video/webm", "video/3gpp",
        "video/quicktime", "video/mpeg", "video/avi",
    )

    fun isImage(mime: String?): Boolean =
        mime != null && (mime.startsWith(IMAGE_MIME_PREFIX) || mime in SUPPORTED_IMAGE_MIME)

    fun isVideo(mime: String?): Boolean =
        mime != null && (mime.startsWith(VIDEO_MIME_PREFIX) || mime in SUPPORTED_VIDEO_MIME)

    fun isSupported(mime: String?): Boolean = isImage(mime) || isVideo(mime)

    /** Human-readable size using binary units. */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    /** Formats an epoch-seconds timestamp as a local date/time string. */
    fun formatDate(epochSeconds: Long, locale: Locale = Locale.getDefault()): String {
        if (epochSeconds <= 0L) return "—"
        val format = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", locale)
        return format.format(java.util.Date(epochSeconds * 1000L))
    }

    /** Formats a duration in milliseconds as m:ss or h:mm:ss. */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Best-effort file extension for a MIME type. */
    fun extensionFor(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        else -> if (isVideo(mime)) "mp4" else "jpg"
    }
}
