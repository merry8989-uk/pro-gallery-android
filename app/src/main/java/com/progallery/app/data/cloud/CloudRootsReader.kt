package com.progallery.app.data.cloud

import android.content.Context
import android.database.Cursor
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists the cloud providers installed on the device.
 *
 * This is the mechanism that makes in-app cloud browsing possible **without the app holding
 * anyone's account**: every provider — Drive, TeraBox, WPS Office, Zoho WorkDrive, a NAS —
 * publishes its roots through the platform's document API, and the provider app is the one
 * that holds the sign-in. The app never sees a password and never has to log in, which is
 * also why there is nothing to log into again later.
 */
@Singleton
class CloudRootsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun roots(): List<CloudRoot> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.acquireRoots()
                .mapNotNull { root ->
                    val uri = root.uri ?: return@mapNotNull null
                    CloudRoot(
                        rootId = root.rootId ?: uri.toString(),
                        title = root.title?.toString().orEmpty().ifBlank { uri.authority.orEmpty() },
                        summary = root.summary?.toString(),
                        treeUri = uri.toString(),
                        authority = uri.authority.orEmpty(),
                        documentId = DocumentsContract.getTreeDocumentId(uri),
                        canCreate = (root.flags and DocumentsContract.Root.FLAG_SUPPORTS_CREATE) != 0,
                    )
                }
                .distinctBy { it.treeUri }
        }.getOrDefault(emptyList())
    }
}

/** Reads one row of a document query into the app's own shape. */
internal fun Cursor.toCloudDocument(): CloudDocument? {
    val id = getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID).let { if (it < 0) return null else it }
    val documentId = getString(id) ?: return null
    val name = getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        .takeIf { it >= 0 }?.let { getString(it) }.orEmpty().ifBlank { documentId.substringAfterLast('/') }
    val mime = getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        .takeIf { it >= 0 }?.let { getString(it) }.orEmpty()
    val size = getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        .takeIf { it >= 0 }?.let { if (isNull(it)) 0L else getLong(it) } ?: 0L
    val modified = getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        .takeIf { it >= 0 }?.let { if (isNull(it)) null else getLong(it) }

    return CloudDocument(
        uri = "",
        documentId = documentId,
        name = name,
        mimeType = mime,
        sizeBytes = size,
        lastModifiedMillis = modified,
        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
    )
}
