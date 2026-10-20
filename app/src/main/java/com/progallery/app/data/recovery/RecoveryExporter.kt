package com.progallery.app.data.recovery

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.progallery.app.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The safe export workflow.
 *
 * Rules it follows, because a recovery tool that loses data is worse than none:
 *
 *  - the user chooses the destination through the Storage Access Framework,
 *  - the copy is streamed, never held in memory,
 *  - it is written under a temporary name, **verified against the source size**,
 *    and only then given its real name,
 *  - an existing file is never overwritten — a numbered name is used instead,
 *  - a failed or cancelled copy deletes the partial file,
 *  - the source is never modified or deleted.
 *
 * Every attempt is recorded in the recovery history (name, source, destination,
 * outcome, timestamp).
 */
@Singleton
class RecoveryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecoveryRepository,
) {

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }

    suspend fun export(
        result: RecoveryResult,
        destinationTree: Uri,
    ): RecoveryExportOutcome = withContext(Dispatchers.IO) {
        if (result.status == RecoveryStatus.PREVIEW_ONLY) {
            val what = if (result.isThumbnail) "a thumbnail" else "a preview"
            return@withContext RecoveryExportOutcome.Failed(
                "This result is $what, not the original file, so there is nothing to export.",
            )
        }

        val tree = DocumentFile.fromTreeUri(context, destinationTree)
            ?: return@withContext RecoveryExportOutcome.Failed("The chosen folder could not be opened.")

        val outcome = copyInto(tree, result)
        // Only a human-readable label is stored — never the tree URI, which carries
        // a permission grant that must not be written to the database.
        repository.record(result, tree.name ?: "chosen folder", outcome)
        outcome
    }

    /** Exports several results one after another, so the provider is not flooded. */
    suspend fun exportAll(
        results: List<RecoveryResult>,
        destinationTree: Uri,
    ): List<RecoveryExportOutcome> = results.map { export(it, destinationTree) }

    private fun copyInto(tree: DocumentFile, result: RecoveryResult): RecoveryExportOutcome {
        val wanted = result.item.displayName.ifBlank { "recovered_${System.currentTimeMillis()}" }
        val mime = result.item.mimeType.ifBlank { "*/*" }

        val temp = tree.createFile(mime, "$wanted.partial")
            ?: return RecoveryExportOutcome.Failed("A file could not be created in the chosen folder.")

        return try {
            val source = context.contentResolver.openInputStream(result.item.uri)
                ?: return RecoveryExportOutcome.Failed("The file could not be read.")

            val written = source.use { input ->
                val out = context.contentResolver.openOutputStream(temp.uri)
                    ?: error("the destination could not be opened")
                out.use { destination -> input.copyTo(destination, BUFFER_BYTES) }
            }
            if (written <= 0L) error("the copy came out empty")

            // Verify before reporting success.
            val onDisk = temp.length()
            if (onDisk <= 0L) error("the copy came out empty")
            val expected = result.item.sizeBytes
            if (expected > 0L && onDisk != expected) {
                error("the copy is $onDisk bytes but the source is $expected — it was not written completely")
            }

            val finalName = uniqueName(tree, wanted)
            if (!temp.renameTo(finalName)) {
                error("the copy was written but could not be given the name \"$finalName\"")
            }
            RecoveryExportOutcome.Success(finalName, onDisk)
        } catch (t: Throwable) {
            // Never leave a partial file behind.
            runCatchingCancellable { temp.delete() }
            if (t is CancellationException) throw t
            RecoveryExportOutcome.Failed(t.message?.takeIf { it.isNotBlank() } ?: "The export failed.")
        }
    }

    /** A name that collides with nothing already in the folder. */
    private fun uniqueName(tree: DocumentFile, base: String): String {
        if (tree.findFile(base) == null) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val extension = if (dot > 0) base.substring(dot) else ""
        for (index in 1..999) {
            val candidate = "$stem ($index)$extension"
            if (tree.findFile(candidate) == null) return candidate
        }
        return "$stem-${System.currentTimeMillis()}$extension"
    }
}
