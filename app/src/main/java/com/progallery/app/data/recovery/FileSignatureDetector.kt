package com.progallery.app.data.recovery

import com.progallery.app.data.model.MediaType
import java.io.InputStream
import kotlinx.coroutines.CancellationException

/** How sure the detector is about an identification. */
enum class Confidence { HIGH, MEDIUM, LOW }

/** The outcome of sniffing a file's leading bytes. */
data class SignatureMatch(
    val kind: MediaType,
    val mimeType: String,
    val formatName: String,
    val confidence: Confidence,
    val evidence: String,
)

/**
 * Identifies a file from its leading bytes rather than trusting its extension —
 * the extension is often missing or simply wrong.
 *
 * [detect] is pure so it can be unit tested; [sniff] reads only the minimum
 * number of bytes needed, never the whole file.
 */
object FileSignatureDetector {

    /** Default number of leading bytes read for identification. */
    const val DEFAULT_HEADER_BYTES = 4096

    fun sniff(openStream: () -> InputStream?, maxBytes: Int = DEFAULT_HEADER_BYTES): SignatureMatch? {
        val stream = openStream() ?: return null
        return try {
            stream.use { detect(it.readNBytes(maxBytes)) }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    /** Identifies a file from its header bytes. Returns null when nothing matches. */
    fun detect(header: ByteArray): SignatureMatch? {
        if (header.size < 4) return null

        fun startsWith(vararg bytes: Int, offset: Int = 0): Boolean {
            if (offset + bytes.size > header.size) return false
            return bytes.withIndex().all { (i, b) -> header[offset + i] == b.toByte() }
        }

        fun asciiAt(offset: Int, text: String): Boolean {
            if (offset + text.length > header.size) return false
            return text.withIndex().all { (i, c) -> header[offset + i] == c.code.toByte() }
        }

        fun u8(index: Int): Int = header[index].toInt() and 0xFF

        // ---- exact magic numbers (high confidence) ------------------------
        if (startsWith(0xFF, 0xD8, 0xFF)) return match(MediaType.IMAGE, "image/jpeg", "JPEG", Confidence.HIGH, "FF D8 FF")
        if (startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return match(MediaType.IMAGE, "image/png", "PNG", Confidence.HIGH, "89 50 4E 47")
        }
        if (asciiAt(0, "GIF87a") || asciiAt(0, "GIF89a")) {
            return match(MediaType.IMAGE, "image/gif", "GIF", Confidence.HIGH, "GIF8")
        }
        if (asciiAt(0, "BM")) return match(MediaType.IMAGE, "image/bmp", "BMP", Confidence.MEDIUM, "BM")
        if (asciiAt(0, "II") && u8(2) == 0x2A && u8(3) == 0x00) {
            return match(MediaType.IMAGE, "image/tiff", "TIFF (little endian)", Confidence.HIGH, "II 2A")
        }
        if (asciiAt(0, "MM") && u8(2) == 0x00 && u8(3) == 0x2A) {
            return match(MediaType.IMAGE, "image/tiff", "TIFF (big endian)", Confidence.HIGH, "MM 00 2A")
        }
        if (asciiAt(0, "8BPS")) return match(MediaType.IMAGE, "image/vnd.adobe.photoshop", "Photoshop", Confidence.HIGH, "8BPS")
        if (asciiAt(0, "%PDF")) return match(MediaType.DOCUMENT, "application/pdf", "PDF", Confidence.HIGH, "%PDF")
        if (asciiAt(0, "%!PS")) return match(MediaType.DOCUMENT, "application/postscript", "PostScript/EPS", Confidence.HIGH, "%!PS")
        if (asciiAt(0, "fLaC")) return match(MediaType.AUDIO, "audio/flac", "FLAC", Confidence.HIGH, "fLaC")
        if (asciiAt(0, "OggS")) return match(MediaType.AUDIO, "audio/ogg", "Ogg", Confidence.HIGH, "OggS")
        if (asciiAt(0, "ID3")) return match(MediaType.AUDIO, "audio/mpeg", "MP3 (ID3)", Confidence.HIGH, "ID3")
        if (asciiAt(0, "MThd")) return match(MediaType.AUDIO, "audio/midi", "MIDI", Confidence.HIGH, "MThd")
        if (startsWith(0x1A, 0x45, 0xDF, 0xA3)) {
            return match(MediaType.VIDEO, "video/x-matroska", "Matroska/WebM", Confidence.HIGH, "1A 45 DF A3")
        }
        if (startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) {
            return match(MediaType.ARCHIVE, "application/x-7z-compressed", "7-Zip", Confidence.HIGH, "37 7A BC AF")
        }
        if (startsWith(0x1F, 0x8B)) return match(MediaType.ARCHIVE, "application/gzip", "GZIP", Confidence.HIGH, "1F 8B")
        if (asciiAt(0, "BZh")) return match(MediaType.ARCHIVE, "application/x-bzip2", "BZIP2", Confidence.HIGH, "BZh")
        if (startsWith(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)) {
            return match(MediaType.ARCHIVE, "application/x-xz", "XZ", Confidence.HIGH, "FD 37 7A 58 5A")
        }
        if (asciiAt(0, "Rar!") && u8(4) == 0x1A && u8(5) == 0x07) {
            return match(MediaType.ARCHIVE, "application/vnd.rar", "RAR", Confidence.HIGH, "Rar! 1A 07")
        }
        if (startsWith(0x00, 0x00, 0x01, 0x00)) {
            return match(MediaType.IMAGE, "image/x-icon", "ICO", Confidence.MEDIUM, "00 00 01 00")
        }
        if (startsWith(0x50, 0x4B, 0x03, 0x04) ||
            startsWith(0x50, 0x4B, 0x05, 0x06) ||
            startsWith(0x50, 0x4B, 0x07, 0x08)
        ) {
            return match(MediaType.ARCHIVE, "application/zip", "ZIP container", Confidence.HIGH, "PK")
        }

        // ---- RIFF containers (brand decides the kind) ---------------------
        if (asciiAt(0, "RIFF") && header.size >= 12) {
            return when {
                asciiAt(8, "WAVE") -> match(MediaType.AUDIO, "audio/wav", "WAV", Confidence.HIGH, "RIFF/WAVE")
                asciiAt(8, "AVI ") -> match(MediaType.VIDEO, "video/x-msvideo", "AVI", Confidence.HIGH, "RIFF/AVI")
                asciiAt(8, "WEBP") -> match(MediaType.IMAGE, "image/webp", "WebP", Confidence.HIGH, "RIFF/WEBP")
                else -> match(MediaType.OTHER, "application/riff", "RIFF", Confidence.LOW, "RIFF")
            }
        }

        // ---- ISO base media (MP4 family) ----------------------------------
        if (header.size >= 12 && asciiAt(4, "ftyp")) {
            val brand = String(header, 8, 4, Charsets.US_ASCII).trim().lowercase()
            return when (brand) {
                "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1" ->
                    match(MediaType.IMAGE, "image/heic", "HEIC/HEIF", Confidence.MEDIUM, "ftyp/$brand")
                "avif", "avis" -> match(MediaType.IMAGE, "image/avif", "AVIF", Confidence.MEDIUM, "ftyp/$brand")
                "m4a", "m4b" -> match(MediaType.AUDIO, "audio/mp4", "M4A", Confidence.MEDIUM, "ftyp/$brand")
                "qt" -> match(MediaType.VIDEO, "video/quicktime", "QuickTime/MOV", Confidence.MEDIUM, "ftyp/qt")
                "3gp4", "3gp5", "3g2a", "3g2b" ->
                    match(MediaType.VIDEO, "video/3gpp", "3GPP", Confidence.MEDIUM, "ftyp/$brand")
                "isom", "iso2", "mp41", "mp42", "avc1", "dash", "m4v", "mmp4" ->
                    match(MediaType.VIDEO, "video/mp4", "MP4", Confidence.MEDIUM, "ftyp/$brand")
                else -> match(MediaType.VIDEO, "video/mp4", "ISO base media", Confidence.LOW, "ftyp/$brand")
            }
        }

        // ---- MP3 without an ID3 tag --------------------------------------
        if (u8(0) == 0xFF && (u8(1) and 0xE0) == 0xE0) {
            return match(MediaType.AUDIO, "audio/mpeg", "MP3", Confidence.MEDIUM, "FF Ex frame sync")
        }

        // ---- text-ish heuristics (low confidence, clearly labelled) -------
        val head = String(header, 0, minOf(header.size, 256), Charsets.UTF_8).trimStart()
        if (head.startsWith("<svg", ignoreCase = true)) {
            return match(MediaType.IMAGE, "image/svg+xml", "SVG", Confidence.MEDIUM, "<svg")
        }
        if (head.startsWith("<?xml")) {
            return match(MediaType.DOCUMENT, "application/xml", "XML", Confidence.LOW, "<?xml")
        }
        if (head.startsWith("<!DOCTYPE html", true) || head.startsWith("<html", true)) {
            return match(MediaType.DOCUMENT, "text/html", "HTML", Confidence.MEDIUM, "<html")
        }
        if (head.startsWith("{") || head.startsWith("[")) {
            return match(MediaType.DOCUMENT, "application/json", "JSON (text)", Confidence.LOW, "braces")
        }

        return null
    }

    private fun match(
        kind: MediaType,
        mimeType: String,
        formatName: String,
        confidence: Confidence,
        evidence: String,
    ) = SignatureMatch(kind, mimeType, formatName, confidence, evidence)
}
