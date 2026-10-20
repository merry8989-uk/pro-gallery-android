package com.progallery.app.data.deepscan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.data.local.entity.ScanDirectoryEntity
import com.progallery.app.data.model.DuplicateGroup
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.ScanSummary
import com.progallery.app.util.MediaFormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    data class Completed(val summary: ScanSummary, val media: List<MediaItem>, val duplicates: List<DuplicateGroup>) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Deep scanning over user-authorised directory trees (SAF). This complements
 * MediaStore: it can surface supported files the index has not picked up, and
 * it finds duplicates. It never touches directories the user has not granted,
 * and never bypasses scoped storage.
 */
@Singleton
class DeepScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanDirectoryDao: ScanDirectoryDao,
    private val hasher: FileHasher,
) {

    private val detector = DuplicateDetector { item -> hasher.sha256(item.uri) }

    fun observeAuthorisedDirectories(): Flow<List<ScanDirectoryEntity>> =
        scanDirectoryDao.observeAll()

    /** Persists a directory the user picked via ACTION_OPEN_DOCUMENT_TREE. */
    suspend fun addAuthorisedDirectory(treeUri: Uri, displayName: String) = withContext(Dispatchers.IO) {
        // Take a persistable grant so access survives process death.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        scanDirectoryDao.add(
            ScanDirectoryEntity(treeUri = treeUri.toString(), displayName = displayName),
        )
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

    /**
     * Recursively scans every authorised tree, emitting [ScanState] progress.
     * Cancelling the collecting coroutine cancels the scan cleanly.
     */
    fun scanAll(findDuplicates: Boolean = true): Flow<ScanState> = flow {
        emit(ScanState.Scanning(0, 0, 0, ""))
        val directories = scanDirectoryDao.all()
        if (directories.isEmpty()) {
            emit(ScanState.Completed(ScanSummary(0, 0, 0, 0, emptyList()), emptyList(), emptyList()))
            return@flow
        }

        val found = ArrayList<MediaItem>()
        val errors = ArrayList<String>()
        val visited = HashSet<String>()
        var dirsScanned = 0
        var filesExamined = 0

        for (entity in directories) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(entity.treeUri))
            if (root == null || !root.canRead()) {
                errors += "Cannot read ${entity.displayName}"
                continue
            }
            val stack = ArrayDeque<Pair<DocumentFile, String>>()
            stack.addLast(root to entity.displayName)
            while (stack.isNotEmpty()) {
                val (dir, path) = stack.removeLast()
                dirsScanned++
                emit(ScanState.Scanning(dirsScanned, filesExamined, found.size, path))
                val children = runCatching { dir.listFiles() }.getOrElse {
                    errors += "Failed to list $path"
                    emptyArray()
                }
                for (child in children) {
                    if (!child.canRead()) { errors += "Unreadable: ${child.name}"; continue }
                    if (child.isDirectory) {
                        stack.addLast(child to "$path/${child.name}")
                        continue
                    }
                    filesExamined++
                    val mime = child.type ?: guessMime(child.name)
                    if (!MediaFormatUtils.isSupported(mime)) continue
                    val uri = child.uri
                    if (!visited.add(uri.toString())) continue
                    val isVideo = MediaFormatUtils.isVideo(mime)
                    found += MediaItem(
                        id = uri.toString().hashCode().toLong(),
                        uri = uri,
                        displayName = child.name ?: "Unnamed",
                        mimeType = mime,
                        sizeBytes = child.length(),
                        dateModifiedSeconds = child.lastModified() / 1000,
                        width = 0,
                        height = 0,
                        durationMs = 0,
                        bucketId = path.hashCode().toLong(),
                        bucketName = path.substringAfterLast('/'),
                        relativePath = path,
                        mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                        source = MediaSource.DEEP_SCAN,
                    )
                }
            }
            scanDirectoryDao.markScanned(entity.treeUri, System.currentTimeMillis())
        }

        val duplicates = if (findDuplicates) detector.findDuplicates(found) else emptyList()
        val summary = ScanSummary(
            directoriesScanned = dirsScanned,
            filesExamined = filesExamined,
            mediaFound = found.size,
            duplicatesFound = duplicates.sumOf { it.items.size - 1 },
            errors = errors.take(50),
        )
        emit(ScanState.Completed(summary, found, duplicates))
    }.flowOn(Dispatchers.IO)

    private fun guessMime(name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return ""
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> ""
        }
    }
}
