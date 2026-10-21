package com.progallery.app.data.cloud

/** What a cloud document is, so tapping it opens the right in-app viewer. */
enum class CloudKind { IMAGE, VIDEO, AUDIO, FOLDER, OTHER }

/**
 * One entry inside a cloud provider's folder.
 *
 * This is the shape every provider is reduced to — Drive, TeraBox, WPS Office, Zoho
 * WorkDrive, a NAS — because the platform's document API is the same for all of them. That
 * is the whole reason the app can browse them **without implementing any provider's own
 * sign-in**: the provider app already holds the account and the session, and exposes it
 * through this common interface.
 */
data class CloudDocument(
    val uri: String,
    val documentId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long?,
    val isDirectory: Boolean,
) {
    val kind: CloudKind
        get() = CloudKinds.of(mimeType, name, isDirectory)
}

object CloudKinds {

    /**
     * Decides the kind from the MIME type the provider reported, falling back to the name.
     *
     * Providers are inconsistent: some report `application/octet-stream` for everything, so
     * the extension is consulted when the MIME type says nothing useful. Pure, so the
     * decision is unit tested rather than discovered by tapping files.
     */
    fun of(mimeType: String, name: String, isDirectory: Boolean): CloudKind {
        if (isDirectory) return CloudKind.FOLDER
        val mime = mimeType.trim().lowercase().substringBefore(';').trim()
        when {
            mime.startsWith("image/") -> return CloudKind.IMAGE
            mime.startsWith("video/") -> return CloudKind.VIDEO
            mime.startsWith("audio/") -> return CloudKind.AUDIO
        }
        return fromExtension(name)
    }

    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts")
    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus")

    private fun fromExtension(name: String): CloudKind {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return CloudKind.OTHER
        return when (val ext = name.substring(dot + 1).lowercase()) {
            in IMAGE -> CloudKind.IMAGE
            in VIDEO -> CloudKind.VIDEO
            in AUDIO -> CloudKind.AUDIO
            else -> CloudKind.OTHER
        }
    }
}

/**
 * How a folder listing is shown: folders first, then names, read the way a file browser is
 * read. Pure, so the order is tested rather than assumed.
 */
object CloudOrdering {
    fun sort(documents: List<CloudDocument>): List<CloudDocument> =
        documents.sortedWith(
            compareByDescending<CloudDocument> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
}
