package com.progallery.app.data.media

/**
 * Explains why playback failed, in terms of the actual reason.
 *
 * This exists because the app used to blame the codec for **every** failure, which was simply
 * wrong for most of them. `ERROR_CODE_IO_FILE_NOT_FOUND` means the file could not be opened at
 * all — it has been moved or deleted, or it sits in a folder Android does not let this app
 * read. Telling the user their device lacks a codec, when the file is not even reachable, sends
 * them looking in the wrong place.
 *
 * The codes are Media3's own, and the mapping is pure so every claim is unit tested.
 */
object PlaybackFailure {

    /** Media3's `PlaybackException` error codes, named here so the mapping reads clearly. */
    private const val UNSPECIFIED = 1000
    private const val TIMEOUT = 1003
    private const val IO_UNSPECIFIED = 2000
    private const val IO_NETWORK_CONNECTION_FAILED = 2001
    private const val IO_NETWORK_CONNECTION_TIMEOUT = 2002
    private const val IO_INVALID_HTTP_CONTENT_TYPE = 2003
    private const val IO_BAD_HTTP_STATUS = 2004
    private const val IO_FILE_NOT_FOUND = 2005
    private const val IO_NO_PERMISSION = 2006
    private const val IO_CLEARTEXT_NOT_PERMITTED = 2007
    private const val IO_READ_POSITION_OUT_OF_RANGE = 2008
    private const val PARSING_CONTAINER_MALFORMED = 3001
    private const val PARSING_MANIFEST_MALFORMED = 3002
    private const val PARSING_CONTAINER_UNSUPPORTED = 3003
    private const val PARSING_MANIFEST_UNSUPPORTED = 3004
    private const val DECODER_INIT_FAILED = 4001
    private const val DECODER_QUERY_FAILED = 4002
    private const val DECODING_FAILED = 4003
    private const val DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004
    private const val DECODING_FORMAT_UNSUPPORTED = 4005
    private const val AUDIO_TRACK_INIT_FAILED = 5001
    private const val AUDIO_TRACK_WRITE_FAILED = 5002

    /** What the user should do about it, when there is something to do. */
    enum class Action {
        /** Nothing to offer: the reason is what it is. */
        NONE,

        /** Re-read the library, because the entry may be stale. */
        RESCAN,

        /** Hand the file to another app that may read it. */
        ANOTHER_APP,

        /** Check the connection. */
        RETRY,
    }

    data class Explanation(
        /** One line naming the reason. */
        val reason: String,
        /** What it means and what to do, in words worth reading. */
        val detail: String,
        val action: Action,
    )

    fun explain(errorCode: Int, isLink: Boolean = false): Explanation = when (errorCode) {
        IO_FILE_NOT_FOUND -> Explanation(
            reason = "The file could not be opened",
            detail = "It has been moved or deleted, or it is in a folder Android does not let " +
                "this app read. Other apps' folders — WhatsApp's, for example — are hidden " +
                "unless you grant the folder or allow all files access.",
            action = Action.RESCAN,
        )

        IO_NO_PERMISSION -> Explanation(
            reason = "Android did not give permission to read this file",
            detail = "The app has the file's address but not permission to open it. Granting " +
                "the folder, or all files access, is what changes that.",
            action = Action.RESCAN,
        )

        IO_NETWORK_CONNECTION_FAILED, IO_NETWORK_CONNECTION_TIMEOUT -> Explanation(
            reason = "The server could not be reached",
            detail = "The connection failed or timed out. Check the network and try again.",
            action = Action.RETRY,
        )

        IO_BAD_HTTP_STATUS -> Explanation(
            reason = "The server refused the request",
            detail = "The address answered with an error. A link that needs a sign-in, or one " +
                "that has expired, will do this.",
            action = Action.ANOTHER_APP,
        )

        IO_CLEARTEXT_NOT_PERMITTED -> Explanation(
            reason = "This address uses plain http",
            detail = "Android blocks unencrypted connections by default. An https address works.",
            action = Action.NONE,
        )

        IO_INVALID_HTTP_CONTENT_TYPE -> Explanation(
            reason = "The address did not return a media file",
            detail = "It answered with something that is not audio or video — usually a web " +
                "page rather than a direct link to the file.",
            action = Action.ANOTHER_APP,
        )

        IO_READ_POSITION_OUT_OF_RANGE -> Explanation(
            reason = "The file ended earlier than expected",
            detail = "The file is shorter than it claims to be, which usually means it was " +
                "copied incompletely or is damaged.",
            action = Action.ANOTHER_APP,
        )

        // For a link, the overwhelmingly common cause is that the address is a page and not a
        // file at all — which is a different answer from "Android cannot read this container",
        // and a more useful one. That mistake was made once already; it is not repeated here.
        PARSING_CONTAINER_UNSUPPORTED, PARSING_MANIFEST_UNSUPPORTED -> if (isLink) {
            Explanation(
                reason = "The address did not return a media file",
                detail = "It answered with a web page rather than a stream. A page is shown by " +
                    "a browser, not played by a player. A direct file link ends in .mp4, .webm, " +
                    ".mkv, .m3u8 or .mpd.",
                action = Action.ANOTHER_APP,
            )
        } else {
            Explanation(
                reason = "This kind of file cannot be played",
                detail = "The container is not one Android can read. MPEG program streams " +
                    "(.mpg), Windows Media (.wmv) and Flash (.swf) have no support on Android " +
                    "at all.",
                action = Action.ANOTHER_APP,
            )
        }

        PARSING_CONTAINER_MALFORMED, PARSING_MANIFEST_MALFORMED -> Explanation(
            reason = "The file is damaged",
            detail = "Its structure could not be read, which usually means the file itself is " +
                "incomplete or corrupt.",
            action = Action.ANOTHER_APP,
        )

        DECODING_FORMAT_UNSUPPORTED, DECODING_FORMAT_EXCEEDS_CAPABILITIES -> Explanation(
            reason = "This device cannot decode the video or audio inside the file",
            detail = "The container is fine, but the codec inside it is not supported here. " +
                "This is the case where another app may still manage it.",
            action = Action.ANOTHER_APP,
        )

        DECODER_INIT_FAILED, DECODER_QUERY_FAILED, DECODING_FAILED -> Explanation(
            reason = "The decoder failed to start",
            detail = "The device's own decoder could not be set up for this file. Trying again " +
                "sometimes works; another app is the other option.",
            action = Action.ANOTHER_APP,
        )

        AUDIO_TRACK_INIT_FAILED, AUDIO_TRACK_WRITE_FAILED -> Explanation(
            reason = "The audio output failed",
            detail = "The device could not open its audio output for this file.",
            action = Action.ANOTHER_APP,
        )

        TIMEOUT -> Explanation(
            reason = "Playback timed out",
            detail = "The file took too long to start.",
            action = Action.RETRY,
        )

        IO_UNSPECIFIED, UNSPECIFIED -> Explanation(
            reason = "The file could not be played",
            detail = "The reason was not reported. Trying another app is the surest way to " +
                "find out whether the file or the app is the problem.",
            action = Action.ANOTHER_APP,
        )

        else -> Explanation(
            reason = "The file could not be played",
            detail = "The reason was not reported. Trying another app is the surest way to " +
                "find out whether the file or the app is the problem.",
            action = Action.ANOTHER_APP,
        )
    }

    /**
     * The message shown to the user.
     *
     * The file's name is included when it is known, because "something failed" is not useful
     * when a playlist of thirty-two videos is open.
     */
    fun message(errorCode: Int, fileName: String? = null, isLink: Boolean = false): String {
        val explanation = explain(errorCode, isLink)
        val prefix = fileName?.takeIf { it.isNotBlank() }?.let { "\"$it\" — " } ?: ""
        return "$prefix${explanation.reason}.\n\n${explanation.detail}"
    }
}
