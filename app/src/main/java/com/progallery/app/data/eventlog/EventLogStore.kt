package com.progallery.app.data.eventlog

import android.content.Context
import android.os.Build
import com.progallery.app.BuildConfig
import com.progallery.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The diagnostics log: one JSON object per event, on the device, and nowhere else.
 *
 * **Nothing here is uploaded.** There is no endpoint and no transmission — the log exists so the
 * person holding the phone can see what happened and choose to send it on. That is also why the
 * consent switch governs it: with `usageDataEnabled` off, nothing is written at all, and the
 * screen says so rather than showing an empty list that looks broken.
 */
@Singleton
class EventLogStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userCodeStore: UserCodeStore,
    private val settingsRepository: SettingsRepository,
) {

    private companion object {
        /** Retention, as the shared standard defines it: seven days, five hundred events. */
        const val KEEP_EVENTS = 500
        const val KEEP_DAYS = 7L
        const val FILE = "diagnostics/events.jsonl"
    }

    private val mutex = Mutex()

    /** The live view reads this, which is what makes the screen real-time. */
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var loaded = false
    private var cachedCode: String? = null
    private var cachedSession: String? = null

    private fun file(): File = File(context.filesDir, FILE)

    /**
     * Records an event, filling in the session context.
     *
     * The convenience exists so a call site cannot forget the parts that make an event joinable —
     * the code, the session and the timestamp. Building the entry by hand at thirty call sites is
     * thirty chances to leave one out, and an event with no session cannot be followed through
     * anything.
     *
     * It is a no-op when logging is switched off, which is the consent gate: the check lives here
     * rather than at each call site, because a gate each caller must remember to respect is a gate
     * that will be missed.
     */
    suspend fun log(
        event: EventName,
        screen: String? = null,
        attemptId: String? = null,
        details: Map<String, String> = emptyMap(),
    ) {
        if (!settingsRepository.current().usageDataEnabled) return
        record(
            LogEntry(
                event = event,
                timestampMillis = System.currentTimeMillis(),
                userCode = code(),
                sessionId = session(),
                attemptId = attemptId,
                screen = screen,
                details = details,
            ),
        )
    }

    suspend fun record(entry: LogEntry) = withContext(Dispatchers.IO) {
        if (!settingsRepository.current().usageDataEnabled) {
            _enabled.value = false
            return@withContext
        }
        _enabled.value = true

        mutex.withLock {
            loadOnce()
            val now = entry.timestampMillis
            val kept = LogRetention.prune(
                entries = _entries.value + entry,
                nowMillis = now,
                keepEvents = KEEP_EVENTS,
                keepDays = KEEP_DAYS,
            )
            _entries.value = kept
            write(kept)
        }
    }

    suspend fun code(): String = withContext(Dispatchers.IO) {
        cachedCode ?: userCodeStore.code().also { cachedCode = it }
    }

    /**
     * A new id per app launch, so one session's events group together and two sessions do not.
     */
    suspend fun session(): String = withContext(Dispatchers.IO) {
        cachedSession ?: run {
            val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
            "sess_" + bytes.joinToString("") { "%02x".format(it) }
        }.also { cachedSession = it }
    }

    suspend fun context(): LogContext = buildContext()

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _entries.value = emptyList()
            runCatching { file().delete() }
            loaded = true
        }
    }

    suspend fun export(format: LogFormat): String = withContext(Dispatchers.IO) {
        val snapshot = read()
        LogExport.render(snapshot, context(), format)
    }

    /** Writes the export into the app's cache and returns it, for attaching to a message. */
    suspend fun exportFile(format: LogFormat): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "shared/diagnostics/progallery-log.${format.extension}")
        runCatching { target.parentFile?.mkdirs() }
        target.writeText(export(format))
        target
    }

    suspend fun read(): List<LogEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadOnce()
            _entries.value
        }
    }

    private suspend fun loadOnce() {
        if (loaded) return
        val parsed = runCatching {
            file().takeIf { it.exists() }
                ?.readLines()
                ?.mapNotNull(LogLine::decode)
                .orEmpty()
        }.getOrDefault(emptyList())
        _entries.value = parsed
        loaded = true
    }

    private fun write(entries: List<LogEntry>) {
        val body = buildString {
            entries.forEach { append(LogLine.encode(it, buildContext())).append('\n') }
        }
        runCatching {
            file().parentFile?.mkdirs()
            file().writeText(body)
        }
    }

    /**
     * The session context, built in one place so the exported file and the live view cannot
     * disagree about which device they came from.
     *
     * Network is left empty rather than asking for ACCESS_NETWORK_STATE: a log field is not worth
     * a permission, and the schema marks this one optional.
     */
    private fun buildContext(): LogContext {
        val density = context.resources.configuration.smallestScreenWidthDp
        return LogContext(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = if (density >= 600) "tablet" else "mobile",
            releaseChannel = if (BuildConfig.DEBUG) "beta" else "production",
            network = null,
        )
    }
}
