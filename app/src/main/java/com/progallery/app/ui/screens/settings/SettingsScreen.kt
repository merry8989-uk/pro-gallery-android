package com.progallery.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.settings.AppSettings
import com.progallery.app.data.settings.GridSize
import com.progallery.app.data.settings.LayoutMode
import com.progallery.app.data.settings.PerformanceMode
import com.progallery.app.data.settings.ThemeMode
import com.progallery.app.data.model.SortOrder
import com.progallery.app.util.DefaultApps

/** One renderable settings row. */
private sealed interface SettingsRow {
    val title: String
    val subtitle: String?
    val advanced: Boolean
    val section: String

    data class Switch(
        override val title: String,
        override val subtitle: String? = null,
        override val advanced: Boolean = false,
        override val section: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val recommended: Boolean = false,
        val experimental: Boolean = false,
    ) : SettingsRow

    data class Choice(
        override val title: String,
        override val subtitle: String? = null,
        override val advanced: Boolean = false,
        override val section: String,
        val options: List<String>,
        val selected: String,
        val onSelect: (String) -> Unit,
    ) : SettingsRow

    data class Slider(
        override val title: String,
        override val subtitle: String? = null,
        override val advanced: Boolean = false,
        override val section: String,
        val value: Int,
        val range: IntRange,
        val onChange: (Int) -> Unit,
    ) : SettingsRow

    data class Action(
        override val title: String,
        override val subtitle: String? = null,
        override val advanced: Boolean = false,
        override val section: String,
        val label: String,
        val onClick: () -> Unit,
    ) : SettingsRow

    data class Info(
        override val title: String,
        override val subtitle: String,
        override val advanced: Boolean = false,
        override val section: String,
    ) : SettingsRow
}

private val SECTION_ORDER = listOf(
    "Scanning",
    "File types",
    "Thumbnails & result cards",
    "Appearance",
    "Default app",
    "Detection",
    "Scan limits",
    "Performance",
    "Diagnostics & experimental",
)

/** Bounded choices, so the size range can never be set to something absurd. */
private val MIN_SIZE_OPTIONS = listOf(
    "1 B" to 1L,
    "1 KB" to 1_024L,
    "100 KB" to 102_400L,
    "1 MB" to 1_048_576L,
)

private val MAX_SIZE_OPTIONS = listOf(
    "Unlimited" to Long.MAX_VALUE,
    "100 MB" to 104_857_600L,
    "1 GB" to 1_073_741_824L,
    "10 GB" to 10_737_418_240L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rootStatus by viewModel.rootStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val rows = buildRows(
        settings = settings,
        rootStatus = rootStatus,
        update = viewModel::update,
        onCheckRoot = viewModel::checkRootStatus,
        onOpenDefaultApps = { DefaultApps.openDefaultAppsSettings(context) },
    )

    val visible = rows.filter { row ->
        (settings.advancedMode || !row.advanced) &&
            (query.isBlank() ||
                row.title.contains(query, ignoreCase = true) ||
                row.subtitle?.contains(query, ignoreCase = true) == true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmReset = true }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Restore defaults")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SummaryCard(settings)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search settings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AdvancedMasterSwitch(
                    enabled = settings.advancedMode,
                    onChange = { value -> viewModel.update { it.copy(advancedMode = value) } },
                )
            }

            SECTION_ORDER.forEach { section ->
                val sectionRows = visible.filter { it.section == section }
                if (sectionRows.isNotEmpty()) {
                    item(key = "header-$section") {
                        Text(
                            text = section.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(sectionRows, key = { "$section-${it.title}" }) { row ->
                        RowCard(row)
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restore default settings?") },
            text = { Text("Every setting returns to its default. Your photos, videos, albums, favourites and trash are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetToDefaults()
                    confirmReset = false
                }) { Text("Restore defaults") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

/** The rows, and the only place each toggle's meaning is defined. */
private fun buildRows(
    settings: AppSettings,
    rootStatus: String?,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onCheckRoot: () -> Unit,
    onOpenDefaultApps: () -> Unit,
): List<SettingsRow> = buildList {
    // ---- Scanning -----------------------------------------------------
    add(
        SettingsRow.Switch(
            title = "Quick scan (MediaStore)",
            subtitle = "Index photos, videos and audio the system already knows about.",
            section = "Scanning",
            checked = settings.quickScanEnabled,
            recommended = true,
            onCheckedChange = { value -> update { it.copy(quickScanEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Deep media scan",
            subtitle = "Walk folders you grant and look inside them for files.",
            section = "Scanning",
            checked = settings.deepScanEnabled,
            onCheckedChange = { value -> update { it.copy(deepScanEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Folder (SAF) access",
            subtitle = "Let you pick folders to scan. Turning this off never revokes access you already granted.",
            section = "Scanning",
            checked = settings.safScanEnabled,
            onCheckedChange = { value -> update { it.copy(safScanEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Duplicate detection",
            subtitle = "Hash files of the same size to find real duplicates. Slow on large libraries.",
            section = "Scanning",
            advanced = true,
            checked = settings.duplicateDetectionEnabled,
            onCheckedChange = { value -> update { it.copy(duplicateDetectionEnabled = value) } },
        ),
    )

    // ---- File types ---------------------------------------------------
    add(
        SettingsRow.Switch(
            title = "Images",
            section = "File types",
            checked = settings.scanImages,
            onCheckedChange = { value -> update { it.copy(scanImages = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Videos",
            subtitle = "Off also stops video thumbnail generation.",
            section = "File types",
            checked = settings.scanVideos,
            onCheckedChange = { value -> update { it.copy(scanVideos = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Audio",
            section = "File types",
            checked = settings.scanAudio,
            onCheckedChange = { value -> update { it.copy(scanAudio = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Documents",
            section = "File types",
            checked = settings.scanDocuments,
            onCheckedChange = { value -> update { it.copy(scanDocuments = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Archives",
            section = "File types",
            checked = settings.scanArchives,
            onCheckedChange = { value -> update { it.copy(scanArchives = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Other / unknown types",
            section = "File types",
            checked = settings.scanOtherFiles,
            onCheckedChange = { value -> update { it.copy(scanOtherFiles = value) } },
        ),
    )

    // ---- Thumbnails ---------------------------------------------------
    add(
        SettingsRow.Switch(
            title = "Generate thumbnails",
            subtitle = "Off shows a file-type icon instead — results never go blank.",
            section = "Thumbnails & result cards",
            checked = settings.generateThumbnails,
            onCheckedChange = { value -> update { it.copy(generateThumbnails = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Show file names",
            section = "Thumbnails & result cards",
            checked = settings.showFileNames,
            onCheckedChange = { value -> update { it.copy(showFileNames = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Show file size",
            section = "Thumbnails & result cards",
            checked = settings.showFileSize,
            onCheckedChange = { value -> update { it.copy(showFileSize = value) } },
        ),
    )

    // ---- Appearance ---------------------------------------------------
    add(
        SettingsRow.Choice(
            title = "Theme",
            section = "Appearance",
            options = ThemeMode.entries.map { it.label },
            selected = settings.themeMode.label,
            onSelect = { label ->
                update { it.copy(themeMode = ThemeMode.entries.first { m -> m.label == label }) }
            },
        ),
    )
    add(
        SettingsRow.Choice(
            title = "Default layout",
            section = "Appearance",
            options = LayoutMode.entries.map { it.label },
            selected = settings.defaultLayout.label,
            onSelect = { label ->
                update { it.copy(defaultLayout = LayoutMode.entries.first { m -> m.label == label }) }
            },
        ),
    )
    add(
        SettingsRow.Choice(
            title = "Grid size",
            section = "Appearance",
            options = GridSize.entries.map { it.label },
            selected = settings.gridSize.label,
            onSelect = { label ->
                update { it.copy(gridSize = GridSize.entries.first { m -> m.label == label }) }
            },
        ),
    )
    add(
        SettingsRow.Choice(
            title = "Default sorting",
            section = "Appearance",
            options = SortOrder.entries.map { it.label },
            selected = settings.defaultSort.label,
            onSelect = { label ->
                update { it.copy(defaultSort = SortOrder.entries.first { m -> m.label == label }) }
            },
        ),
    )

    // ---- Default app --------------------------------------------------
    add(
        SettingsRow.Action(
            title = "Set as default gallery",
            subtitle = "Android only lets you choose a default app; this opens the screen where you pick it.",
            section = "Default app",
            label = "Open default apps",
            onClick = onOpenDefaultApps,
        ),
    )

    // ---- Detection (advanced) -----------------------------------------
    add(
        SettingsRow.Switch(
            title = "Detect files by signature",
            subtitle = "Read the first bytes of a file instead of trusting its extension.",
            section = "Detection",
            advanced = true,
            checked = settings.signatureDetectionEnabled,
            onCheckedChange = { value -> update { it.copy(signatureDetectionEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Include hidden files",
            subtitle = "Include names that start with a dot. Off by default.",
            section = "Detection",
            advanced = true,
            checked = settings.includeHiddenFiles,
            onCheckedChange = { value -> update { it.copy(includeHiddenFiles = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Include files with no extension",
            section = "Detection",
            advanced = true,
            checked = settings.includeExtensionlessFiles,
            onCheckedChange = { value -> update { it.copy(includeExtensionlessFiles = value) } },
        ),
    )
    add(
        SettingsRow.Info(
            title = "What signature detection can and cannot do",
            subtitle = "It identifies a file's real format. It cannot recreate data that is no longer accessible.",
            section = "Detection",
            advanced = true,
        ),
    )

    // ---- Limits (advanced) --------------------------------------------
    add(
        SettingsRow.Slider(
            title = "Bytes read for identification",
            subtitle = "${settings.signatureReadBytes} bytes",
            section = "Scan limits",
            advanced = true,
            value = settings.signatureReadBytes,
            range = 64..65_536,
            onChange = { value -> update { it.copy(signatureReadBytes = value) } },
        ),
    )
    add(
        SettingsRow.Slider(
            title = "Maximum folder depth",
            subtitle = "${settings.maxRecursionDepth} levels",
            section = "Scan limits",
            advanced = true,
            value = settings.maxRecursionDepth,
            range = 1..32,
            onChange = { value -> update { it.copy(maxRecursionDepth = value) } },
        ),
    )
    add(
        SettingsRow.Slider(
            title = "Maximum results",
            subtitle = "${settings.maxScanResults} files",
            section = "Scan limits",
            advanced = true,
            value = settings.maxScanResults,
            range = 50..50_000,
            onChange = { value -> update { it.copy(maxScanResults = value) } },
        ),
    )

    // ---- File size range (advanced) ------------------------------------
    add(
        SettingsRow.Info(
            title = "File size range",
            subtitle = "Every file from 1 byte upward is shown, with no upper limit — APKs, images, " +
                "documents, videos. Size never hides a file. Current range: ${settings.sizeRangeLabel}.",
            section = "Scan limits",
            advanced = true,
        ),
    )
    add(
        SettingsRow.Choice(
            title = "Minimum file size",
            subtitle = "Only applies when a file's size can be read; an unreadable size is never " +
                "treated as too small.",
            section = "Scan limits",
            advanced = true,
            options = MIN_SIZE_OPTIONS.map { it.first },
            selected = MIN_SIZE_OPTIONS.firstOrNull { it.second == settings.minFileSizeBytes }?.first
                ?: MIN_SIZE_OPTIONS.first().first,
            onSelect = { label ->
                MIN_SIZE_OPTIONS.firstOrNull { it.first == label }?.let { option ->
                    update { it.copy(minFileSizeBytes = option.second) }
                }
            },
        ),
    )
    add(
        SettingsRow.Choice(
            title = "Maximum file size",
            subtitle = "Unlimited by default.",
            section = "Scan limits",
            advanced = true,
            options = MAX_SIZE_OPTIONS.map { it.first },
            selected = MAX_SIZE_OPTIONS.firstOrNull { option ->
                settings.isMaxSizeUnlimited && option.second == Long.MAX_VALUE ||
                    option.second == settings.maxFileSizeBytes
            }?.first ?: MAX_SIZE_OPTIONS.first().first,
            onSelect = { label ->
                MAX_SIZE_OPTIONS.firstOrNull { it.first == label }?.let { option ->
                    update { it.copy(maxFileSizeBytes = option.second) }
                }
            },
        ),
    )

    // ---- Performance (advanced) ---------------------------------------
    add(
        SettingsRow.Choice(
            title = "Performance mode",
            section = "Performance",
            advanced = true,
            options = PerformanceMode.entries.map { it.label },
            selected = settings.performanceMode.label,
            onSelect = { label ->
                update {
                    it.copy(performanceMode = PerformanceMode.entries.first { m -> m.label == label })
                }
            },
        ),
    )
    add(
        SettingsRow.Slider(
            title = "Concurrent scan tasks",
            subtitle = "${settings.maxConcurrentTasks} at a time",
            section = "Performance",
            advanced = true,
            value = settings.maxConcurrentTasks,
            range = 1..16,
            onChange = { value -> update { it.copy(maxConcurrentTasks = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Pause scanning in the background",
            section = "Performance",
            advanced = true,
            checked = settings.pauseWhenBackgrounded,
            onCheckedChange = { value -> update { it.copy(pauseWhenBackgrounded = value) } },
        ),
    )

    // ---- Diagnostics & experimental -----------------------------------
    add(
        SettingsRow.Switch(
            title = "Show root status",
            subtitle = "Read-only check for a su binary. This app never requests root.",
            section = "Diagnostics & experimental",
            advanced = true,
            checked = settings.showRootStatus,
            onCheckedChange = { value -> update { it.copy(showRootStatus = value) } },
        ),
    )
    if (settings.showRootStatus) {
        add(
            SettingsRow.Action(
                title = "Root status",
                subtitle = rootStatus ?: "Not checked yet.",
                section = "Diagnostics & experimental",
                advanced = true,
                label = "Check",
                onClick = onCheckRoot,
            ),
        )
        add(
            SettingsRow.Info(
                title = "Root recovery is not implemented",
                subtitle = "Reading raw storage bypasses Android's security model and cannot honestly promise recovery of " +
                    "TRIMmed or encrypted data, so this app does not do it.",
                section = "Diagnostics & experimental",
                advanced = true,
            ),
        )
    }
    add(
        SettingsRow.Switch(
            title = "Experimental detection",
            subtitle = "Reserved for detection methods that are implemented and tested. Enabling it does not unlock anything unverified.",
            section = "Diagnostics & experimental",
            advanced = true,
            experimental = true,
            checked = settings.experimentalMethodsEnabled,
            onCheckedChange = { value -> update { it.copy(experimentalMethodsEnabled = value) } },
        ),
    )
}

@Composable
private fun SummaryCard(settings: AppSettings) {
    Surface(
        tonalElevation = 2.dp,
        color = if (settings.scanCanStart) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Active configuration", style = MaterialTheme.typography.titleSmall)
            Text(
                text = settings.summary(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (!settings.scanCanStart) {
                Text(
                    text = if (!settings.anyScanModeEnabled) {
                        "Scanning is disabled: no scan method is on. Enable Quick scan or Deep media scan."
                    } else {
                        "Scanning is disabled: every file category is off. Enable at least one file type."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AdvancedMasterSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Surface(tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Advanced mode", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Shows technical options. Leave off for the simple set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun RowCard(row: SettingsRow) {
    Surface(tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            when (row) {
                is SettingsRow.Switch -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge)
                            if (row.recommended) Label("Recommended")
                            if (row.experimental) Label("Experimental")
                        }
                        row.subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    Switch(checked = row.checked, onCheckedChange = row.onCheckedChange)
                }

                is SettingsRow.Choice -> Column {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge)
                    row.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.options.forEach { option ->
                            FilterChip(
                                onClick = { row.onSelect(option) },
                                selected = row.selected == option,
                                label = { Text(option) },
                            )
                        }
                    }
                }

                is SettingsRow.Slider -> Column {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge)
                    row.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = row.value.toFloat(),
                        onValueChange = { row.onChange(it.toInt()) },
                        valueRange = row.range.first.toFloat()..row.range.last.toFloat(),
                    )
                }

                is SettingsRow.Action -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.bodyLarge)
                        row.subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    TextButton(onClick = row.onClick) { Text(row.label) }
                }

                is SettingsRow.Info -> Column {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = row.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp),
    )
}
