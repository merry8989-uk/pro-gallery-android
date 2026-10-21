package com.progallery.app.data.appmedia

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What one app's media folder holds. */
data class AppMediaFolder(
    val packageName: String,
    /** The app's own name where the system could supply one. */
    val label: String,
    val installed: Boolean,
    /** Null when the app has no media folder on this device. */
    val folderPath: String?,
    val images: Int = 0,
    val videos: Int = 0,
    val audio: Int = 0,
    val documents: Int = 0,
    val other: Int = 0,
    /**
     * True when counting stopped early, so the numbers are a floor rather than a total.
     * Said plainly in the UI instead of presenting a guess as a count.
     */
    val countCapped: Boolean = false,
) {
    val total: Int get() = images + videos + audio + documents + other
    val hasMedia: Boolean get() = total > 0
}

/**
 * Finds the media that messaging apps keep on the device.
 *
 * **How it decides which apps to look at.** Two sources, and the second is what makes this
 * work for apps nobody listed:
 *
 *  1. every folder that **actually exists** under `Android/media/`, whose name *is* the package
 *     name, plus the older top-level `WhatsApp/` and `Telegram/` folders;
 *  2. a short list of verified package names, so an installed app with no media yet can still
 *     be shown as installed.
 *
 * **The name comes from the system, not from a table.** For an installed app the label is read
 * from the PackageManager, so "Arattai", "Hyped" and anything else appear under the name the
 * device itself uses.
 */
@Singleton
class AppMediaDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        /** Counting stops here, so a folder with a hundred thousand files cannot hang the app. */
        const val MAX_ENTRIES = 20_000
        const val MAX_DEPTH = 6
        const val MEDIA_DIR = "Android/media"

        /** The older top-level folders, by their name on disk. */
        val LEGACY_DIRS = listOf(
            "WhatsApp" to "com.whatsapp",
            "WhatsApp Business" to "com.whatsapp.w4b",
            "Telegram" to "org.telegram.messenger",
        )
    }

    suspend fun detect(): List<AppMediaFolder> = withContext(Dispatchers.IO) {
        val roots = storageRoots()

        val discovered = LinkedHashSet<String>()
        roots.forEach { root ->
            File(root, MEDIA_DIR).listFiles()?.forEach { child ->
                if (child.isDirectory && child.name.contains('.')) discovered += child.name
            }
            LEGACY_DIRS.forEach { (name, packageName) ->
                if (File(root, name).isDirectory) discovered += packageName
            }
        }

        val candidates = LinkedHashSet<String>()
        candidates += KnownApps.MESSAGING.map { it.packageName }
        candidates += discovered

        candidates
            .map { inspect(it, roots) }
            // Apps with media first, then the most media, then alphabetically, so the list is
            // useful before it is complete.
            .sortedWith(
                compareByDescending<AppMediaFolder> { it.hasMedia }
                    .thenByDescending { it.total }
                    .thenBy { it.label.lowercase() },
            )
    }

    private fun inspect(packageName: String, roots: List<File>): AppMediaFolder {
        val installedLabel = installedLabel(packageName)
        val label = installedLabel
            ?: KnownApps.nameOf(packageName)
            ?: packageName

        val folder = folderFor(packageName, roots)
        if (folder == null) {
            return AppMediaFolder(
                packageName = packageName,
                label = label,
                installed = installedLabel != null,
                folderPath = null,
            )
        }

        val counts = count(folder)
        return AppMediaFolder(
            packageName = packageName,
            label = label,
            installed = installedLabel != null,
            folderPath = folder.absolutePath,
            images = counts.byKind[MediaType.IMAGE] ?: 0,
            videos = counts.byKind[MediaType.VIDEO] ?: 0,
            audio = counts.byKind[MediaType.AUDIO] ?: 0,
            documents = counts.byKind[MediaType.DOCUMENT] ?: 0,
            other = counts.byKind.entries.filter { it.key !in COUNTED }.sumOf { it.value },
            countCapped = counts.capped,
        )
    }

    /** The app's own name, or null when it is not installed. */
    private fun installedLabel(packageName: String): String? = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** The first folder on this device that belongs to [packageName]. */
    private fun folderFor(packageName: String, roots: List<File>): File? {
        roots.forEach { root ->
            val modern = File(root, "$MEDIA_DIR/$packageName")
            if (modern.isDirectory) return modern
        }
        roots.forEach { root ->
            LEGACY_DIRS.filter { it.second == packageName }.forEach { (name, _) ->
                val legacy = File(root, name)
                if (legacy.isDirectory) return legacy
            }
        }
        return null
    }

    /**
     * Counts media by kind, breadth-first and bounded.
     *
     * Bounded on purpose: a messaging app's media folder can hold tens of thousands of files,
     * and the number is only there to tell the user what is available.
     */
    private fun count(folder: File): Counts {
        val counts = HashMap<MediaType, Int>()
        var entries = 0
        var capped = false

        val queue = ArrayDeque<Pair<File, Int>>()
        queue += folder to 0

        while (queue.isNotEmpty()) {
            val (directory, depth) = queue.removeFirst()
            if (depth > MAX_DEPTH) continue
            val children = directory.listFiles() ?: continue

            for (child in children) {
                if (entries++ >= MAX_ENTRIES) {
                    capped = true
                    break
                }
                if (child.isDirectory) {
                    queue += child to (depth + 1)
                } else {
                    val kind = FileTypeRegistry.kindFor(null, child.name)
                    counts[kind] = (counts[kind] ?: 0) + 1
                }
            }
            if (capped) break
        }

        return Counts(counts, capped)
    }

    /**
     * Every storage root on the device, so an SD card's app media is found too.
     *
     * The app's own media directories sit at `<root>/Android/media/<package>`, so the root is
     * three levels up — which works for the internal storage and for a card alike.
     */
    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            ?.let { roots += it }

        runCatching { context.externalMediaDirs }.getOrNull()?.forEach { dir ->
            var candidate: File? = dir
            repeat(3) { candidate = candidate?.parentFile }
            candidate?.let { roots += it }
        }
        return roots.filter { it.isDirectory }
    }
}

/** Counts by kind, and whether counting stopped early. */
private data class Counts(val byKind: Map<MediaType, Int>, val capped: Boolean)

private val COUNTED = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.DOCUMENT)
