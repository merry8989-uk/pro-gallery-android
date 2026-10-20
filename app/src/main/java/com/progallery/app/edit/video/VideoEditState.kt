package com.progallery.app.edit.video

/** Target resolution for an export. */
enum class ResolutionPreset(val label: String, val targetHeight: Int) {
    ORIGINAL("Original", 0),
    P720("720p", 720),
    P1080("1080p", 1080),
}

/**
 * Non-destructive description of a video edit. Pure Kotlin for unit testing.
 */
data class VideoEditState(
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val mute: Boolean = false,
    val rotationDegrees: Int = 0,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
) {
    /** Clamps the trim range into [0, durationMs] and guarantees start < end. */
    fun normalized(durationMs: Long): VideoEditState {
        val end = if (trimEndMs <= 0L) durationMs else trimEndMs.coerceAtMost(durationMs)
        val start = trimStartMs.coerceIn(0L, (end - 1).coerceAtLeast(0L))
        return copy(trimStartMs = start, trimEndMs = end)
    }

    val trimDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
}
