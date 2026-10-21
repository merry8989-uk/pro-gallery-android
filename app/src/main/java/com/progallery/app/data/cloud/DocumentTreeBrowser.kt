package com.progallery.app.data.cloud

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Walks a cloud provider's folders using the platform's document API.
 *
 * Every provider is spoken to the same way, which is what makes one browser work for Drive,
 * TeraBox, WPS Office and a NAS alike. The app never talks to a provider's own servers: the
 * provider app does, on its own session, and hands back documents.
 *
 * **Access.** A provider may let an app read a folder straight away, or it may refuse until
 * the user grants that folder. Both are handled: a refusal is reported as
 * [GrantRequired] rather than as a generic failure, so the screen can offer the one-time
 * grant instead of an error. Once granted it is persisted, which is why a provider is
 * picked once and browsed afterwards without any further sign-in.
 */
@Singleton
class DocumentTreeBrowser @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** The provider will not read this folder until the user grants it. */
    class GrantRequired(val treeUri: String) :
        Exception("Access to this provider's folder has not been granted yet.")

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }

    /** The address of one document inside a granted tree. */
    fun documentUri(treeUri: String, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId)

    /**
     * Lists a folder. Passing no [parentDocumentId] lists the root the grant was given for.
     */
    suspend fun list(treeUri: String, parentDocumentId: String? = null): Result<List<CloudDocument>> =
        withContext(Dispatchers.IO) {
            val tree = runCatching { Uri.parse(treeUri) }.getOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("That folder address could not be read."))

            val parent = parentDocumentId
                ?: runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("That folder has no identifier."))

            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)

            try {
                val documents = ArrayList<CloudDocument>()
                context.contentResolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = cursor.toCloudDocument() ?: continue
                        documents += row.copy(uri = documentUri(treeUri, row.documentId).toString())
                    }
                }
                Result.success(CloudOrdering.sort(documents))
            } catch (t: SecurityException) {
                // Not a failure: it is the provider asking to be granted this folder once.
                Result.failure(GrantRequired(treeUri))
            } catch (t: Exception) {
                Result.failure(IllegalArgumentException("That folder could not be listed: ${t.message}", t))
            }
        }

    /**
     * The display name of a granted folder.
     *
     * The system picker returns only an address, so the name is asked of the provider — which
     * is the only thing that knows what the folder is called.
     */
    suspend fun displayName(treeUri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val tree = Uri.parse(treeUri)
            val root = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            context.contentResolver.query(
                root,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    /** Opens a document for reading, so it can be played or shown in-app. */
    suspend fun openStream(documentUri: String): InputStream? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(Uri.parse(documentUri)) }.getOrNull()
    }

    /** True when this app already holds a persisted grant for [treeUri]. */
    fun hasPersistedGrant(treeUri: String): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any {
            it.isReadPermission && it.uri.toString() == treeUri
        }
    }.getOrDefault(false)
}
