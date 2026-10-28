package com.progallery.app.data.documents

import com.progallery.app.data.capability.Capability
import com.progallery.app.data.capability.CapabilityMatrix
import com.progallery.app.data.model.ImageDecodability
import com.progallery.app.data.model.MediaItem

/** Which of the app's viewers opens a file. */
enum class Viewer { IMAGE, PLAYER, PDF, TEXT, ARCHIVE }

/**
 * Where a file goes when it is tapped.
 *
 * Two answers, and there is deliberately no third. A file either opens **here**, with one of the
 * app's viewers, or it is **handed over** with a reason. What it cannot be is nothing.
 */
sealed interface FileDestination {

    /** Open it in the app, with this viewer. */
    data class Here(val viewer: Viewer) : FileDestination

    /**
     * Nothing here can open it, so another app is asked — and the reason says why.
     *
     * The reason is never invented: it comes from the capability matrix, which is the same table
     * the capability screen shows. A file that cannot be opened anywhere says that too.
     */
    data class HandOff(val reason: String) : FileDestination
}

/**
 * One place that decides where a tapped file goes.
 *
 * There was already a `when` in the navigation host claiming to be that place, and it had **no
 * `else` branch** — so a document, an archive, or any extension the app does not recognise had its
 * tap silently ignored. Nothing happened, and nothing said why. That is the worst of the three
 * possible outcomes: worse than opening the wrong viewer, and worse than an honest refusal.
 *
 * The capability matrix is the **only** source of truth here. It is the same table the capability
 * screen is built from, so what the app says it can open and what it does when a file is tapped
 * cannot disagree — which is a class of bug this project has already had once, between the log and
 * the screen.
 *
 * Pure, so a test can walk **every extension the app knows** and assert that each one goes
 * somewhere. That test is what "every format has a way in" actually means.
 */
object FileOpener {

    fun destinationFor(item: MediaItem): FileDestination {
        val capability = CapabilityMatrix.forExtension(item.extension)

        // The matrix has already answered this one, and its reason is the honest one to show.
        if (capability.verdict == Capability.NEEDS_ANOTHER_APP) {
            return FileDestination.HandOff(capability.reason ?: "No app on this device can open it.")
        }

        // An image the platform cannot decode would open a viewer that shows nothing, so it is a
        // hand-off rather than a blank screen. `ImageDecodability` is the same table the thumbnail
        // policy reads, so a tile that shows an icon and a tap that refuses agree with each other.
        if (item.isImage) {
            return if (ImageDecodability.canRender(item.extension)) {
                FileDestination.Here(Viewer.IMAGE)
            } else {
                FileDestination.HandOff(
                    capability.reason
                        ?: "This device cannot decode this image format, so the app cannot draw it.",
                )
            }
        }

        if (item.isAudio || item.isVideo) return FileDestination.Here(Viewer.PLAYER)
        if (item.isPdf) return FileDestination.Here(Viewer.PDF)

        // An archive has a viewer of its own now. It used to fall through to the hand-off below,
        // which meant "extract" was not an option anywhere in the app — the honest answer to "where
        // do I extract it" was "nowhere". The viewer lists what is inside and unpacks it into a
        // folder the person picks.
        if (item.isArchive) return FileDestination.Here(Viewer.ARCHIVE)

        // Text, JSON, CSV, code, and the document formats the app reads itself.
        if (item.isTextReadable) return FileDestination.Here(Viewer.TEXT)

        // Everything else — an archive, a document nothing here parses, a kind with no viewer.
        // This is the branch the old `when` did not have, and its absence is why a tap could do
        // nothing at all.
        return FileDestination.HandOff(
            capability.reason
                ?: "The app has no viewer for .${item.extension.ifBlank { "this file" }}, so it is " +
                "handed to another app.",
        )
    }
}
