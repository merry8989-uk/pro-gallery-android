package com.progallery.app.data.stream

import java.net.URI
import java.net.URISyntaxException

/** What a link looks like it points at. */
enum class StreamKind(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    IMAGE("Image"),
    /** HLS or DASH — a stream that Media3 assembles as it plays. */
    PLAYLIST("Stream"),
    /** The link is fine; what it holds cannot be told from the address alone. */
    UNKNOWN("Link"),
}

/** A link the user pasted, understood as far as an address allows. */
data class StreamLink(
    val url: String,
    val kind: StreamKind,
    val host: String,
)

/**
 * Reads a pasted link.
 *
 * Pure, so every rule about what the app will and will not open is unit tested.
 *
 * Two deliberate choices:
 *
 *  * **Only `http` and `https`.** A `file:`, `content:`, `javascript:` or `ftp:` link is
 *    refused with a plain reason rather than handed to the player, which would either
 *    fail obscurely or mean something the user did not intend.
 *  * **A bare address gets `https://`.** Pasting `example.com/clip.mp4` is a normal thing
 *    to do, and `https` is the only safe guess.
 *
 * What the link holds is guessed from the path's extension, and the guess is allowed to
 * be wrong: [StreamKind.UNKNOWN] is not a rejection. Media3 sniffs the content when it
 * opens the stream, so an unrecognised address still gets a fair chance to play.
 */
object StreamLinks {

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")

    private val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "flv")
    private val PLAYLIST = setOf("m3u8", "m3u", "mpd")
    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma")
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    fun parse(raw: String): Result<StreamLink> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Paste a link first."))
        }

        val declaredScheme = SCHEME.find(trimmed)?.groupValues?.get(1)?.lowercase()
        if (declaredScheme != null && declaredScheme !in ALLOWED_SCHEMES) {
            return Result.failure(
                IllegalArgumentException(
                    "Only http and https links can be streamed; \"$declaredScheme\" is not one of them.",
                ),
            )
        }

        val candidate = if (declaredScheme == null) "https://$trimmed" else trimmed

        val uri = try {
            URI(candidate)
        } catch (t: URISyntaxException) {
            return Result.failure(IllegalArgumentException("That does not look like a link the app can open."))
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return Result.failure(
                IllegalArgumentException("That link has no address the app can reach."),
            )
        }

        return Result.success(
            StreamLink(
                url = candidate,
                kind = kindOf(uri.path.orEmpty()),
                host = host,
            ),
        )
    }

    /** The kind implied by a path's extension, ignoring case. */
    private fun kindOf(path: String): StreamKind {
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return StreamKind.UNKNOWN
        return when (name.substring(dot + 1).lowercase()) {
            in VIDEO -> StreamKind.VIDEO
            in PLAYLIST -> StreamKind.PLAYLIST
            in AUDIO -> StreamKind.AUDIO
            in IMAGE -> StreamKind.IMAGE
            else -> StreamKind.UNKNOWN
        }
    }

    private val ALLOWED_SCHEMES = setOf("http", "https")
}
