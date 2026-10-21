package com.progallery.app.data.update

/**
 * Compares version names the way a person reads them.
 *
 * Pure, because getting this wrong is the classic update bug: either the app never offers
 * an update, or it offers one that is older than what is installed and the user is told to
 * "upgrade" backwards.
 *
 * The rules are deliberately forgiving, because release tags are written by hand:
 *
 *  * a leading `v` is ignored, so `v1.16.0` and `1.16.0` are the same version;
 *  * everything from the first `-`, `+` or `_` is a pre-release or build suffix and is
 *    ignored, so `1.16.0-beta` and `1.16.0+build7` both count as `1.16.0`;
 *  * a missing segment counts as zero, so `1.16` and `1.16.0` are equal.
 *
 * A name with no digits at all is not a version, and [isNewer] refuses to guess at it.
 */
object VersionName {

    /** The numeric segments of [raw], or an empty list when it holds no digits. */
    fun segments(raw: String): List<Int> {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        // A pre-release or build suffix does not change which release this is, so it is cut
        // off before the segments are read.
        val core = trimmed.takeWhile { it != '-' && it != '+' && it != '_' }
        if (core.isEmpty()) return emptyList()
        return core.split('.').mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
    }

    /** True when [raw] looks like a version the app can compare. */
    fun isValid(raw: String): Boolean = segments(raw).isNotEmpty()

    /**
     * Compares two names: negative when [a] is older, zero when they are the same, positive
     * when [a] is newer.
     */
    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        val length = maxOf(left.size, right.size)
        for (index in 0 until length) {
            val difference = (left.getOrNull(index) ?: 0) - (right.getOrNull(index) ?: 0)
            if (difference != 0) return difference
        }
        return 0
    }

    /**
     * Whether [candidate] is a genuinely newer version than [current].
     *
     * A candidate that is not a version at all is **not** an update — better to say nothing
     * than to offer something meaningless.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        if (!isValid(candidate)) return false
        return compare(candidate, current) > 0
    }
}
