package com.progallery.app.data.deepscan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.data.local.entity.ScanDirectoryEntity
import com.progallery.app.data.model.DuplicateGroup
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.ScanSummary
import com.progallery.app.data.recovery.FileSignatureDetector
import com.progallery.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Progress + result states emitted while a deep scan runs. */
sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(
        val directoriesScanned: Int,
        val filesExamined: Int,
        val mediaFound: Int,
        val currentPath: String,
    ) : ScanState
    data class Completed(
        val summary: ScanSummary,
        val media: List<MediaItem>,
        val duplicates: List<DuplicateGroup>,
    ) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Deep scanning over user-authorised directory trees (SAF).
 *
 * Every behaviour here is driven by the user's settings, so a toggle has a real
 * effect: disabled categories are skipped, signature detection can be turned
 * off, duplicate hashing can be turned off, and the depth/result limits are
 * enforced. Cancelling the collector stops the walk promptly.
 */
@Singleton
class DeepScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanDirectoryDao: ScanDirectoryDao,
    private val hasher: FileHasher,
    private val settingsRepository: SettingsRepository,
) {

    private val detector = DuplicateDetector { item -> hasher.sha256(item.uri) }

    fun observeAuthorisedDirectories(): Flow<List<ScanDirectoryEntity>> =
        scanDirectoryDao.observeAll()

    /**
     * Persists a directory the user picked via ACTION_OPEN_DOCUMENT_TREE.
     * Returns false when the grant could not be persisted — the caller must not
     * record a directory the app cannot actually read later.
     */
    suspend fun addAuthorisedDirectory(treeUri: Uri, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                true
            }.getOrDefault(false)

            if (!persisted) return@withContext false

            scanDirectoryDao.add(
                ScanDirectoryEntity(treeUri = treeUri.toString(), displayName = displayName),
            )
            true
        }

    suspend fun removeAuthorisedDirectory(treeUri: String) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        scanDirectoryDao.remove(treeUri)
    }

    /** Recursively scans every authorised tree, emitting [ScanState] progress. */
    fun scanAll(): Flow<ScanState> = flow {
        val settings = settingsRepository.current()

        if (!settings.deepScanEnabled && !settings.safScanEnabled) {
            emit(ScanState.Failed("Deep scan is turned off in Settings."))
            return@flow
        }
        if (!settings.anyCategoryEnabled) {
            emit(
                ScanState.Failed(
                    "Every file category is disabled in Settings, so there is nothing to look for.",
                ),
            )
            return@flow
        }

        emit(ScanState.Scanning(0, 0, 0, ""))

        val directories = scanDirectoryDao.all()
        if (directories.isEmpty()) {
            emit(
                ScanState.Completed(
                    ScanSummary(0, 0, 0, 0, emptyList()),
                    emptyList(),
                    emptyList(),
                ),
            )
            return@flow
        }

        val found = ArrayList<MediaItem>()
        val errors = ArrayList<String>()
        val visited = HashSet<String>()
        var directoriesScanned = 0
        var filesExamined = 0
        var signatureIdentified = 0
        var truncated = false
        var lastProgressAt = 0

        for (entity in directories) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(entity.treeUri))
            if (root == null || !root.canRead()) {
                errors += "Cannot read ${entity.displayName} — the grant may have been revoked."
                continue
            }

            // Depth is tracked so the configured recursion limit is enforced.
            val stack = ArrayDeque<Triple<DocumentFile, String, Int>>()
            stack.addLast(Triple(root, entity.displayName, 0))

            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (dir, path, depth) = stack.removeLast()
                directoriesScanned++

                val children = runCatching { dir.listFiles() }.getOrElse {
                    errors += "Failed to list $path"
                    emptyArray()
                }

                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    val name = child.name ?: continue

                    if (child.isDirectory) {
                        if (depth >= settings.maxRecursionDepth) {
                            errors += "Skipped $path/$name — deeper than the configured limit"
                            continue
                        }
                        stack.addLast(Triple(child, "$path/$name", depth + 1))
                        continue
                    }

                    if (!child.canRead()) {
                        filesExamined++
                        errors += "Unreadable: $name"
                        continue
                    }

                    // Hidden and extensionless files are opt-in.
                    if (!settings.includeHiddenFiles && name.startsWith(".")) continue
                    val extension = FileTypeRegistry.extensionOf(name)
                    if (!settings.includeExtensionlessFiles && extension.isEmpty()) continue

                    filesExamined++
                    val uri = child.uri
                    if (!visited.add(uri.toString())) continue

                    var mime = child.type ?: ""
                    var kind = FileTypeRegistry.kindFor(mime, name)

                    // Signature detection: identify from the bytes when the
                    // extension/MIME is missing or generic.
                    if (settings.signatureDetectionEnabled &&
                        (kind == MediaType.OTHER || extension.isEmpty() || mime.isEmpty())
                    ) {
                        val sniffed = FileSignatureDetector.sniff(
                            openStream = { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() },
                            maxBytes = settings.signatureReadBytes,
                        )
                        if (sniffed != null) {
                            kind = sniffed.kind
                            mime = sniffed.mimeType
                            signatureIdentified++
                        }
                    }

                    if (kind !in settings.enabledKinds) continue

                    found += MediaItem(
                        id = uri.toString().hashCode().toLong(),
                        uri = uri,
                        displayName = name,
                        mimeType = mime,
                        sizeBytes = child.length(),
                        dateModifiedSeconds = child.lastModified() / 1000,
                        width = 0,
                        height = 0,
                        durationMs = 0,
                        bucketId = path.hashCode().toLong(),
                        bucketName = path.substringAfterLast('/'),
                        relativePath = path,
                        mediaType = kind,
                        source = MediaSource.DEEP_SCAN,
                    )

                    if (found.size >= settings.maxScanResults) {
                        truncated = true
                        break
                    }
                }

                if (truncated) break

                // Throttle progress so a huge folder doesn't flood the UI.
                if (filesExamined - lastProgressAt >= 25) {
                    lastProgressAt = filesExamined
                    emit(ScanState.Scanning(directoriesScanned, filesExamined, found.size, path))
                }
            }

            if (truncated) {
                errors += "Stopped at the configured limit of ${settings.maxScanResults} results."
                break
            }
            scanDirectoryDao.markScanned(entity.treeUri, System.currentTimeMillis())
        }

        // Hashing is the expensive part, so it is skipped entirely when the
        // user turns duplicate detection off.
        val duplicates = if (settings.duplicateDetectionEnabled) {
            detector.findDuplicates(found)
        } else {
            emptyList()
        }

        emit(
            ScanState.Completed(
                summary = ScanSummary(
                    directoriesScanned = directoriesScanned,
                    filesExamined = filesExamined,
                    mediaFound = found.size,
                    duplicatesFound = duplicates.sumOf { it.items.size - 1 },
                    errors = errors.take(50),
                    signatureIdentified = signatureIdentified,
                    truncated = truncated,
                ),
                media = found,
                duplicates = duplicates,
            ),
        )
    }.flowOn(Dispatchers.IO)
}
