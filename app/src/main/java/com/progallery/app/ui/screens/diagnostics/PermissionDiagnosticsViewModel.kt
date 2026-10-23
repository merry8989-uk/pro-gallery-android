package com.progallery.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName
import com.progallery.app.data.eventlog.LogEntry
import com.progallery.app.data.eventlog.LogFilter
import com.progallery.app.data.eventlog.LogLevel
import com.progallery.app.data.eventlog.LogFilters
import com.progallery.app.data.eventlog.LogFormat
import com.progallery.app.data.eventlog.PendingFix
import com.progallery.app.data.eventlog.PendingFixStore
import com.progallery.app.data.eventlog.wire
import com.progallery.app.data.permissions.AccessDiagnostics
import com.progallery.app.data.permissions.AccessId
import com.progallery.app.data.permissions.AccessFixOutcome
import com.progallery.app.data.permissions.AccessRow
import com.progallery.app.data.permissions.AccessSnapshotReader
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.progallery.app.data.settings.SettingsRepository

data class PermissionDiagnosticsUiState(
    val loading: Boolean = true,
    val rows: List<AccessRow> = emptyList(),
    val headline: String = "",
    /** How many accesses are actually stopping the app. */
    val blockingCount: Int = 0,
    /** Photos and videos visible right now — the number that makes an access problem legible. */
    val mediaItemCount: Int = 0,
    /** The log, newest first, live. */
    val log: List<LogEntry> = emptyList(),
    val loggingEnabled: Boolean = true,
    val userCode: String = "",
    val filter: LogFilter = LogFilter(),
    val message: String? = null,
) {
    val filteredLog: List<LogEntry> get() = LogFilters.apply(log, filter)
    val levelCounts: Map<LogLevel, Int> get() = LogFilters.countsByLevel(log)
}

@HiltViewModel
class PermissionDiagnosticsViewModel @Inject constructor(
    private val reader: AccessSnapshotReader,
    private val eventLog: EventLogStore,
    private val pendingFix: PendingFixStore,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PermissionDiagnosticsUiState())
    val state: StateFlow<PermissionDiagnosticsUiState> = _state.asStateFlow()

    /** How many times a fix has been asked for this session. Three is a loop; two is trying again. */
    private var fixAttempts = 0

    init {
        // The log is shown from the store's own flow, so an event appears on the screen as it is
        // written rather than when someone remembers to refresh.
        viewModelScope.launch {
            eventLog.entries.collect { entries ->
                _state.update { it.copy(log = entries.asReversed()) }
            }
        }
        viewModelScope.launch {
            eventLog.enabled.collect { enabled -> _state.update { it.copy(loggingEnabled = enabled) } }
        }
        viewModelScope.launch {
            _state.update { it.copy(userCode = eventLog.code()) }
        }
        refresh()
    }

    /**
     * Re-reads the device state, and **answers any open fix attempt**.
     *
     * This is the half that was missing: the app recorded that the user asked for a fix and never
     * recorded what came of it, so a report could show 71 attempts and no explanation. Reading
     * the state again is exactly the moment the answer is known.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val snapshot = reader.read()
            val rows = AccessDiagnostics.rows(snapshot)
            val blocking = AccessDiagnostics.blocking(rows)

            _state.update {
                it.copy(
                    loading = false,
                    rows = rows,
                    headline = AccessDiagnostics.headline(rows),
                    blockingCount = blocking.size,
                    mediaItemCount = snapshot.mediaItemCount,
                )
            }

            answerOpenAttempt(rows, snapshot.mediaItemCount)

            // What the app can reach, with the count — "granted, 412 items" and "granted, 0
            // items" are the same state and completely different problems.
            val visual = rows.firstOrNull { it.id == AccessId.MEDIA_VISUAL }?.state
            if (visual != null) {
                // A refusal is a security event in its own right: the app asked for access and
                // did not get it, which is exactly what a security log is for.
                if (visual.wire() != "granted") {
                    record(
                        event = EventName.PERMISSION_DENIED,
                        details = mapOf("permission" to "photos_and_videos", "state" to visual.wire()),
                    )
                }
                record(
                    event = EventName.PERMISSION_STATE_RESOLVED,
                    details = mapOf(
                        "permission" to "photos_and_videos",
                        "state" to visual.wire(),
                        "media_item_count" to snapshot.mediaItemCount.toString(),
                    ),
                )
            }
        }
    }

    /** Records that the user went to fix something, and keeps the attempt open until answered. */
    fun noteFixAttempt(row: AccessRow) {
        viewModelScope.launch {
            val attemptId = newAttemptId()
            val action = row.fix.name.lowercase()
            // Written down before the tap is acted on, because the app can be killed while the
            // user is in Settings — which is precisely when the answer arrives.
            pendingFix.put(PendingFix(attemptId, row.id, row.state, action))

            // The same permission being asked for again and again is what being stuck looks like,
            // and it is the one signal that says the fix on offer is not the fix that is needed.
            fixAttempts++
            if (fixAttempts == LOOP_THRESHOLD) {
                record(
                    event = EventName.PERMISSION_LOOP,
                    details = mapOf("permission" to row.id.name.lowercase(), "attempts" to fixAttempts.toString()),
                )
            }
            record(
                event = EventName.ACCESS_FIX_REQUESTED,
                attemptId = attemptId,
                details = mapOf(
                    "permission" to row.id.name.lowercase(),
                    "state_before" to row.state.wire(),
                    "action_taken" to action,
                ),
            )
        }
    }

    private companion object {
        /** Three attempts is a loop; two is a person trying again. */
        const val LOOP_THRESHOLD = 3
    }

    fun setFilter(filter: LogFilter) = _state.update { it.copy(filter = filter) }

    /**
     * Grants or withdraws the consent to record anything, from the screen that shows the recording.
     *
     * The screen used to tell the person to go to Settings to turn this on, which is a strange thing
     * for the one screen that displays the data to say. This is the same setting either way — there
     * is one consent, not two — it is simply offered where the question is actually being asked.
     *
     * The change is recorded **before** it is applied when consent is being withdrawn, because after
     * it is applied nothing more can be written. Turning it on is recorded after, for the same
     * reason in reverse.
     */
    fun setUsageConsent(granted: Boolean) {
        viewModelScope.launch {
            if (!granted) {
                record(
                    EventName.CONSENT_CHANGED,
                    details = mapOf("consent" to "usage_data", "granted" to "false"),
                )
                settingsRepository.update { it.copy(usageDataEnabled = false) }
            } else {
                settingsRepository.update { it.copy(usageDataEnabled = true) }
                record(
                    EventName.CONSENT_CHANGED,
                    details = mapOf("consent" to "usage_data", "granted" to "true"),
                )
            }
            _state.update { it.copy(loggingEnabled = granted) }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            // Recorded before the clear, because after it there is nowhere to record it — and
            // "the log was emptied" is exactly the kind of thing an audit trail is for.
            val removed = _state.value.log.size
            record(EventName.DATA_DELETED, details = mapOf("what" to "diagnostics_log", "events" to removed.toString()))
            eventLog.clear()
            _state.update { it.copy(message = "The log was cleared.") }
        }
    }

    /** Renders the filtered log, for the export and for attaching to a message. */
    suspend fun renderLog(format: LogFormat): String = eventLog.export(format)

    fun logFileFor(format: LogFormat, onReady: (File) -> Unit, onFailed: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { eventLog.exportFile(format) }.fold(
                onSuccess = { file ->
                    record(EventName.DATA_EXPORTED, details = mapOf("what" to "diagnostics_log", "format" to format.label))
                    onReady(file)
                },
                onFailure = { onFailed(it.message ?: "The log could not be written.") },
            )
        }
    }

    /**
     * Writes the filtered log to a place the user chose.
     *
     * The write happens here rather than in the screen so it runs on the view model's scope: a
     * coroutine started from a composable outlives the composition and leaks.
     */
    fun saveLogTo(uri: android.net.Uri, format: LogFormat, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val body = renderLog(format)
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream -> stream.write(body.toByteArray()) }
                true
            }.getOrDefault(false)
            if (written) {
                record(EventName.DATA_EXPORTED, details = mapOf("what" to "diagnostics_log", "format" to format.label))
            }
            onDone(written)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---- internals ----

    private suspend fun answerOpenAttempt(rows: List<AccessRow>, mediaItemCount: Int) {
        val open = pendingFix.take() ?: return
        val after = rows.firstOrNull { it.id == open.accessId }?.state ?: return
        record(
            event = EventName.ACCESS_FIX_RESULT,
            attemptId = open.attemptId,
            details = mapOf(
                "permission" to open.accessId.name.lowercase(),
                "action_taken" to open.actionTaken,
                "state_before" to open.stateBefore.wire(),
                "state_after" to after.wire(),
                "outcome" to AccessFixOutcome.of(open.stateBefore, after).wire,
                "media_item_count" to mediaItemCount.toString(),
            ),
        )
    }

    private suspend fun record(
        event: EventName,
        details: Map<String, String> = emptyMap(),
        attemptId: String? = null,
    ) {
        eventLog.record(
            LogEntry(
                event = event,
                timestampMillis = System.currentTimeMillis(),
                userCode = eventLog.code(),
                sessionId = eventLog.session(),
                attemptId = attemptId,
                screen = "AccessDiagnostics",
                details = details,
            ),
        )
    }

    private fun newAttemptId(): String {
        val bytes = ByteArray(4).also { SecureRandom().nextBytes(it) }
        return "att_" + bytes.joinToString("") { "%02x".format(it) }
    }
}
