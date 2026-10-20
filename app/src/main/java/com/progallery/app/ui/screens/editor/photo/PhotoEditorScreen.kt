package com.progallery.app.ui.screens.editor.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.edit.photo.ExportFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    uri: String,
    onBack: () -> Unit,
    viewModel: PhotoEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var showExport by remember { mutableStateOf(false) }

    LaunchedEffect(uri) { viewModel.load(uri) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Photo Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.editState.canUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.editState.canRedo) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset")
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    state.displayed != null -> Image(
                        bitmap = state.displayed!!.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleShowOriginal() },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Compare,
                        contentDescription = "Before / after",
                        tint = if (state.showOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            TabRow(selectedTabIndex = tab) {
                listOf("Adjust", "Filters", "Crop", "Transform").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    0 -> AdjustTab(viewModel, state)
                    1 -> FiltersTab(viewModel, state)
                    2 -> CropTab(viewModel)
                    else -> TransformTab(viewModel)
                }
            }
        }
    }

    if (showExport) {
        ExportDialog(
            state = state,
            onFormat = viewModel::setFormat,
            onQuality = viewModel::setQuality,
            onDismiss = { showExport = false },
            onSave = { overwrite ->
                viewModel.export(overwrite)
                showExport = false
            },
        )
    }
}

@Composable
private fun AdjustTab(viewModel: PhotoEditorViewModel, state: PhotoEditorUiState) {
    LabeledSlider("Brightness", state.params.brightness, -1f..1f) { viewModel.setBrightness(it) }
    LabeledSlider("Contrast", state.params.contrast, 0f..2f) { viewModel.setContrast(it) }
    LabeledSlider("Saturation", state.params.saturation, 0f..2f) { viewModel.setSaturation(it) }
    LabeledSlider("Warmth", state.params.warmth, -1f..1f) { viewModel.setWarmth(it) }
    LabeledSlider("Exposure", state.params.exposure, -1f..1f) { viewModel.setExposure(it) }
}

@Composable
private fun FiltersTab(viewModel: PhotoEditorViewModel, state: PhotoEditorUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Grayscale", modifier = Modifier.weight(1f))
        Switch(checked = state.params.grayscale, onCheckedChange = { viewModel.toggleGrayscale() })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Sepia", modifier = Modifier.weight(1f))
        Switch(checked = state.params.sepia, onCheckedChange = { viewModel.toggleSepia() })
    }
    LabeledSlider("Blur", state.params.blurRadius, 0f..25f) { viewModel.setBlur(it) }
    LabeledSlider("Sharpen", state.params.sharpenAmount, 0f..1f) { viewModel.setSharpen(it) }
}

@Composable
private fun CropTab(viewModel: PhotoEditorViewModel) {
    Text("Aspect ratio", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(onClick = { viewModel.applyAspect(null) }, selected = false, label = { Text("Free") })
        FilterChip(onClick = { viewModel.applyAspect(1f) }, selected = false, label = { Text("1:1") })
        FilterChip(onClick = { viewModel.applyAspect(4f / 3f) }, selected = false, label = { Text("4:3") })
        FilterChip(onClick = { viewModel.applyAspect(16f / 9f) }, selected = false, label = { Text("16:9") })
    }
    Text(
        "Crops are applied centred. Choose Free to restore the full frame.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TransformTab(viewModel: PhotoEditorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { viewModel.rotateLeft() }) {
            Icon(Icons.Filled.RotateLeft, contentDescription = "Rotate left")
        }
        IconButton(onClick = { viewModel.rotateRight() }) {
            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate right")
        }
        IconButton(onClick = { viewModel.flipHorizontal() }) {
            Icon(Icons.Filled.Flip, contentDescription = "Flip horizontal")
        }
        OutlinedButton(onClick = { viewModel.flipVertical() }) { Text("Flip vertical") }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ExportDialog(
    state: PhotoEditorUiState,
    onFormat: (ExportFormat) -> Unit,
    onQuality: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { format ->
                        FilterChip(
                            onClick = { onFormat(format) },
                            selected = state.format == format,
                            label = { Text(format.name) },
                        )
                    }
                }
                Text("Quality: ${state.quality}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = state.quality.toFloat(),
                    onValueChange = { onQuality(it.toInt()) },
                    valueRange = 10f..100f,
                )
                Text(
                    "Saves a new file to Pictures/ProGallery. The original is preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(false) }, enabled = !state.exporting) { Text("Save copy") }
        },
        dismissButton = {
            TextButton(onClick = { onSave(true) }, enabled = !state.exporting) { Text("Overwrite original") }
        },
    )
}
