package com.progallery.app.data.usage

import android.content.Context
import android.os.Build
import com.progallery.app.BuildConfig
import com.progallery.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the usage counts on the device.
 *
 * **Nothing leaves the device.** This is a local record, shown to the user in Diagnostics and
 * exportable by them; there is no upload, no endpoint and no background transmission anywhere
 * in this class or the ones it uses.
 *
 * Every write is refused when the user has switched recording off, and the stored file can be
 * read and deleted from the same screen that shows it.
 */
@Singleton
class UsageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private companion object {
        /** How long a day is kept. Long enough for a month of review, short enough to be small. */
        const val KEEP_DAYS = 30L
        const val DIRECTORY = "usage"
        const val FILE_NAME = "usage.txt"
    }

    private val mutex = Mutex()

    private fun file(): File = File(File(context.filesDir, DIRECTORY), FILE_NAME)

    fun profile(): DeviceProfile = DeviceProfile(
        model = Build.MODEL ?: "unknown",
        androidVersion = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME,
        appBuild = BuildConfig.VERSION_CODE,
    )

    suspend fun read(): List<UsageDay> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
    }

    suspend fun digest(today: LocalDate = LocalDate.now()): UsageDigest =
        UsageReview.digest(read(), today)

    /** The file as the user would see it, for export and for the on-screen view. */
    suspend fun asText(): String = withContext(Dispatchers.IO) {
        mutex.withLock { UsageCodec.encode(profile(), readUnlocked()) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { file().delete(); Unit }
    }

    // UsageDay is immutable, so each recording returns a new one. That is deliberate: a day
    // that could be mutated in place could be half-updated when a write fails.
    suspend fun recordEvent(event: UsageEvent) = mutate { day ->
        day.copy(events = day.events + (event to (day.events[event] ?: 0) + 1))
    }

    suspend fun recordFeature(feature: FeatureId) = mutate { day ->
        day.copy(features = day.features + (feature to (day.features[feature] ?: 0) + 1))
    }

    suspend fun recordPerformance(operation: PerfOperation, millis: Long) = mutate { day ->
        val totals = (day.performance[operation] ?: PerfTotals()).plus(millis)
        day.copy(performance = day.performance + (operation to totals))
    }

    /**
     * Records a crash, reduced to what is safe — see [CrashSanitizer].
     *
     * A crash is recorded **even when the user has switched usage recording off**, and this is
     * deliberate: the diagnostic log is a separate, user-visible thing that exists so a fault
     * can be reported at all. It is listed in the app's own wording as such, and it is deleted
     * by the same button.
     */
    suspend fun recordCrash(throwable: Throwable) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val days = readUnlocked().toMutableList()
            val record = CrashSanitizer.record(throwable, BuildConfig.VERSION_NAME)
            replaceToday(days) { day -> day.copy(crashes = day.crashes + record) }
            write(days)
        }
    }

    private suspend fun mutate(block: (UsageDay) -> UsageDay) = withContext(Dispatchers.IO) {
        if (!settingsRepository.current().usageDataEnabled) return@withContext

        mutex.withLock {
            val days = readUnlocked().toMutableList()
            replaceToday(days, block)
            write(days)
        }
    }

    /** Replaces today's entry with [block] applied to it, adding the day if it is new. */
    private fun replaceToday(days: MutableList<UsageDay>, block: (UsageDay) -> UsageDay) {
        val today = LocalDate.now()
        val index = days.indexOfFirst { it.day == today }
        if (index < 0) {
            days.add(block(UsageDay(day = today)))
        } else {
            days[index] = block(days[index])
        }
    }

    private fun write(days: List<UsageDay>) {
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS)
        val kept = days.filter { !it.day.isBefore(cutoff) && !it.day.isAfter(LocalDate.now()) }

        val target = file()
        target.parentFile?.mkdirs()
        // Written to a temporary file and moved into place, so an interrupted write cannot
        // leave a half-written record behind.
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(UsageCodec.encode(profile(), kept))
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun readUnlocked(): List<UsageDay> {
        val target = file()
        if (!target.exists()) return emptyList()
        return runCatching { UsageCodec.decode(target.readText()).second }.getOrDefault(emptyList())
    }
}
