package com.progallery.app.data.transfer

/**
 * How far a copy has got, and whether that can be said at all.
 *
 * A content URI does not have to report a size — a document from a provider, a stream, a file whose
 * length the provider will not tell — and a progress bar that guessed would be worse than none. So
 * there are three answers rather than two: a fraction, **nothing yet**, and **no total to compare
 * against**.
 */
sealed interface CopyProgress {

    /** Bytes copied so far. */
    val copied: Long

    /** A copy that has started but has nothing to show yet. */
    data object Starting : CopyProgress {
        override val copied: Long get() = 0
    }

    /** A copy whose total is known, so a bar can be drawn. */
    data class Known(override val copied: Long, val total: Long) : CopyProgress {
        /** From 0 to 1, clamped, and 1 when the total is nonsense rather than dividing by it. */
        val fraction: Float
            get() = when {
                total <= 0L -> 0f
                copied <= 0L -> 0f
                copied >= total -> 1f
                else -> (copied.toDouble() / total.toDouble()).toFloat()
            }
    }

    /**
     * A copy whose total is not known.
     *
     * The screen shows bytes copied and says the total is unknown, rather than drawing a bar that
     * means nothing. This is not a failure — it is a provider that does not say how big its document
     * is, which is allowed.
     */
    data class UnknownTotal(override val copied: Long) : CopyProgress

    companion object {
        /**
         * The right answer for a copy that has got [copied] bytes in, when the source claims to be
         * [total] bytes.
         *
         * A total of zero or less is treated as no total at all: `0` from a provider means "I do not
         * know", not "this file is empty", because a file with nothing in it is copied before anyone
         * can draw a bar for it.
         */
        fun of(copied: Long, total: Long): CopyProgress = when {
            total <= 0L -> UnknownTotal(copied)
            else -> Known(copied, total)
        }
    }
}
