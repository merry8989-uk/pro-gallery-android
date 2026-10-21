package com.progallery.app.ui.screens.diagnostics

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.progallery.app.data.eventlog.LogEntry
import com.progallery.app.data.eventlog.LogFilter
import com.progallery.app.data.eventlog.LogFormat
import com.progallery.app.data.eventlog.LogLevel
import com.progallery.app.data.eventlog.LogLine
import com.progallery.app.util.LogMail

/**
 * The log, live, on the screen where access problems are diagnosed.
 *
 * **It reads from the store's own flow**, so an event appears as it is written — there is nothing
 * to refresh. That matters here more than anywhere: the question this screen answers is "why is
 * this stuck", and the answer is the sequence of events, which is no use a minute late.
 *
 * The filter, the export and the email are all here because a log you cannot narrow, keep or send
 * is a log that stays on one phone.
 */
@Composable
fun LogSection(
    state: PermissionDiagnosticsUiState,
    onFilter: (LogFilter) -> Unit,
    onClear: () -> Unit,
    onSave: (android.net.Uri, LogFormat) -> Unit,
    onFile: (LogFormat, (java.io.File) -> Unit, (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var exportMenu by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<LogFormat?>(null) }

    // Saving through the system picker, so the file lands where the person chooses rather than
    // somewhere they have to go looking for.
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val format = pendingSave
        pendingSave = null
        if (uri != null && format != null) onSave(uri, format)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Live log", style = MaterialTheme.typography.titleMedium)
                if (state.userCode.isNotBlank()) {
                    Text(
                        text = state.userCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (!state.loggingEnabled) {
                Text(
                    // Said plainly rather than showing an empty list, which looks broken.
                    text = "Logging is off. Turn on \u201cAnonymised usage data\u201d in Settings " +
                        "\u2192 Diagnostics to record anything. Nothing is recorded while it is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
            Text(
                text = "${state.log.size} events · ${state.mediaItemCount} photos and videos " +
                    "visible. Newest first. Nothing here leaves the device unless you send it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- the filter ----
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelChip("All", state.filter.level == null) { onFilter(state.filter.copy(level = null)) }
                LogLevel.entries.forEach { level ->
                    LevelChip(
                        label = "${level.wire} ${state.levelCounts[level] ?: 0}",
                        selected = state.filter.level == level,
                    ) { onFilter(state.filter.copy(level = if (state.filter.level == level) null else level)) }
                }
            }
            OutlinedTextField(
                value = state.filter.text.orEmpty(),
                onValueChange = { onFilter(state.filter.copy(text = it)) },
                label = { Text("Filter events") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.filter.userCode.orEmpty(),
                onValueChange = { onFilter(state.filter.copy(userCode = it)) },
                label = { Text("Device code (a prefix is enough)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- the events ----
            if (state.filteredLog.isEmpty()) {
                Text(
                    text = if (state.log.isEmpty()) {
                        "Nothing recorded yet. Open a screen, or ask for an access fix, and it " +
                            "appears here."
                    } else {
                        "No events match the filter."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.filteredLog.take(200).forEach { entry -> LogRow(entry) }
                }
            }

            // ---- actions ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onFile(LogFormat.CSV, { file ->
                            context.startActivity(
                                LogMail.send(
                                    context = context,
                                    file = file,
                                    userCode = state.userCode,
                                    eventCount = state.log.size,
                                ),
                            )
                        }, { })
                    },
                ) {
                    Icon(Icons.Filled.Mail, contentDescription = null)
                    Text("Email", modifier = Modifier.padding(start = 6.dp))
                }

                OutlinedButton(onClick = { exportMenu = true }) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text("Export", modifier = Modifier.padding(start = 6.dp))
                }

                TextButton(onClick = onClear) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("Clear", modifier = Modifier.padding(start = 6.dp))
                }
            }

            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                LogFormat.entries.forEach { format ->
                    DropdownMenuItem(
                        text = { Text("${format.label} (.${format.extension})") },
                        onClick = {
                            exportMenu = false
                            pendingSave = format
                            save.launch("progallery-diagnostics.${format.extension}")
                        },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** One event, in the order a person reads it: when, how bad, what, and the details. */
@Composable
private fun LogRow(entry: LogEntry) {
    val colour = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Text(
            text = "${LogLine.isoTimestamp(entry.timestampMillis).substringAfter('T')} " +
                "${entry.level.wire.uppercase()} ${entry.event.wire}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = colour,
        )
        if (entry.details.isNotEmpty() || entry.attemptId != null) {
            Text(
                text = buildString {
                    if (entry.attemptId != null) append(entry.attemptId).append(' ')
                    append(entry.details.entries.joinToString(" ") { (k, v) -> "$k=$v" })
                }.trim(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
