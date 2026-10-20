package com.progallery.app.data.recovery

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.progallery.app.data.deepscan.FileClassifier
import com.progallery.app.data.deepscan.ScanProgressThrottle
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quick Scan: finds media this app can actually reach and says where each piece
 * came from.
 *
 * It deliberately does **not** call an indexed file a deleted file. Three kinds of
 * result are produced, each labelled:
 *
 *  1. items the system's index lists (source `MEDIA_STORE`),
 *  2. bytes this app is holding in its own Recently Deleted (source `TRASH`),
 *  3. files inside folders the user granted that the system's index does not
 *     list (source `SAF_FOLDER`).
 *
 * Nothing is invented, and nothing is claimed to be recoverable that is not.
 */
@Singleton
class RecoveryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val trashRepository: TrashRepository,
    private val classifier: FileClassifier,
    private val settingsRepository: SettingsRepository,
    private val scanDirectoryDao: ScanDirectoryDao,
    private val boundsReader: ImageBoundsReader,
) {

    fun quickScan(): Flow<RecoveryScanState> = scan(RecoveryScanMode.QUICK)

    fun scan(mode: RecoveryScanMode): Flow<RecoveryScanState> = flow {
        try {
            emitAll(scanInternal(mode))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            emit(
                RecoveryScanState.Failed(
                    t.message?.takeIf { it.isNotBlank() }
                        ?: "The scan stopped unexpectedly (${t::class.java.simpleName}).",
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun scanInternal(mode: RecoveryScanMode): Flow<RecoveryScanState> = flow {
        val settings = settingsRepository.current()
        if (!settings.anyCategoryEnabled) {
            emit(
                RecoveryScanState.Failed(
                    "Every file category is switched off in Settings, so there is nothing to look for.",
                ),
            )
            return@flow
        }

        val results = ArrayList<RecoveryResult>()
        val errors = ArrayList<String>()
        var skippedAlreadyIndexed = 0

        // The thumbnail/cache mode is about artefacts only, so it does not list
        // the index or the trash at all.
        val includeIndexAndTrash = mode == RecoveryScanMode.QUICK

        // ---- 1. the system's own index ------------------------------------
        emit(RecoveryScanState.Scanning("Reading the system index", 0))
        mediaRepository.refresh()
        val indexed = if (includeIndexAndTrash) {
            mediaRepository.currentSnapshot().filter { it.mediaType in settings.enabledKinds }
        } else {
            emptyList()
        }

        indexed.forEach { item ->
            results += RecoveryResult(
                id = "${RecoverySource.MEDIA_STORE.name}|${item.uri}",
                item = item,
                source = RecoverySource.MEDIA_STORE,
                confidence = RecoveryConfidence.HIGH,
                status = RecoveryStatus.AVAILABLE,
                evidence = "Listed by the system's media index.",
                originLabel = item.bucketName.ifBlank { "System index" },
            )
        }

        // ---- 2. our own Recently Deleted ----------------------------------
        emit(
            RecoveryScanState.Scanning(
                if (includeIndexAndTrash) "Reading Recently Deleted" else "Looking for thumbnails and cached previews",
                results.size,
            ),
        )
        val trashed = if (includeIndexAndTrash) trashRepository.list() else emptyList()
        trashed.forEach { entity ->
            val kind = entity.mediaType
                ?.let { name -> MediaType.entries.firstOrNull { it.name == name } }
                ?: if (entity.isVideo) {
                    MediaType.VIDEO
                } else {
                    MediaType.IMAGE
                }
            val item = MediaItem(
                id = entity.id,
                uri = Uri.parse(entity.trashUri),
                displayName = entity.displayName,
                mimeType = entity.mimeType,
                sizeBytes = entity.sizeBytes,
                dateModifiedSeconds = entity.deletedAt / 1000,
                width = 0,
                height = 0,
                durationMs = 0,
                bucketId = entity.originalBucketId,
                bucketName = entity.originalRelativePath?.substringAfterLast('/') ?: "Recently Deleted",
                relativePath = entity.originalRelativePath,
                mediaType = kind,
                source = MediaSource.DEEP_SCAN,
            )
            results += RecoveryResult(
                id = "${RecoverySource.TRASH.name}|${entity.trashUri}",
                item = item,
                source = RecoverySource.TRASH,
                confidence = RecoveryConfidence.HIGH,
                status = RecoveryStatus.AVAILABLE,
                evidence = "This app is holding the file's bytes, so it can be restored or exported.",
                originLabel = "Recently Deleted",
            )
        }

        // ---- 3. files in granted folders that the index does not list ------
        val directories = scanDirectoryDao.all()
        var foldersScanned = 0
        var unindexedFound = 0
        val progressThrottle = ScanProgressThrottle()
        val visited = HashSet<String>()

        // The index and a granted folder use different URI schemes, so they can
        // never be matched by URI. Name plus size is used instead - a heuristic,
        // and described as one.
        val indexKeys = indexed.mapTo(HashSet()) { indexedKey(it.displayName, it.sizeBytes) }

        for (entity in directories) {
            currentCoroutineContext().ensureActive()
            val root = DocumentFile.fromTreeUri(context, Uri.parse(entity.treeUri))
            if (root == null || !root.canRead()) {
                errors += "Cannot read ${entity.displayName} — the grant may have been revoked."
                continue
            }
            foldersScanned++

            val stack = ArrayDeque<Pair<DocumentFile, Int>>()
            stack.addLast(root to 0)

            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (results.size >= settings.maxScanResults) {
                    errors += "Stopped at the configured limit of ${settings.maxScanResults} results."
                    break
                }
                val (dir, depth) = stack.removeLast()

                // Keep the screen alive during a long walk.
                if (progressThrottle.shouldPublish(filesExamined)) {
                    emit(
                        RecoveryScanState.Scanning(
                            if (mode == RecoveryScanMode.THUMBNAIL_CACHE) {
                                "Looking for thumbnails and cached previews"
                            } else {
                                "Looking inside ${entity.displayName}"
                            },
                            results.size,
                        ),
                    )
                }

                val children = dir.listFiles()
                if (children.isEmpty()) continue

                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    val name = child.name ?: continue

                    if (child.isDirectory) {
                        if (depth < settings.maxRecursionDepth) stack.addLast(child to (depth + 1))
                        continue
                    }
                    if (!settings.includeHiddenFiles && name.startsWith(".")) continue
                    val extension = FileTypeRegistry.extensionOf(name)
                    if (!settings.includeExtensionlessFiles && extension.isEmpty()) continue

                    val size = child.length()
                    if (!settings.acceptsSize(size)) continue

                    // The same file can be reachable through more than one granted
                    // tree, and a folder can be nested inside another grant. Keyed by
                    // the canonical document URI, so nothing is reported twice.
                    if (!visited.add(child.uri.toString())) continue

                    // In quick mode a file the index already lists is not a
                    // discovery. In thumbnail mode the user asked for artefacts,
                    // indexed or not, so nothing is skipped on that basis.
                    if (includeIndexAndTrash && indexedKey(name, size) in indexKeys) {
                        skippedAlreadyIndexed++
                        continue
                    }

                    val readable = child.canRead()
                    val classified = if (readable) {
                        classifier.classify(
                            name = name,
                            declaredMime = child.type,
                            readSignature = settings.signatureDetectionEnabled,
                            maxSignatureBytes = settings.signatureReadBytes,
                            openStream = {
                                runCatching { context.contentResolver.openInputStream(child.uri) }.getOrNull()
                            },
                        )
                    } else {
                        null
                    }

                    val kind = classified?.kind ?: MediaType.OTHER
                    if (kind !in settings.enabledKinds) continue

                    // Read the pixel size from the header only, so a thumbnail can
                    // show an estimated resolution.
                    val bounds = if (classified?.kind == MediaType.IMAGE) {
                        boundsReader.read {
                            runCatching { context.contentResolver.openInputStream(child.uri) }.getOrNull()
                        }
                    } else {
                        null
                    }

                    val verdict = ThumbnailHeuristics.evaluate(
                        name = name,
                        relativePath = child.uri.toString(),
                        width = bounds?.width ?: 0,
                        height = bounds?.height ?: 0,
                    )

                    // The thumbnail mode returns artefacts and nothing else.
                    if (mode == RecoveryScanMode.THUMBNAIL_CACHE && !verdict.isThumbnail) continue

                    val judgement = if (verdict.isThumbnail) {
                        RecoveryConfidencePolicy.forThumbnailArtifact(
                            identifiedBySignature = classified?.identifiedBySignature == true,
                        )
                    } else {
                        RecoveryConfidencePolicy.evaluate(
                            identifiedBySignature = classified?.identifiedBySignature == true,
                            hasExtension = extension.isNotEmpty(),
                            hasDeclaredMime = !child.type.isNullOrBlank(),
                            isReadable = readable,
                        )
                    }

                    unindexedFound++
                    val item = MediaItem(
                        id = child.uri.toString().hashCode().toLong(),
                        uri = child.uri,
                        displayName = name,
                        mimeType = classified?.mimeType?.ifBlank { child.type ?: "" } ?: (child.type ?: ""),
                        sizeBytes = size,
                        dateModifiedSeconds = child.lastModified() / 1000,
                        width = bounds?.width ?: 0,
                        height = bounds?.height ?: 0,
                        durationMs = 0,
                        bucketId = entity.displayName.hashCode().toLong(),
                        bucketName = entity.displayName,
                        relativePath = null,
                        mediaType = kind,
                        source = MediaSource.DEEP_SCAN,
                    )
                    results += RecoveryResult(
                        id = "${if (verdict.isThumbnail) RecoverySource.THUMBNAIL_CACHE.name else RecoverySource.SAF_FOLDER.name}|${child.uri}",
                        item = item,
                        source = if (verdict.isThumbnail) {
                            RecoverySource.THUMBNAIL_CACHE
                        } else {
                            RecoverySource.SAF_FOLDER
                        },
                        confidence = if (verdict.isThumbnail) verdict.confidence else judgement.confidence,
                        status = judgement.status,
                        evidence = if (verdict.isThumbnail) verdict.reason else judgement.evidence,
                        isThumbnail = verdict.isThumbnail,
                        originLabel = entity.displayName,
                    )
                }
            }
        }

        emit(
            RecoveryScanState.Completed(
                results = results,
                summary = RecoveryScanSummary(
                    indexedFound = indexed.size,
                    trashFound = trashed.size,
                    unindexedFound = unindexedFound,
                    foldersScanned = foldersScanned,
                    skippedAlreadyIndexed = skippedAlreadyIndexed,
                    errors = errors.take(50),
                ),
            ),
        )
    }

    private fun indexedKey(name: String, size: Long): String = "$name|$size"

    /**
     * Opens a result's bytes for export. Works for every source: our trash
     * (`file://`), the system index (`content://media/...`) and a granted folder
     * (`content://...documents/...`).
     */
    fun openSource(result: RecoveryResult) =
        runCatching { context.contentResolver.openInputStream(result.item.uri) }.getOrNull()

    /** Unused by the scan itself, kept so callers can open a plain file too. */
    internal fun openFile(file: java.io.File) = FileInputStream(file)
}
