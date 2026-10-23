package com.progallery.app.data.detection

/**
 * Something provably wrong with a file.
 *
 * Every value here is a claim the app can **back with evidence**, which is the whole point. A
 * "corrupt" label that cannot say why is worse than no label: it makes a person distrust a file
 * that may be perfectly fine.
 */
enum class IntegrityIssue(val label: String) {
    /** No bytes at all. Provable, and the commonest real case. */
    EMPTY("The file is empty"),

    /** The bytes stop before the format's own end marker. */
    TRUNCATED("The file ends before its own end marker"),

    /** The leading bytes match no format this app knows. */
    HEADER_UNRECOGNISED("The first bytes match no known format"),

    /** The bytes say one format and the name says another. */
    EXTENSION_MISMATCH("The name does not match the contents"),
}

/** What was found, and what proved it. */
data class IntegrityReport(
    val issues: List<IntegrityIssue> = emptyList(),
    /** The format the bytes indicate, when they indicate one. */
    val actualFormat: String? = null,
    /** One line a person can read, naming the evidence rather than the verdict. */
    val evidence: String = "",
) {
    val suspicious: Boolean get() = issues.isNotEmpty()

    /** True when the file cannot be read at all, rather than merely being odd. */
    val unreadable: Boolean get() = issues.any { it == IntegrityIssue.EMPTY || it == IntegrityIssue.TRUNCATED }
}

/**
 * Decides whether a file is what it says it is, and whether all of it is there.
 *
 * **Pure, and deliberately conservative.** It is given the leading bytes, the trailing bytes and
 * the declared size, and it flags only what those can prove:
 *
 * - **Empty** is a fact.
 * - **Truncated** is checked only for formats that carry their own end marker — JPEG ends `FFD9`,
 *   PNG ends with an `IEND` chunk. For anything else there is no reliable way to tell a complete
 *   file from a short one, so nothing is claimed. Guessing here would produce exactly the false
 *   accusation this is meant to avoid.
 * - **A mismatched extension** is a fact when the bytes identify a format and it differs from the
 *   name's.
 * - **An unrecognised header** is only reported for a file that has a **known media extension**:
 *   a `.txt` or a `.zip` legitimately starts with bytes this detector does not catalogue, and
 *   calling those "corrupt" would be wrong.
 */
object MediaIntegrity {

    /** JPEG: `FFD8` at the start, `FFD9` at the end. Both are part of the format. */
    private val JPEG_START = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val JPEG_END = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private val PNG_START = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val PNG_END_CHUNK = "IEND".toByteArray(Charsets.US_ASCII)

    /**
     * Everything the check needs, so the decision is one pure call rather than seven arguments.
     *
     * [extensionIsKnownMedia] is passed in rather than guessed from a list kept here: whether an
     * extension is one the app catalogues is `FileTypeRegistry`'s question, and a second list in
     * this file would be a second answer that could drift from the first. It replaces a list of
     * forty opaque extensions that was, on inspection, a list of everything somebody remembered.
     */
    data class Input(
        val extension: String,
        val declaredSize: Long,
        val header: ByteArray?,
        val trailer: ByteArray?,
        val signatureFormatName: String?,
        /** Null when the detector could not identify the bytes at all. */
        val signatureMatchesExtension: Boolean?,
        /** Whether the app knows this extension as a media or document kind. */
        val extensionIsKnownMedia: Boolean,
    )

    fun analyse(input: Input): IntegrityReport {
        val ext = input.extension.lowercase().trim().removePrefix(".")
        val declaredSize = input.declaredSize
        val header = input.header
        val trailer = input.trailer
        val signatureFormatName = input.signatureFormatName
        val issues = mutableListOf<IntegrityIssue>()
        val evidence = mutableListOf<String>()

        if (declaredSize == 0L) {
            issues += IntegrityIssue.EMPTY
            evidence += "reported as 0 bytes"
        }

        if (header == null || header.isEmpty()) {
            // Nothing to look at. If the size said there was something, that is worth saying;
            // if the size was zero, the EMPTY above already covers it.
            if (declaredSize > 0L) {
                issues += IntegrityIssue.HEADER_UNRECOGNISED
                evidence += "the first bytes could not be read"
            }
            return IntegrityReport(issues, signatureFormatName, evidence.joinToString("; "))
        }

        // The name against the contents. Only a mismatch, never an absence: a file whose extension
        // is unknown to the detector is not thereby wrong.
        if (input.signatureMatchesExtension == false && signatureFormatName != null) {
            issues += IntegrityIssue.EXTENSION_MISMATCH
            evidence += "the bytes are $signatureFormatName but the name says .$ext"
        }

        // A header that matches nothing, for a file whose extension is one this detector knows.
        if (signatureFormatName == null && input.extensionIsKnownMedia) {
            issues += IntegrityIssue.HEADER_UNRECOGNISED
            evidence += "the first bytes match no known format"
        }

        // Truncation, only where the format carries its own end marker.
        val truncated = when {
            ext == "jpg" || ext == "jpeg" ->
                header.startsWith(JPEG_START) && trailer != null && !trailer.endsWith(JPEG_END)
            ext == "png" ->
                header.startsWith(PNG_START) && trailer != null && !trailer.contains(PNG_END_CHUNK)
            else -> false
        }
        if (truncated) {
            issues += IntegrityIssue.TRUNCATED
            evidence += if (ext == "png") {
                "a PNG must end with an IEND chunk and this does not"
            } else {
                "a JPEG must end with FFD9 and this does not"
            }
        }

        return IntegrityReport(
            issues = issues.distinct(),
            actualFormat = signatureFormatName,
            evidence = evidence.joinToString("; "),
        )
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (size < suffix.size) return false
        val offset = size - suffix.size
        for (i in suffix.indices) if (this[offset + i] != suffix[i]) return false
        return true
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty() || size < needle.size) return false
        outer@ for (start in 0..(size - needle.size)) {
            for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
            return true
        }
        return false
    }
}
