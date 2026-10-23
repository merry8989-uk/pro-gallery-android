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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import com.progallery.app.util.MediaFormatUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.progallery.app.data.settings.ExportDuplicatePolicy
import android.content.Intent
import com.progallery.app.BuildConfig
import com.progallery.app.data.update.UpdateCheck
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import com.progallery.app.data.update.UpdateInstallPlan
import com.progallery.app.data.update.UpdateInstallStep
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.progallery.app.data.usage.UsageDigest

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
    "Recovery & export",
    "Diagnostics & experimental",
    "About",
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
    onOpenPermissionDiagnostics: () -> Unit,
    /** Opens the scope screen, which shows what a scan would do and then runs it. */
    onRunScan: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rootStatus by viewModel.rootStatus.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val cacheMessage by viewModel.cacheMessage.collectAsStateWithLifecycle()
    val settingsMessage by viewModel.settingsMessage.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var updateDialogOpen by remember { mutableStateOf(false) }

    // The destination and the source are always chosen by the user, through the
    // official pickers — the app never picks a path on its own.
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportTo(it) } }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importFrom(it) } }
    val usagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let { viewModel.exportUsageTo(it) } }
    var usageDialogOpen by remember { mutableStateOf(false) }
    val usageDigest by viewModel.usageDigest.collectAsStateWithLifecycle()
    val diagnosticsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let { viewModel.saveDiagnosticsTo(it) } }
    val snackbarHostState = remember { SnackbarHostState() }

    // The "install unknown apps" switch is changed outside the app, so the answer is re-read
    // when the app comes back to the front. Without this the dialog would keep offering a
    // setting the user has already turned on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshInstallPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }
    LaunchedEffect(cacheMessage) {
        cacheMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeCacheMessage() }
    }
    LaunchedEffect(settingsMessage) {
        settingsMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeSettingsMessage() }
    }

    /** Shown under the cache row, so the user sees what clearing would free. */
    val cacheSizeLabel = "Using ${MediaFormatUtils.formatBytes(cacheSizeBytes)}"
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val rows = buildRows(
        settings = settings,
        rootStatus = rootStatus,
        cacheSizeLabel = cacheSizeLabel,
        update = viewModel::update,
        onCheckRoot = viewModel::checkRootStatus,
        onClearThumbnailCache = viewModel::clearThumbnailCache,
        onResetSection = viewModel::resetSection,
        onExportSettings = { exportPicker.launch("progallery-settings.json") },
        onImportSettings = { importPicker.launch(arrayOf("application/json")) },
        onSaveDiagnostics = { diagnosticsPicker.launch("progallery-diagnostics.txt") },
        onOpenPermissionDiagnostics = onOpenPermissionDiagnostics,
        onCheckForUpdates = {
            updateDialogOpen = true
            viewModel.checkForUpdates()
        },
        onOpenInstagram = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(INSTAGRAM_URL)),
                )
            }
        },
        onClearDiagnostics = viewModel::clearDiagnostics,
        onReviewUsage = {
            usageDialogOpen = true
            viewModel.loadUsageDigest()
        },
        onExportUsage = { usagePicker.launch("progallery-usage.txt") },
        onDeleteUsage = viewModel::clearUsage,
        onOpenDefaultApps = { DefaultApps.openDefaultAppsSettings(context) },
        onRunScan = onRunScan,
    )

    val visible = rows.filter { row ->
        (settings.advancedMode || !row.advanced) &&
            (query.isBlank() ||
                row.title.contains(query, ignoreCase = true) ||
                row.subtitle?.contains(query, ignoreCase = true) == true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = section.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                            )
                            // Restores this heading's rows only, so the control does
                            // exactly what the heading promises.
                            TextButton(onClick = { viewModel.resetSection(section) }) {
                                Text("Reset")
                            }
                        }
                    }
                    items(sectionRows, key = { "$section-${it.title}" }) { row ->
                        RowCard(row)
                    }
                }
            }
        }
    }

    // Rendered from the flag the row sets. Without this the row opened nothing at all: the
    // flag was set, the check ran, and no dialog was ever shown.
    if (usageDialogOpen) {
        UsageReviewDialog(
            digest = usageDigest,
            onDismiss = {
                usageDialogOpen = false
                viewModel.clearUsageDigest()
            },
        )
    }

    if (updateDialogOpen) {
        UpdateDialog(
            state = updateState,
            onDismiss = {
                updateDialogOpen = false
                viewModel.dismissUpdateCheck()
            },
            onOpen = { url ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                    )
                }
            },
            onRetry = viewModel::checkForUpdates,
            onDownload = viewModel::downloadUpdate,
            onInstall = viewModel::installDownloaded,
            onAllowInstalls = {
                // Re-reads the switch when the user comes back from the setting.
                viewModel.refreshInstallPermission()
                runCatching { context.startActivity(viewModel.installPermissionIntent()) }
            },
        )
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
    cacheSizeLabel: String,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onCheckRoot: () -> Unit,
    onClearThumbnailCache: () -> Unit,
    onResetSection: (String) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onOpenPermissionDiagnostics: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenInstagram: () -> Unit,
    onOpenDefaultApps: () -> Unit,
    onReviewUsage: () -> Unit,
    onExportUsage: () -> Unit,
    onDeleteUsage: () -> Unit,
    onRunScan: () -> Unit,
): List<SettingsRow> = buildList {
    // ---- Scanning -----------------------------------------------------
    // First in the section, because it is the answer to "so what does all this actually do?" —
    // and until now the screen described a scan and offered no way to run one.
    add(
        SettingsRow.Action(
            title = "Run a scan with the current configuration",
            subtitle = "See exactly what will be scanned — and what it will miss — before it starts.",
            section = "Scanning",
            label = "Review",
            onClick = onRunScan,
        ),
    )
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
    // The switch that makes app media a real part of the gallery rather than a separate screen.
    add(
        SettingsRow.Switch(
            title = "Show app media in the gallery",
            subtitle = "Merges the photos and videos messaging apps keep in their own folders — " +
                "WhatsApp, Telegram, Arattai, Hyped and others — into the gallery, in date order. " +
                "Off leaves the gallery as the system reports it. See App media for what was found.",
            section = "Scanning",
            checked = settings.includeAppMediaInGallery,
            onCheckedChange = { value -> update { it.copy(includeAppMediaInGallery = value) } },
        ),
    )
    add(
        SettingsRow.Info(
            title = "Why app media needs its own switch",
            subtitle = "Those files sit under Android/media, which the system does not hand to " +
                "other apps in its index. The app finds them itself when you scan Android media, " +
                "and keeps its own record of them so the gallery can show them. Turning this off " +
                "does not delete that record — Delete it from App media if you want it gone.",
            section = "Scanning",
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
            title = "Video frames",
            subtitle = "Show a real frame for videos. Off leaves the video icon.",
            section = "Thumbnails & result cards",
            advanced = true,
            checked = settings.videoThumbnails,
            onCheckedChange = { value -> update { it.copy(videoThumbnails = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Album art",
            subtitle = "Show the artwork embedded in audio files. Off leaves the music icon.",
            section = "Thumbnails & result cards",
            advanced = true,
            checked = settings.audioArtwork,
            onCheckedChange = { value -> update { it.copy(audioArtwork = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Document previews",
            subtitle = "Render the first page of a PDF. This is the most expensive preview, " +
                "which is why it is separate.",
            section = "Thumbnails & result cards",
            advanced = true,
            checked = settings.documentPreviews,
            onCheckedChange = { value -> update { it.copy(documentPreviews = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Show resolution and duration",
            subtitle = "Adds pixel size and running time to tiles, where the app actually " +
                "knows them.",
            section = "Thumbnails & result cards",
            checked = settings.showResolution,
            onCheckedChange = { value -> update { it.copy(showResolution = value) } },
        ),
    )

    add(
        SettingsRow.Slider(
            title = "Thumbnail cache limit",
            subtitle = "How much space the app's own preview cache may use. Takes effect the " +
                "next time you open the app.",
            section = "Thumbnails & result cards",
            value = settings.thumbnailCacheLimitMb,
            range = 32..1024,
            onChange = { value -> update { it.copy(thumbnailCacheLimitMb = value) } },
        ),
    )
    add(
        SettingsRow.Action(
            title = "Thumbnail cache",
            subtitle = cacheSizeLabel,
            section = "Thumbnails & result cards",
            label = "Clear",
            onClick = onClearThumbnailCache,
        ),
    )
    add(
        SettingsRow.Info(
            title = "What clearing the cache does",
            subtitle = "It empties the app's own store of generated previews, so the next scan " +
                "rebuilds them. It never touches your photos, videos, or anything you recovered.",
            section = "Thumbnails & result cards",
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
            subtitle = settings.performanceMode.policy?.let { policy ->
                "Sets ${policy.concurrentTasks} concurrent task(s), a batch of ${policy.batchSize}, " +
                    if (policy.pauseWhenBackgrounded) "and pauses in the background." else "and keeps scanning in the background."
            } ?: "The three settings below are used as they are.",
            section = "Performance",
            advanced = true,
            options = PerformanceMode.entries.map { it.label },
            selected = settings.performanceMode.label,
            onSelect = { label ->
                val mode = PerformanceMode.entries.first { m -> m.label == label }
                update { current ->
                    // Choosing a mode writes the numbers it stands for, so the mode is a preset
                    // rather than a label — which is what it was before.
                    mode.policy?.let { policy ->
                        current.copy(
                            performanceMode = mode,
                            maxConcurrentTasks = policy.concurrentTasks,
                            scanBatchSize = policy.batchSize,
                            pauseWhenBackgrounded = policy.pauseWhenBackgrounded,
                        )
                    } ?: current.copy(performanceMode = mode)
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
            onChange = { value -> update { it.copy(maxConcurrentTasks = value, performanceMode = PerformanceMode.CUSTOM) } },
        ),
    )

    add(
        SettingsRow.Slider(
            title = "Index batch size",
            subtitle = "How many files are read before the library is published again. " +
                "Smaller shows results sooner; larger does fewer passes.",
            section = "Performance",
            advanced = true,
            value = settings.scanBatchSize,
            range = 100..5000,
            onChange = { value -> update { it.copy(scanBatchSize = value, performanceMode = PerformanceMode.CUSTOM) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Pause scanning in the background",
            section = "Performance",
            advanced = true,
            checked = settings.pauseWhenBackgrounded,
            onCheckedChange = { value -> update { it.copy(pauseWhenBackgrounded = value, performanceMode = PerformanceMode.CUSTOM) } },
        ),
    )

    // ---- Recovery & export ---------------------------------------------
    add(
        SettingsRow.Switch(
            title = "Recovery: quick scan",
            subtitle = "Offers the Quick Scan on the Recovery screen.",
            section = "Recovery & export",
            checked = settings.recoveryQuickScanEnabled,
            onCheckedChange = { value -> update { it.copy(recoveryQuickScanEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Recovery: thumbnails and cache",
            subtitle = "Offers the scan that finds preview artefacts. It never finds originals, " +
                "and says so on every result.",
            section = "Recovery & export",
            checked = settings.recoveryThumbnailCacheEnabled,
            onCheckedChange = { value -> update { it.copy(recoveryThumbnailCacheEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Allow export",
            subtitle = "Lets recovered items be written to a folder you choose. Off leaves " +
                "recovery read-only.",
            section = "Recovery & export",
            checked = settings.exportEnabled,
            onCheckedChange = { value -> update { it.copy(exportEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Verify after copying",
            subtitle = "Checks the copy against the source size before reporting success. Off " +
                "reports success as soon as the write finishes.",
            section = "Recovery & export",
            advanced = true,
            checked = settings.exportVerifyAfterCopy,
            onCheckedChange = { value -> update { it.copy(exportVerifyAfterCopy = value) } },
        ),
    )
    add(
        SettingsRow.Choice(
            title = "If the name already exists",
            subtitle = "Overwriting is deliberately not offered: silently replacing a file you " +
                "already have is not a recovery tool's job.",
            section = "Recovery & export",
            advanced = true,
            options = ExportDuplicatePolicy.entries.map { it.label },
            selected = settings.exportDuplicatePolicy.label,
            onSelect = { label ->
                ExportDuplicatePolicy.entries.firstOrNull { it.label == label }?.let { policy ->
                    update { it.copy(exportDuplicatePolicy = policy) }
                }
            },
        ),
    )
    add(
        SettingsRow.Slider(
            title = "Copies at once",
            subtitle = "How many files may be exported in parallel.",
            section = "Recovery & export",
            advanced = true,
            value = settings.exportMaxConcurrentJobs,
            range = 1..8,
            onChange = { value -> update { it.copy(exportMaxConcurrentJobs = value) } },
        ),
    )

    // ---- About ----------------------------------------------------------
    // The version and the update check are one row: two rows saying the same thing in
    // different sections read as two unrelated features, and take two lines to say one thing.
    add(
        SettingsRow.Action(
            title = "ProGallery ${BuildConfig.VERSION_NAME}",
            subtitle = "Build ${BuildConfig.VERSION_CODE}. Asks the project's releases feed " +
                "whether a newer version exists. Nothing about this device is sent.",
            section = "About",
            label = "Check for updates",
            onClick = onCheckForUpdates,
        ),
    )
    add(
        SettingsRow.Action(
            title = "Suggestions, feedback and feature requests",
            subtitle = "Reach the developer on Instagram at @osintgram_io.",
            section = "About",
            label = "@osintgram_io",
            onClick = onOpenInstagram,
        ),
    )

    // ---- Diagnostics & experimental -----------------------------------
    // ---- Usage data ----------------------------------------------------
    add(
        SettingsRow.Switch(
            title = "Anonymised usage data",
            subtitle = "Keeps counts of which features are used, how long loading takes, the " +
                "device model and Android version, and reduced crash records — on this device " +
                "only. Nothing leaves the device, and it is deleted by the button below.",
            section = "Diagnostics & experimental",
            checked = settings.usageDataEnabled,
            onCheckedChange = { value -> update { it.copy(usageDataEnabled = value) } },
        ),
    )
    add(
        SettingsRow.Info(
            title = "What is collected, and what never is",
            subtitle = "Collected: which feature was opened, how often search ran and albums " +
                "were created, how long loading took, the device model and Android version, and " +
                "crash records that keep only the exception's class and method — never its " +
                "message. Never collected: photos, contacts, location, face data, file names, " +
                "file paths, search terms, or anything inside your files. Photo access is only " +
                "ever for the photos you choose, through the system picker, and can be revoked " +
                "at any time.",
            section = "Diagnostics & experimental",
        ),
    )
    add(
        SettingsRow.Action(
            title = "Review of the last 7 days",
            subtitle = "What the recorded counts say — which area carries the app, what is slow, " +
                "whether anything crashed. This is what the next round of work is chosen from.",
            section = "Diagnostics & experimental",
            label = "Review",
            onClick = onReviewUsage,
        ),
    )
    add(
        SettingsRow.Action(
            title = "Export usage data",
            subtitle = "Writes the same file to a location you choose. It is plain text you can " +
                "read, and it holds counts only.",
            section = "Diagnostics & experimental",
            label = "Export",
            onClick = onExportUsage,
        ),
    )
    add(
        SettingsRow.Action(
            title = "Delete usage data",
            subtitle = "Removes everything recorded, now.",
            section = "Diagnostics & experimental",
            label = "Delete",
            onClick = onDeleteUsage,
        ),
    )

    add(
        SettingsRow.Action(
            title = "Export settings",
            subtitle = "Writes every choice above to a file you choose. The file holds only your " +
                "choices — no file paths and no access grants.",
            section = "Diagnostics & experimental",
            label = "Export",
            onClick = onExportSettings,
        ),
    )
    add(
        SettingsRow.Action(
            title = "Import settings",
            subtitle = "Applies a settings file. Nothing changes until the file has been read " +
                "and understood, so a bad file leaves your settings alone.",
            section = "Diagnostics & experimental",
            label = "Import",
            onClick = onImportSettings,
        ),
    )
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
    add(
        SettingsRow.Switch(
            title = "Keep a record of recovery and export activity",
            subtitle = "Records recent recovery scans and exports locally, so the history screen " +
                "has something to show. Nothing leaves the device.",
            section = "Diagnostics & experimental",
            checked = settings.keepActivityHistory,
            onCheckedChange = { value -> update { it.copy(keepActivityHistory = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Write diagnostics",
            subtitle = "Keeps a local log of what the app did. It is never sent anywhere.",
            section = "Diagnostics & experimental",
            checked = settings.saveDiagnosticLogs,
            onCheckedChange = { value -> update { it.copy(saveDiagnosticLogs = value) } },
        ),
    )
    add(
        SettingsRow.Switch(
            title = "Include file paths in diagnostics",
            subtitle = "Off by default, and it stays off unless you turn it on: a log is a file " +
                "you may share, and a path says where your files are.",
            section = "Diagnostics & experimental",
            advanced = true,
            checked = settings.includePathsInLogs,
            onCheckedChange = { value -> update { it.copy(includePathsInLogs = value) } },
        ),
    )
    add(
        SettingsRow.Action(
            title = "Access diagnostics",
            subtitle = "What the app can reach, what each access is for, and how to change it. " +
                "Every fix goes through the system dialog or the system settings screen.",
            section = "Diagnostics & experimental",
            label = "Open",
            onClick = onOpenPermissionDiagnostics,
        ),
    )
    add(
        SettingsRow.Action(
            title = "Save diagnostics",
            subtitle = "Writes the local log to a file you choose, so you can attach it when " +
                "reporting a problem. It is written only if diagnostics are switched on.",
            section = "Diagnostics & experimental",
            label = "Save",
            onClick = onSaveDiagnostics,
        ),
    )
    add(
        SettingsRow.Action(
            title = "Clear diagnostics",
            subtitle = "Empties the local log now, without waiting for the automatic clear.",
            section = "Diagnostics & experimental",
            label = "Clear",
            onClick = onClearDiagnostics,
        ),
    )
    add(
        SettingsRow.Slider(
            title = "Clear diagnostics after",
            subtitle = "Days before the log is cleared automatically.",
            section = "Diagnostics & experimental",
            advanced = true,
            value = settings.autoClearLogsAfterDays,
            range = 1..90,
            onChange = { value -> update { it.copy(autoClearLogsAfterDays = value) } },
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

/**
 * The result of an update check.
 *
 * It says which version you have and which is published, fetches the file **in the app**, checks
 * it against this app's own signing key, and asks Android to install it. Nothing is downloaded
 * or installed until the user presses the button, and the system's own confirmation still
 * appears — which the dialog says in advance.
 */
@Composable
private fun UpdateDialog(
    state: SettingsViewModel.UpdateUiState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
) {
    val available = state.result as? UpdateCheck.Available
    val step = available?.let {
        UpdateInstallPlan.step(
            installedFromPlay = state.installedFromPlay,
            hasApk = it.release.apkUrl != null,
            downloaded = state.download is SettingsViewModel.UpdateDownloadUi.Ready,
            canInstallPackages = state.canInstallPackages,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check for updates") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("You are on version ${state.currentVersion}.")

                when {
                    state.checking -> Text("Checking…")

                    available != null -> {
                        val release = available.release
                        Text(
                            text = "Version ${release.displayVersion} is available.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        release.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                            Text(
                                // Long notes are clipped: the dialog is a summary, and the
                                // release page has the whole thing.
                                text = notes.take(1_200) + if (notes.length > 1_200) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // What happens next, in the app's own words — including the part that
                        // cannot be avoided: Android shows its own confirmation.
                        step?.let { UpdateInstallPlan.note(it) }?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        when (val download = state.download) {
                            is SettingsViewModel.UpdateDownloadUi.Working -> {
                                if (download.percent >= 0) {
                                    LinearProgressIndicator(
                                        progress = { download.percent / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = "Downloading… ${download.percent}%",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    // The server did not say how large the file is, so a
                                    // percentage would be invented.
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = "Downloading…",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            is SettingsViewModel.UpdateDownloadUi.Ready -> Text(
                                text = "Downloaded and checked. It is signed with this app's " +
                                    "own key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            is SettingsViewModel.UpdateDownloadUi.Failed -> Text(
                                text = download.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )

                            SettingsViewModel.UpdateDownloadUi.Idle -> Unit
                        }
                    }

                    state.result is UpdateCheck.UpToDate -> {
                        val latest = (state.result as UpdateCheck.UpToDate).latestVersion
                        Text("You have the newest version ($latest).")
                    }

                    state.result is UpdateCheck.Failed -> {
                        Text(
                            text = (state.result as UpdateCheck.Failed).reason,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val result = state.result) {
                is UpdateCheck.Available -> {
                    val working = state.download is SettingsViewModel.UpdateDownloadUi.Working
                    if (step != null && step != UpdateInstallStep.PLAY && !working) {
                        TextButton(
                            onClick = when (step) {
                                UpdateInstallStep.DOWNLOAD -> onDownload
                                UpdateInstallStep.ALLOW_INSTALLS -> onAllowInstalls
                                else -> onInstall
                            },
                        ) { Text(UpdateInstallPlan.actionLabel(step)) }
                    }
                    TextButton(onClick = { onOpen(result.release.pageUrl) }) { Text("Release page") }
                }

                // A check that failed can simply be tried again, without closing the dialog and
                // finding the row a second time.
                is UpdateCheck.Failed -> TextButton(onClick = onRetry) { Text("Try again") }

                else -> TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (state.result is UpdateCheck.Available) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        },
    )
}

/** Where suggestions and feature requests are sent. */
private const val INSTAGRAM_URL = "https://instagram.com/osintgram_io"

/**
 * The seven-day review.
 *
 * This is the "improve the UI every seven days" part made visible: it shows the counts, and the
 * observations derived from them, so the next round of work is chosen from what the app actually
 * sees rather than from a guess. It also says, in the same breath, that none of it has left the
 * device — which is the thing a person reading a screen full of their own usage wants to know.
 */
@Composable
private fun UsageReviewDialog(digest: UsageDigest?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("The last 7 days") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (digest == null) {
                    Text("Reading…")
                } else {
                    Text(
                        text = "Recorded on ${digest.daysWithData} of the last " +
                            "${digest.windowDays} days.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    if (digest.features.isNotEmpty()) {
                        Text("Most used", style = MaterialTheme.typography.titleSmall)
                        digest.features.take(5).forEach { (feature, count) ->
                            Text(
                                text = "${feature.id} — $count",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (digest.slowest.isNotEmpty()) {
                        Text("Slowest", style = MaterialTheme.typography.titleSmall)
                        digest.slowest.take(5).forEach { (operation, average) ->
                            Text(
                                text = "${operation.id} — ${average}ms average",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Text(
                        text = "Crashes recorded: ${digest.crashes}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text("What it suggests", style = MaterialTheme.typography.titleSmall)
                    digest.observations.forEach { observation ->
                        Text(
                            text = "• $observation",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Text(
                        text = "Counts only — no file names, no paths, no search terms, and " +
                            "nothing has left this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
