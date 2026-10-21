package com.progallery.app.data.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Text that has been read, with what was assumed to read it. */
data class DecodedText(
    val text: String,
    /** The encoding used, so the app can say when it had to guess. */
    val encoding: String,
    /** True when the encoding was a fallback rather than something the file declared. */
    val guessed: Boolean,
    /** True when the file was longer than the reader will hold. */
    val truncated: Boolean,
)

/**
 * Turns bytes into text.
 *
 * Pure, so every rule about what the app will and will not show is unit tested.
 *
 * Three decisions, all deliberate:
 *
 *  * **A byte-order mark is believed.** If the file says what it is, that is used.
 *  * **UTF-8 first, then a fallback that is admitted to.** A file that is not valid UTF-8 is
 *    decoded as ISO-8859-1 — which cannot fail — but the result is flagged as guessed, so the
 *    screen can say the accents may be wrong rather than pretending it read it perfectly.
 *  * **Binary is refused, not shown as noise.** A `.docx` is a zip archive; rendering its
 *    bytes would be worse than saying the app cannot read it.
 */
object TextDecoder {

    /** How much of a file the reader will hold. Enough for any log or data file. */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** How much of the start is examined when deciding whether something is binary. */
    private const val SNIFF_BYTES = 1_024

    /**
     * True when the content does not look like text.
     *
     * A NUL byte is the giveaway: no text encoding in use today contains one.
     */
    fun looksBinary(bytes: ByteArray, limit: Int = SNIFF_BYTES): Boolean {
        val end = minOf(bytes.size, limit)
        for (index in 0 until end) {
            if (bytes[index] == 0.toByte()) return true
        }
        return false
    }

    fun decode(bytes: ByteArray, limit: Int = MAX_BYTES): Result<DecodedText> {
        if (bytes.isEmpty()) {
            return Result.success(DecodedText("", "empty", guessed = false, truncated = false))
        }
        if (looksBinary(bytes)) {
            return Result.failure(
                IllegalArgumentException("This file is not text — it is a binary file with a text extension."),
            )
        }

        val truncated = bytes.size > limit
        val slice = if (truncated) bytes.copyOf(limit) else bytes

        // A byte-order mark is the file telling us what it is.
        when {
            slice.startsWith(0xEF, 0xBB, 0xBF) -> return Result.success(
                DecodedText(
                    String(slice, 3, slice.size - 3, StandardCharsets.UTF_8),
                    "UTF-8",
                    guessed = false,
                    truncated = truncated,
                ),
            )
            slice.startsWith(0xFF, 0xFE) -> return Result.success(
                DecodedText(
                    String(slice, 2, slice.size - 2, StandardCharsets.UTF_16LE),
                    "UTF-16 LE",
                    guessed = false,
                    truncated = truncated,
                ),
            )
            slice.startsWith(0xFE, 0xFF) -> return Result.success(
                DecodedText(
                    String(slice, 2, slice.size - 2, StandardCharsets.UTF_16BE),
                    "UTF-16 BE",
                    guessed = false,
                    truncated = truncated,
                ),
            )
        }

        // No mark, so UTF-8 is tried properly — strictly, not leniently, or invalid bytes would
        // be silently replaced and the file would look fine while being wrong.
        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return Result.success(
                DecodedText(decoder.decode(ByteBuffer.wrap(slice)).toString(), "UTF-8", guessed = false, truncated = truncated),
            )
        } catch (_: CharacterCodingException) {
            // Fall through to the admitted guess.
        }

        return Result.success(
            DecodedText(
                String(slice, StandardCharsets.ISO_8859_1),
                "ISO-8859-1",
                guessed = true,
                truncated = truncated,
            ),
        )
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }
    }
}
