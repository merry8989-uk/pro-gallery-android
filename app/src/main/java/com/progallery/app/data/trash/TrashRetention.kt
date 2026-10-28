package com.progallery.app.data.trash

/**
 * When something in the trash has waited long enough.
 *
 * Small and pure on purpose. The purge worker retries when a delete fails, which is the right
 * behaviour — but "which rows are expired" is arithmetic, and arithmetic that decides whether a
 * person's file is deleted deserves a test rather than trust.
 */
object TrashRetention {

    /** The default: a month. */
    const val DEFAULT_DAYS = 30

    /**
     * The moment before which a row is expired.
     *
     * A retention of **zero or less is clamped to one day** rather than to "everything". A caller
     * passing 0 almost certainly means "purge now", but reading it literally would delete a file the
     * instant it was put in the trash — which is not a trash, and is not recoverable.
     */
    fun cutoff(retentionDays: Int, nowMillis: Long): Long {
        val days = retentionDays.coerceAtLeast(1)
        return nowMillis - days * 24L * 60L * 60L * 1000L
    }

    /** Whether a row put in the trash at [trashedAtMillis] has expired by [nowMillis]. */
    fun isExpired(trashedAtMillis: Long, retentionDays: Int, nowMillis: Long): Boolean =
        trashedAtMillis < cutoff(retentionDays, nowMillis)

    /**
     * Whether a row is safe to purge **and** old enough.
     *
     * A row with no timestamp is not purged: an unknown age is not the same as an old one, and
     * deleting on a guess is how a file disappears before its time.
     */
    fun isPurgeable(trashedAtMillis: Long?, retentionDays: Int, nowMillis: Long): Boolean =
        trashedAtMillis != null && isExpired(trashedAtMillis, retentionDays, nowMillis)
}
