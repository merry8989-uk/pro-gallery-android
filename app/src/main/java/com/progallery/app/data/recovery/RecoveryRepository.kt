package com.progallery.app.data.recovery

import com.progallery.app.data.local.dao.RecoveryHistoryDao
import com.progallery.app.data.local.entity.RecoveryHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the results of the last scan so a preview screen can resolve a result by
 * its id, and owns the export history.
 *
 * The scanning itself lives in [RecoveryScanner] rather than being duplicated
 * here.
 */
@Singleton
class RecoveryRepository @Inject constructor(
    private val scanner: RecoveryScanner,
    private val historyDao: RecoveryHistoryDao,
) {

    private var lastResults: List<RecoveryResult> = emptyList()

    fun quickScan(): Flow<RecoveryScanState> = scanner.quickScan()

    fun openSource(result: RecoveryResult) = scanner.openSource(result)

    /** Kept in memory only; a scan's results are not worth persisting. */
    fun remember(results: List<RecoveryResult>) {
        lastResults = results
    }

    fun resultById(id: String): RecoveryResult? = lastResults.firstOrNull { it.id == id }

    fun observeHistory(): Flow<List<RecoveryHistoryEntity>> = historyDao.observeAll()

    suspend fun recentHistory(limit: Int = 50): List<RecoveryHistoryEntity> =
        withContext(Dispatchers.IO) { historyDao.recent(limit) }

    /**
     * Records an attempt. Only a human-readable destination label is stored — never
     * the Storage Access Framework tree URI, which carries a permission grant.
     */
    suspend fun record(
        result: RecoveryResult,
        destinationLabel: String,
        outcome: RecoveryExportOutcome,
    ) = withContext(Dispatchers.IO) {
        historyDao.add(
            RecoveryHistoryEntity(
                displayName = result.item.displayName,
                sourceLabel = result.source.label,
                destinationLabel = destinationLabel,
                outcome = when (outcome) {
                    is RecoveryExportOutcome.Success -> "Exported"
                    is RecoveryExportOutcome.Failed -> "Failed"
                },
                detail = (outcome as? RecoveryExportOutcome.Failed)?.message,
                sizeBytes = (outcome as? RecoveryExportOutcome.Success)?.sizeBytes ?: 0L,
            ),
        )
    }

    /** Clears the history. Never touches the recovered files themselves. */
    suspend fun clearHistory() = withContext(Dispatchers.IO) { historyDao.clear() }

    /** Removes history rows older than [retentionDays]. Returns how many went. */
    suspend fun purgeHistory(retentionDays: Int = 90): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        historyDao.purgeOlderThan(cutoff)
    }
}
