package com.progallery.app.data.model

import android.os.Build

/** Whether a preview can actually be produced on this device. */
enum class DecoderSupport {
    /** The platform can decode it on any supported version. */
    ALWAYS,

    /** The platform can decode it, but only from the given API level. */
    FROM_API,

    /**
     * The platform has no decoder, but the app ships one.
     *
     * A separate answer from [ALWAYS] on purpose: it says the preview depends on a library this
     * app carries, not on the operating system — which is the difference between a format that
     * works on every device and one that works because of a decision made here.
     */
    BY_APP,

    /** No decoder exists on Android for this format. */
    NEVER,
}

/**
 * Which image formats the app can really render.
 *
 * This exists because the format registry used to say "image" for anything with an image
 * extension — including formats Android has no decoder for at all. The file was listed,
 * a preview was promised, and the platform failed silently, leaving a blank tile. Saying
 * "image" is a classification; being able to *show* it is a separate question, and this
 * answers the second one honestly.
 *
 * Pure, so every claim is unit tested.
 */
object ImageDecodability {

    private val ALWAYS = setOf(
        "jpg", "jpeg", "jpe", "jfif", "pjpeg",
        "png", "apng",
        "gif",
        "webp",
        "bmp", "dib",
        "wbmp",
    )

    /** Decodable, but only from a given API level. */
    private val GATED = mapOf(
        "heic" to Build.VERSION_CODES.P,
        "heif" to Build.VERSION_CODES.P,
        "avif" to Build.VERSION_CODES.S,
    )

    /**
     * Formats the app renders itself, because the platform cannot.
     *
     * SVG is a vector format: a bitmap decoder cannot draw it, and Android ships none. The app
     * carries one, so these files show a picture — and the reason line says nothing, because
     * there is nothing to warn about.
     */
    private val BY_APP = setOf("svg", "svgz")

    /** Image formats Android has no decoder for. Listed, never promised. */
    private val NEVER = setOf(
        // vector and page description — a bitmap decoder cannot render these, and none is
        // carried for them
        "eps", "epsf", "ai", "indd", "indt", "sketch", "cdr", "cdt",
        // layered and professional
        "psd", "psb", "xcf",
        // other image codecs with no platform decoder
        "tif", "tiff", "tga", "pcx", "ppm", "pgm", "pbm", "pnm", "pam",
        "jxl", "jp2", "j2k", "jpf", "jpx", "jpm",
        "ico", "cur",
    )

    /** True when the format is animated and needs a multi-frame decoder. */
    fun isAnimated(extension: String): Boolean = extension.lowercase() in setOf("gif", "webp")

    fun support(extension: String, sdkInt: Int = Build.VERSION.SDK_INT): DecoderSupport {
        val ext = extension.lowercase()
        return when {
            ext in ALWAYS -> DecoderSupport.ALWAYS
            GATED.containsKey(ext) -> DecoderSupport.FROM_API
            ext in BY_APP -> DecoderSupport.BY_APP
            ext in NEVER -> DecoderSupport.NEVER
            else -> DecoderSupport.NEVER
        }
    }

    /** Whether a preview can be produced for this extension on this device. */
    fun canRender(extension: String, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        when (support(extension, sdkInt)) {
            DecoderSupport.ALWAYS -> true
            DecoderSupport.BY_APP -> true
            DecoderSupport.FROM_API -> sdkInt >= (GATED[extension.lowercase()] ?: Int.MAX_VALUE)
            DecoderSupport.NEVER -> false
        }

    /**
     * The API level from which this format becomes decodable, or null when it never is.
     * The UI uses this to say *why* there is no preview.
     */
    fun requiredApi(extension: String): Int? = GATED[extension.lowercase()]

    /** The Android version name for an API level, for wording a message a person can act on. */
    fun androidName(apiLevel: Int): String = when (apiLevel) {
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        else -> "API $apiLevel"
    }

    /** A short line for the UI, so a missing preview is explained rather than blank. */
    fun reason(extension: String, sdkInt: Int = Build.VERSION.SDK_INT): String? {
        val ext = extension.lowercase()
        return when (support(ext, sdkInt)) {
            DecoderSupport.ALWAYS -> null
            // The app draws it itself, so there is nothing to warn about.
            DecoderSupport.BY_APP -> null
            DecoderSupport.FROM_API -> {
                val needed = GATED[ext] ?: return null
                if (sdkInt >= needed) null else "Needs Android ${androidName(needed)} or newer to preview"
            }
            DecoderSupport.NEVER -> "Android has no decoder for .$ext files"
        }
    }
}
