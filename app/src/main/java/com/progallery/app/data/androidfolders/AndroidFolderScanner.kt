package com.progallery.app.data.androidfolders

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.progallery.app.data.deepscan.DeepScanRepository
import com.progallery.app.data.deepscan.DuplicateDetector
import com.progallery.app.data.deepscan.FileClassifier
import com.progallery.app.data.deepscan.FileHasher
import com.progallery.app.data.deepscan.ScanState
import com.progallery.app.data.model.DuplicateGroup
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.ScanSummary
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans the locations under `Android/` that a **non-rooted** app is actually
 * allowed to read — see [AndroidFolderPolicy] for the rules.
 *
 * Uses direct file access, which works for these paths only when the OS permits
 * it (All files access on Android 11+, or any storage permission before that).
 * Everything the user cannot reach is reported with the reason rather than
 * silently skipped.
 */
@Singleton
class AndroidFolderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classifier: FileClassifier,
    private val hasher: FileHasher,
    private val settingsRepository: SettingsRepository,
) {

    private val detector = DuplicateDetector { item -> hasher.sha256(item.uri) }

    /** The directory a target maps to, or null when it has no location here. */
    fun rootFor(target: AndroidFolderTarget): File? = when (target) {
        AndroidFolderTarget.MEDIA ->
            File(Environment.getExternalStorageDirectory(), "Android/media")
        AndroidFolderTarget.OWN_DATA ->
            context.getExternalFilesDir(null)?.parentFile
        AndroidFolderTarget.OTHER_APP_DATA ->
            File(Environment.getExternalStorageDirectory(), "Android/data")
        AndroidFolderTarget.OBB ->
            File(Environment.getExternalStorageDirectory(), "Android/obb")
    }

    /** Reachability report for the UI, evaluated for this device right now. */
    fun reachability() = AndroidFolderPolicy.evaluate(
        sdkInt = android.os.Build.VERSION.SDK_INT,
        hasAllFilesAccess = PermissionUtils.hasAllFilesAccess(context),
    )

    fun scan(targets: List<AndroidFolderTarget>): Flow<ScanState> = flow {
        try {
            emitAll(scanInternal(targets))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            emit(
                ScanState.Failed(
                    t.message?.takeIf { it.isNotBlank() }
                        ?: "The scan stopped unexpectedly (${t::class.java.simpleName}).",
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun scanInternal(targets: List<AndroidFolderTarget>): Flow<ScanState> = flow {
        val settings = settingsRepository.current()
        val allowed = reachability().filter { it.reachable }.map { it.target }
        val requested = targets.filter { it in allowed }

        if (requested.isEmpty()) {
            emit(
                ScanState.Failed(
                    "None of those folders can be read on this device. See the notes on this screen.",
                ),
            )
            return@flow
        }

        emit(ScanState.Scanning(0, 0, 0, ""))

        val found = ArrayList<MediaItem>()
        val errors = ArrayList<String>()
        val visited = HashSet<String>()
        var directoriesScanned = 0
        var filesExamined = 0
        var signatureIdentified = 0
        var skippedBySize = 0
        var truncated = false

        for (target in requested) {
            currentCoroutineContext().ensureActive()
            val root = rootFor(target)
            if (root == null || !root.exists()) {
                errors += "${target.label}: folder not present on this device."
                continue
            }
            if (!root.canRead()) {
                errors += "${target.label}: the system refused access to this folder."
                continue
            }

            val stack = ArrayDeque<Triple<File, String, Int>>()
            stack.addLast(Triple(root, target.label, 0))

            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (dir, path, depth) = stack.removeLast()
                directoriesScanned++

                val children = dir.listFiles()
                if (children == null) {
                    errors += "$path: could not be listed."
                    continue
                }

                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    val name = child.name

                    if (child.isDirectory) {
                        if (depth >= settings.maxRecursionDepth) {
                            errors += "$path/$name: deeper than the configured limit."
                            continue
                        }
                        stack.addLast(Triple(child, "$path/$name", depth + 1))
                        continue
                    }

                    if (!settings.includeHiddenFiles && name.startsWith(".")) continue
                    val extension = FileTypeRegistry.extensionOf(name)
                    if (!settings.includeExtensionlessFiles && extension.isEmpty()) continue

                    filesExamined++
                    val fileSize = child.length()
                    if (!settings.acceptsSize(fileSize)) {
                        skippedBySize++
                        continue
                    }
                    if (!visited.add(child.absolutePath)) continue

                    val classified = classifier.classify(
                        name = name,
                        declaredMime = null,
                        readSignature = settings.signatureDetectionEnabled,
                        maxSignatureBytes = settings.signatureReadBytes,
                        openStream = { runCatching { FileInputStream(child) }.getOrNull() },
                    )
                    if (classified.identifiedBySignature) signatureIdentified++
                    if (classified.kind !in settings.enabledKinds) continue

                    found += MediaItem(
                        id = child.absolutePath.hashCode().toLong(),
                        uri = Uri.fromFile(child),
                        displayName = name,
                        mimeType = classified.mimeType,
                        sizeBytes = fileSize,
                        dateModifiedSeconds = child.lastModified() / 1000,
                        width = 0,
                        height = 0,
                        durationMs = 0,
                        bucketId = path.hashCode().toLong(),
                        bucketName = path.substringAfterLast('/'),
                        relativePath = path,
                        mediaType = classified.kind,
                        source = MediaSource.DEEP_SCAN,
                    )

                    if (found.size >= settings.maxScanResults) {
                        truncated = true
                        break
                    }
                }
                if (truncated) break
            }
            if (truncated) {
                errors += "Stopped at the configured limit of ${settings.maxScanResults} results."
                break
            }
        }

        if (skippedBySize > 0) {
            errors += "Skipped $skippedBySize file(s) outside the size range (${settings.sizeRangeLabel})."
        }

        val duplicates: List<DuplicateGroup> = if (settings.duplicateDetectionEnabled) {
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
    }
}
