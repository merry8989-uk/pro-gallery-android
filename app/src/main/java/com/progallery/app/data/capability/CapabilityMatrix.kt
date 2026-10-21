package com.progallery.app.data.capability

import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.ImageDecodability
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.stickers.Stickers
import com.progallery.app.data.text.TextFormats

/** What the app can do with a format. */
enum class Capability(val label: String) {
    /** Opens here, with the app's own player, viewer or reader. */
    OPENS_HERE("Opens here"),

    /** Opens here, but something about it may not work — said rather than discovered. */
    OPENS_WITH_CAVEAT("Opens, with a caveat"),

    /** No way to open it here; another app is the answer. */
    NEEDS_ANOTHER_APP("Needs another app"),
}

/** One format's answer. */
data class FormatCapability(
    val extension: String,
    val kind: MediaType,
    val verdict: Capability,
    /** Why, when there is something to say. Null when the format simply opens. */
    val reason: String?,
)

/**
 * The app's honest answer to "can this be opened here?".
 *
 * **Generated from the same tables the rest of the app uses**, not written out again: the image
 * verdicts come from `ImageDecodability`, the sticker verdict from `Stickers`, the text verdict
 * from `TextFormats`, and the universe of formats from `FileTypeRegistry`. A capability screen
 * that disagreed with the player would be worse than none, and the only way to guarantee it does
 * not is to derive both from one source.
 *
 * The remaining knowledge — which containers Android has no parser for at all — is stated here
 * once, as data, because that is a fact about the platform rather than about this app.
 *
 * Pure, and tested against the registry so a format cannot be added and silently left out.
 */
object CapabilityMatrix {

    /**
     * Containers with **no extractor on Android at all**, so no permission, codec pack or
     * setting changes the answer. This is the list the player's failure message names.
     */
    private val NO_PARSER = mapOf(
        // MPEG program streams. The transport stream (.ts) is supported; the *program* stream
        // is a different container and has no extractor.
        "mpg" to "MPEG program stream — Android has no parser for it",
        "mpeg" to "MPEG program stream — Android has no parser for it",
        "mpe" to "MPEG program stream — Android has no parser for it",
        "m2v" to "MPEG program stream — Android has no parser for it",
        "mpv" to "MPEG program stream — Android has no parser for it",
        "vob" to "DVD video object, an MPEG program stream — Android has no parser for it",
        "ifo" to "A DVD's index file, not media itself",
        // ASF and its relatives
        "wmv" to "Windows Media — the ASF container has no parser on Android",
        "asf" to "Windows Media — the ASF container has no parser on Android",
        "wma" to "Windows Media Audio — the ASF container has no parser on Android",
        // Flash
        "swf" to "Flash — Android has no parser or player for it",
        // RealMedia
        "rm" to "RealMedia — Android has no parser for it",
        "rmvb" to "RealMedia — Android has no parser for it",
        // Video containers with no extractor
        "svi" to "A Samsung video container Android has no parser for",
        "mxf" to "MXF, a broadcast container Android has no parser for",
        "dv" to "DV video — Android has no parser for it",
        "roq" to "Id RoQ video — Android has no parser for it",
        "y4m" to "YUV4MPEG — Android has no parser for it",
        "nut" to "NUT container — Android has no parser for it",
        "bik" to "Bink video — Android has no parser for it",
        // Audio codecs and containers with no decoder
        "ape" to "Monkey's Audio — Android has no decoder for it",
        "ra" to "RealAudio — Android has no decoder for it",
        "ram" to "A RealAudio link, not the audio itself",
        "caf" to "Apple's CAF container — Android has no parser for it",
        "m4p" to "Protected (DRM) audio — it cannot be played by an app",
        "wv" to "WavPack — Android has no decoder for it",
        "tta" to "True Audio — Android has no decoder for it",
        "dsf" to "DSD audio — Android has no decoder for it",
        "dff" to "DSD audio — Android has no decoder for it",
        "au" to "Sun AU audio — Android has no decoder for it",
        "snd" to "Sun AU audio — Android has no decoder for it",
        "gsm" to "Raw GSM audio — Android has no decoder for it",
        "voc" to "Creative Voice — Android has no decoder for it",
        "8svx" to "Amiga IFF audio — Android has no decoder for it",
        "s3m" to "A tracker module — Android has no player for it",
        "xm" to "A tracker module — Android has no player for it",
        "it" to "A tracker module — Android has no player for it",
        "mod" to "A tracker module — Android has no player for it",
    )

    /**
     * Formats the container can be read for, but whose contents may still not decode.
     *
     * AVI is the ordinary case: the container is supported, but an AVI may hold a codec the
     * device has no decoder for. Saying so is better than a promise that half works.
     */
    private val CAVEATS = mapOf(
        "avi" to "The container is supported, but an AVI may hold a codec this device cannot decode",
        "divx" to "Usually an AVI inside; whether it plays depends on the codec",
        "qt" to "Old QuickTime files may use a container the platform cannot read",
        "ogv" to "The Ogg container is read, but Android has no Theora decoder",
        "flv" to "The container is read; older Flash codecs may not decode",
        "amv" to "An AVI-like container; whether it plays depends on the codec",
        "spx" to "Speex in Ogg — whether it decodes depends on the device",
        "aif" to "Whether AIFF decodes depends on the device",
        "aiff" to "Whether AIFF decodes depends on the device",
        "aifc" to "Whether AIFF decodes depends on the device",
        "ac3" to "The device must be able to decode AC-3",
        "eac3" to "The device must be able to decode E-AC-3",
        "dts" to "The device must be able to decode DTS",
    )

    /** Adaptive playlists, which the app plays because the modules are on the classpath. */
    private val PLAYLISTS = setOf("m3u8", "m3u", "mpd")

    fun forExtension(extension: String): FormatCapability {
        val ext = extension.lowercase().trim()
        val kind = FileTypeRegistry.kindFor(null, "file.$ext")

        // No parser at all: the answer does not depend on anything else.
        NO_PARSER[ext]?.let { reason ->
            return FormatCapability(ext, kind, Capability.NEEDS_ANOTHER_APP, reason)
        }

        // Playlists are checked before the kind, because the registry does not classify them as
        // media at all — they are a text list of addresses. The app plays them because the HLS
        // and DASH modules are on the classpath, and that is what this says.
        if (ext in PLAYLISTS) {
            return FormatCapability(ext, kind, Capability.OPENS_HERE, null)
        }

        return when (kind) {
            MediaType.IMAGE -> image(ext, kind)
            MediaType.VIDEO -> video(ext, kind)
            MediaType.AUDIO -> audio(ext, kind)
            MediaType.DOCUMENT -> document(ext, kind)
            MediaType.ARCHIVE -> FormatCapability(
                ext,
                kind,
                Capability.NEEDS_ANOTHER_APP,
                "The app can list archives but not open them — extract them with another app",
            )
            MediaType.OTHER, MediaType.ALL -> FormatCapability(
                ext,
                kind,
                Capability.NEEDS_ANOTHER_APP,
                "Not a format the app opens",
            )
        }
    }

    private fun image(ext: String, kind: MediaType): FormatCapability = when {
        ImageDecodability.canRender(ext) -> FormatCapability(ext, kind, Capability.OPENS_HERE, null)
        else -> FormatCapability(
            ext,
            kind,
            Capability.NEEDS_ANOTHER_APP,
            // The decoder's own words, so the screen and the tile cannot disagree.
            ImageDecodability.reason(ext) ?: "No decoder for this format",
        )
    }

    private fun video(ext: String, kind: MediaType): FormatCapability = when {
        CAVEATS.containsKey(ext) -> FormatCapability(ext, kind, Capability.OPENS_WITH_CAVEAT, CAVEATS[ext])
        // Everything else is sniffed by Media3, which reads the container rather than the name.
        else -> FormatCapability(ext, kind, Capability.OPENS_HERE, null)
    }

    private fun audio(ext: String, kind: MediaType): FormatCapability =
        FormatCapability(ext, kind, Capability.OPENS_HERE, null)

    private fun document(ext: String, kind: MediaType): FormatCapability = when {
        ext == "pdf" -> FormatCapability(ext, kind, Capability.OPENS_HERE, null)
        TextFormats.canRead(ext) -> FormatCapability(ext, kind, Capability.OPENS_HERE, null)
        Stickers.isSticker(ext, null) -> FormatCapability(ext, kind, Capability.OPENS_HERE, null)
        else -> FormatCapability(
            ext,
            kind,
            Capability.NEEDS_ANOTHER_APP,
            "The app has no reader for this document — open it with another app",
        )
    }

    /**
     * Every format the app knows, with its answer. Sorted by extension.
     *
     * The registry's extensions plus the three groups this file adds: the adaptive playlists,
     * which the registry does not classify as media at all, and the no-parser and caveat lists,
     * which name formats the registry may not carry. Without them the screen would omit formats
     * the app has a definite answer about — and omitting `.m3u8` would have hidden something the
     * app plays.
     */
    fun all(): List<FormatCapability> =
        (FileTypeRegistry.knownExtensions() + PLAYLISTS + NO_PARSER.keys + CAVEATS.keys)
            .sorted()
            .map(::forExtension)

    fun byVerdict(): Map<Capability, List<FormatCapability>> =
        all().groupBy { it.verdict }
}
