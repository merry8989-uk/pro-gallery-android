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
import com.progallery.app.data.settings.AppSettings
import com.progallery.app.data.settings.ExportDuplicatePolicy
import com.progallery.app.data.settings.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

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
    private val settingsRepository: SettingsRepository,
) {

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }

    /**
     * Serialises the "choose a name and rename" step.
     *
     * With copies running in parallel, two of them could otherwise pick the same free
     * name and one would clobber the other.
     */
    private val namingLock = Mutex()

    suspend fun export(
        result: RecoveryResult,
        destinationTree: Uri,
    ): RecoveryExportOutcome = exportWith(settingsRepository.current(), result, destinationTree)

    /**
     * Exports several results, up to the configured number at a time.
     *
     * Bounded so a large selection does not open hundreds of streams at once; the
     * default of one keeps the original one-at-a-time behaviour.
     */
    suspend fun exportAll(
        results: List<RecoveryResult>,
        destinationTree: Uri,
    ): List<RecoveryExportOutcome> {
        val settings = settingsRepository.current()
        if (!settings.exportEnabled) {
            val refused = RecoveryExportOutcome.Failed("Export is switched off in Settings.")
            return results.map { refused }
        }

        val permits = Semaphore(settings.exportMaxConcurrentJobs.coerceIn(1, 8))
        return coroutineScope {
            results
                .map { result ->
                    async { permits.withPermit { exportWith(settings, result, destinationTree) } }
                }
                .awaitAll()
        }
    }

    private suspend fun exportWith(
        settings: AppSettings,
        result: RecoveryResult,
        destinationTree: Uri,
    ): RecoveryExportOutcome = withContext(Dispatchers.IO) {
        if (!settings.exportEnabled) {
            return@withContext RecoveryExportOutcome.Failed("Export is switched off in Settings.")
        }
        if (!result.canExport) {
            return@withContext RecoveryExportOutcome.Failed(
                "This file could not be read, so there is nothing to copy.",
            )
        }

        val tree = DocumentFile.fromTreeUri(context, destinationTree)
            ?: return@withContext RecoveryExportOutcome.Failed("The chosen folder could not be opened.")

        val outcome = copyInto(tree, result, settings)
        // Only a human-readable label is stored — never the tree URI, which carries
        // a permission grant that must not be written to the database.
        repository.record(result, tree.name ?: "chosen folder", outcome)
        outcome
    }

    private fun copyInto(
        tree: DocumentFile,
        result: RecoveryResult,
        settings: AppSettings,
    ): RecoveryExportOutcome {
        val wanted = result.item.displayName.ifBlank { "recovered_${System.currentTimeMillis()}" }
        // A thumbnail is a real file, so it is exported — but under a name that
        // says so, because it is not the original picture.
        val exportName = if (result.isThumbnail) markAsThumbnail(wanted) else wanted
        val mime = result.item.mimeType.ifBlank { "*/*" }

        // Skipping is checked before anything is created, so a skip leaves no trace.
        if (settings.exportDuplicatePolicy == ExportDuplicatePolicy.SKIP &&
            tree.findFile(exportName) != null
        ) {
            return RecoveryExportOutcome.Skipped(
                exportName,
                "A file of that name is already in the folder, and the setting is to skip.",
            )
        }

        // Unique, so parallel copies never share a partial file.
        val temp = tree.createFile(mime, "$exportName.partial-${UUID.randomUUID().toString().take(8)}")
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
            if (settings.exportVerifyAfterCopy && expected > 0L && onDisk != expected) {
                error("the copy is $onDisk bytes but the source is $expected — it was not written completely")
            }

            val finalName = namingLock.withLock { uniqueName(tree, exportName) }
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

    /** "photo.jpg" -> "photo-thumbnail.jpg", so the file never masquerades as the original. */
    private fun markAsThumbnail(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) {
            name.substring(0, dot) + "-thumbnail" + name.substring(dot)
        } else {
            "$name-thumbnail"
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
