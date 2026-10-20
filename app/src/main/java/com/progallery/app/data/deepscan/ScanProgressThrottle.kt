package com.progallery.app.data.deepscan

/**
 * Decides when a scan should publish progress.
 *
 * Progress used to be published every 25 examined files. That is fine for a
 * folder full of small files, but a folder holding a few very large videos could
 * go a long time without a single update, so the screen looked frozen.
 *
 * This publishes on whichever comes first: [everyFiles] files examined, or
 * [everyMillis] elapsed. Pure, with the clock injected, so it is unit testable.
 */
class ScanProgressThrottle(
    private val everyFiles: Int = 25,
    private val everyMillis: Long = 250L,
) {

    private var lastCount = -1
    private var lastPublishedAt = 0L

    fun shouldPublish(filesExamined: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (lastCount < 0) {
            lastCount = filesExamined
            lastPublishedAt = now
            return true
        }
        val enoughFiles = filesExamined - lastCount >= everyFiles
        val enoughTime = now - lastPublishedAt >= everyMillis
        if (enoughFiles || enoughTime) {
            lastCount = filesExamined
            lastPublishedAt = now
            return true
        }
        return false
    }
}
