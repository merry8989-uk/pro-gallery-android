package com.progallery.app.data.recovery

/** What a file looks like: a thumbnail/cache artefact, or an ordinary file. */
data class ThumbnailVerdict(
    val isThumbnail: Boolean,
    val confidence: RecoveryConfidence,
    val reason: String,
)

/**
 * Decides whether a discovered file is a thumbnail or cached preview rather than
 * the original image.
 *
 * This matters because a thumbnail is **not** the picture the user lost. It is
 * still worth showing — and worth exporting as the small file it is — but it must
 * never be presented as the original.
 *
 * Small dimensions on their own are deliberately **not** treated as evidence: a
 * genuinely small photo is a small photo. The signals are the file's name, the
 * folder it lives in, and a size recorded in its name.
 *
 * Pure, so every branch is unit tested.
 */
object ThumbnailHeuristics {

    private val DIR_HINTS = listOf(
        "thumbnail", "thumbnails", "thumbs", ".thumbs", "cache", ".cache",
        "preview", "previews", ".tmp",
    )

    private val NAME_HINTS = listOf(
        "_thumb", "-thumb", "thumb_", "thumbnail", ".thumb", "thumb-",
    )

    /** A size like "320x240" written into the file name. */
    private val SIZE_IN_NAME = Regex("""(?:^|[^0-9])(\d{2,4})x(\d{2,4})(?:[^0-9]|$)""")

    /** Anything larger than this is not plausibly a thumbnail. */
    private const val MAX_THUMBNAIL_SIDE = 512

    fun evaluate(
        name: String,
        relativePath: String?,
        width: Int,
        height: Int,
    ): ThumbnailVerdict {
        val lowerName = name.lowercase()
        val lowerPath = (relativePath ?: "").lowercase()

        val namedLikeThumbnail = NAME_HINTS.any { lowerName.contains(it) }
        val inCacheFolder = DIR_HINTS.any { lowerPath.contains(it) }
        val tiny = width > 0 && height > 0 && maxOf(width, height) <= MAX_THUMBNAIL_SIDE
        val sizeInName = SIZE_IN_NAME.containsMatchIn(lowerName)
        val dimensions = if (width > 0 && height > 0) " ($width×$height)" else ""

        return when {
            namedLikeThumbnail && tiny -> ThumbnailVerdict(
                isThumbnail = true,
                confidence = RecoveryConfidence.HIGH,
                reason = "The name marks it as a thumbnail and it is only${dimensions.trim().let { if (it.isEmpty()) " a small size" else it }}.",
            )

            namedLikeThumbnail -> ThumbnailVerdict(
                isThumbnail = true,
                confidence = RecoveryConfidence.MEDIUM,
                reason = "The file name marks it as a thumbnail.",
            )

            inCacheFolder && tiny -> ThumbnailVerdict(
                isThumbnail = true,
                confidence = RecoveryConfidence.HIGH,
                reason = "It sits in a thumbnail/cache folder and is only$dimensions.",
            )

            inCacheFolder -> ThumbnailVerdict(
                isThumbnail = true,
                confidence = RecoveryConfidence.MEDIUM,
                reason = "It sits in a folder that holds thumbnails or cached files.",
            )

            sizeInName && tiny -> ThumbnailVerdict(
                isThumbnail = true,
                confidence = RecoveryConfidence.MEDIUM,
                reason = "Its name records a size and the image really is that small$dimensions.",
            )

            else -> ThumbnailVerdict(
                isThumbnail = false,
                confidence = RecoveryConfidence.LOW,
                reason = "Looks like an ordinary file, not a thumbnail.",
            )
        }
    }
}
