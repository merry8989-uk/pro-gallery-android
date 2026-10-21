package com.progallery.app.data.memories

import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.data.repository.MediaRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.progallery.app.data.usage.PerfOperation
import com.progallery.app.data.usage.UsageStore

/**
 * Today's memories, in one place.
 *
 * The screen and the daily notification ask the same question, so they ask it here rather than
 * each assembling the answer — otherwise the two could disagree about what today holds, and a
 * notification would announce something the screen then did not show.
 */
@Singleton
class MemoriesRepository @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val usageStore: UsageStore,
) {

    /**
     * The pictures worth showing again on [today].
     *
     * Reads the library once. A memory is a question about dates, and MediaStore cannot answer
     * "pictures taken on 5 April in any earlier year" as a query — the dates are not indexed
     * that way — so the matching is done here, in memory, from the same list the rest of the
     * app already reads.
     */
    suspend fun forDate(today: LocalDate = LocalDate.now()): Memories = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val library = mediaRepository.snapshot(
            kinds = MediaType.entries.filter { it != MediaType.ALL }.toSet(),
            sort = SortOrder.DATE_NEWEST,
            query = "",
        )
        val memories = MemoriesEngine.forDate(library, today)
        usageStore.recordPerformance(PerfOperation.MEMORIES, System.currentTimeMillis() - startedAt)
        memories
    }
}
