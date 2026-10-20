package com.progallery.app.data.permissions

/** How much of an access the app currently has. */
enum class AccessState { GRANTED, PARTIAL, DENIED, NOT_APPLICABLE }

/** What the user can do about a row. */
enum class AccessFix {
    NONE,
    REQUEST_PERMISSION,
    REQUEST_MORE_PHOTOS,
    OPEN_APP_SETTINGS,
    OPEN_ALL_FILES_SETTINGS,
    PICK_FOLDER,
}

/** The accesses worth explaining. */
enum class AccessId { MEDIA_VISUAL, MEDIA_AUDIO, MEDIA_LOCATION, ALL_FILES, FOLDERS, ROOT }

/**
 * One line of the diagnostics: what it is, what state it is in, what it unlocks, and
 * what to do about it.
 */
data class AccessRow(
    val id: AccessId,
    val title: String,
    val state: AccessState,
    /** What the app can do *because* of this. Written for the user, not the developer. */
    val enables: String,
    /** What it costs to be without it, or why it does not matter. */
    val consequence: String,
    val fix: AccessFix,
    val fixLabel: String? = null,
)

/**
 * Everything the screen needs, gathered by the Android layer.
 *
 * A plain snapshot of values, so the whole decision — which rows appear, what they say
 * and what they recommend — is unit tested without a device or a permission dialog.
 */
data class AccessSnapshot(
    val sdkInt: Int,
    val visual: AccessState,
    val audio: AccessState,
    val location: AccessState,
    val allFiles: Boolean,
    val grantedFolders: Int,
    /** The read-only root check, or null when it has not been run. */
    val rootStatus: String? = null,
)

/**
 * Turns a snapshot into the rows the diagnostics screen shows.
 *
 * The wording matters here: the app must never imply that a permission unlocks more
 * than it does, and must never imply that a missing optional access is a fault.
 */
object AccessDiagnostics {

    fun rows(snapshot: AccessSnapshot): List<AccessRow> = buildList {
        add(visualRow(snapshot))
        add(audioRow(snapshot))
        add(locationRow(snapshot))
        add(allFilesRow(snapshot))
        add(foldersRow(snapshot))
        snapshot.rootStatus?.let { add(rootRow(it)) }
    }

    /** One line for the top of the screen. */
    fun headline(rows: List<AccessRow>): String {
        val required = rows.filter { it.id in REQUIRED }
        val blocked = required.filter { it.state == AccessState.DENIED }
        return when {
            blocked.isEmpty() -> "The app has the access it needs."
            blocked.size == 1 -> "One access still needed: ${blocked.first().title}."
            else -> "${blocked.size} accesses still needed."
        }
    }

    /** The rows that actually stop the app working, in the order they appear. */
    fun blocking(rows: List<AccessRow>): List<AccessRow> =
        rows.filter { it.id in REQUIRED && it.state == AccessState.DENIED }

    /** Accesses without which the app cannot show a library at all. */
    private val REQUIRED = setOf(AccessId.MEDIA_VISUAL)

    private fun visualRow(snapshot: AccessSnapshot): AccessRow = AccessRow(
        id = AccessId.MEDIA_VISUAL,
        title = "Photos and videos",
        state = snapshot.visual,
        enables = "Browsing your photos and videos, their thumbnails and their details.",
        consequence = when (snapshot.visual) {
            AccessState.GRANTED -> "Everything is visible."
            AccessState.PARTIAL ->
                "Only the photos you picked are visible. Android lets you add more at any time."
            AccessState.DENIED -> "Without this the library has nothing to show."
            AccessState.NOT_APPLICABLE -> "Not needed on this device."
        },
        fix = when (snapshot.visual) {
            AccessState.PARTIAL -> AccessFix.REQUEST_MORE_PHOTOS
            AccessState.DENIED -> AccessFix.REQUEST_PERMISSION
            else -> AccessFix.NONE
        },
        fixLabel = when (snapshot.visual) {
            AccessState.PARTIAL -> "Choose more photos"
            AccessState.DENIED -> "Allow"
            else -> null
        },
    )

    private fun audioRow(snapshot: AccessSnapshot): AccessRow = AccessRow(
        id = AccessId.MEDIA_AUDIO,
        title = "Music and audio",
        state = snapshot.audio,
        enables = "Showing your audio files and playing them in the in-app player.",
        consequence = when (snapshot.audio) {
            AccessState.GRANTED -> "Audio files are listed and playable."
            AccessState.DENIED -> "Audio files will not appear. Photos and videos still work."
            else -> "Not needed on this device: audio comes with the storage permission."
        },
        fix = if (snapshot.audio == AccessState.DENIED) AccessFix.REQUEST_PERMISSION else AccessFix.NONE,
        fixLabel = if (snapshot.audio == AccessState.DENIED) "Allow" else null,
    )

    private fun locationRow(snapshot: AccessSnapshot): AccessRow = AccessRow(
        id = AccessId.MEDIA_LOCATION,
        title = "Photo locations",
        state = snapshot.location,
        enables = "Reading where a photo was taken, when the photo carries it.",
        consequence = when (snapshot.location) {
            AccessState.GRANTED -> "Location data in your photos can be read."
            else -> "Optional. Without it, the app simply does not read where a photo was taken."
        },
        fix = if (snapshot.location == AccessState.DENIED) AccessFix.REQUEST_PERMISSION else AccessFix.NONE,
        fixLabel = if (snapshot.location == AccessState.DENIED) "Allow" else null,
    )

    private fun allFilesRow(snapshot: AccessSnapshot): AccessRow = AccessRow(
        id = AccessId.ALL_FILES,
        title = "All files access",
        state = if (snapshot.allFiles) AccessState.GRANTED else AccessState.DENIED,
        enables = "Documents, archives and other non-media files — including those created " +
            "by other apps — inside folders you grant.",
        consequence = if (snapshot.allFiles) {
            "Non-media files in your granted folders are visible."
        } else {
            "Optional. Android hides other apps' documents from a normal media permission; " +
                "Deep Scan over a folder you grant is the alternative, and needs no extra access."
        },
        fix = if (snapshot.allFiles) AccessFix.NONE else AccessFix.OPEN_ALL_FILES_SETTINGS,
        fixLabel = if (snapshot.allFiles) null else "Open settings",
    )

    private fun foldersRow(snapshot: AccessSnapshot): AccessRow = AccessRow(
        id = AccessId.FOLDERS,
        title = "Folders you granted",
        state = if (snapshot.grantedFolders > 0) AccessState.GRANTED else AccessState.DENIED,
        enables = "Deep Scan, duplicate detection and recovery inside those folders.",
        consequence = when (snapshot.grantedFolders) {
            0 -> "No folders granted yet. Deep Scan has nothing to walk until you add one."
            1 -> "One folder is being scanned."
            else -> "${snapshot.grantedFolders} folders are being scanned."
        },
        fix = if (snapshot.grantedFolders > 0) AccessFix.NONE else AccessFix.PICK_FOLDER,
        fixLabel = if (snapshot.grantedFolders > 0) null else "Add a folder",
    )

    private fun rootRow(status: String): AccessRow = AccessRow(
        id = AccessId.ROOT,
        title = "Root status",
        state = AccessState.NOT_APPLICABLE,
        enables = "Nothing. This is a read-only check, shown for information only.",
        consequence = "$status The app never requests root and does not recover with it.",
        fix = AccessFix.NONE,
    )
}
