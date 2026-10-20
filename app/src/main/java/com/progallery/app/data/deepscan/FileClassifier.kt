package com.progallery.app.data.deepscan

import android.content.Context
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.recovery.FileSignatureDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of identifying one file. */
data class ClassifiedFile(
    val kind: MediaType,
    val mimeType: String,
    /** True when the bytes (not the extension) decided the type. */
    val identifiedBySignature: Boolean,
)

/**
 * Single source of truth for "what is this file?".
 *
 * MIME first, then the extension, then — when the extension is missing or the
 * type is unknown — the leading bytes via [FileSignatureDetector]. Shared by the
 * SAF deep scan and the `Android/` folder scan so both classify identically.
 */
@Singleton
class FileClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun classify(
        name: String,
        declaredMime: String?,
        readSignature: Boolean,
        maxSignatureBytes: Int,
        openStream: () -> InputStream? = {
            // Default: resolve the file as a content URI is not possible here, so
            // callers that have a URI pass their own opener.
            null
        },
    ): ClassifiedFile {
        val mime = declaredMime ?: ""
        val extension = FileTypeRegistry.extensionOf(name)
        var kind = FileTypeRegistry.kindFor(mime, name)
        var resolvedMime = mime

        val worthSniffing = kind == MediaType.OTHER || extension.isEmpty() || mime.isEmpty()
        if (readSignature && worthSniffing) {
            val sniffed = FileSignatureDetector.sniff(openStream, maxSignatureBytes)
            if (sniffed != null) {
                kind = sniffed.kind
                resolvedMime = sniffed.mimeType
                return ClassifiedFile(kind, resolvedMime, identifiedBySignature = true)
            }
        }
        return ClassifiedFile(kind, resolvedMime, identifiedBySignature = false)
    }
}
