package com.progallery.app.ui.screens.deepscan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.deepscan.ScanState
import com.progallery.app.util.MediaFormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepScanScreen(
    onBack: () -> Unit,
    viewModel: DeepScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.addDirectory(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deep Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Deep scan looks inside folders you explicitly grant access to (including nested folders) and finds supported photos and videos the system index may have missed, plus duplicate files. Android/data and Android/obb may be restricted by the system on newer versions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = { treeLauncher.launch(null) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add folder to scan")
            }

            if (state.directories.isNotEmpty()) {
                Text("Authorised folders", style = MaterialTheme.typography.titleSmall)
                state.directories.forEach { dir ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(dir.displayName, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeDirectory(dir.treeUri) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.startScan() },
                    enabled = state.directories.isNotEmpty() && state.scanState !is ScanState.Scanning,
                ) { Text("Start scan") }
                if (state.scanState is ScanState.Scanning) {
                    OutlinedButton(onClick = { viewModel.cancelScan() }) { Text("Cancel") }
                }
            }

            when (val scan = state.scanState) {
                is ScanState.Scanning -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Scanning: ${scan.directoriesScanned} folders, ${scan.filesExamined} files, ${scan.mediaFound} media found")
                    Text(scan.currentPath, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                is ScanState.Completed -> {
                    Card {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Scan complete", style = MaterialTheme.typography.titleMedium)
                            Text("Folders scanned: ${scan.summary.directoriesScanned}")
                            Text("Files examined: ${scan.summary.filesExamined}")
                            Text("Media found: ${scan.summary.mediaFound}")
                            Text("Duplicates: ${scan.summary.duplicatesFound}")
                            if (scan.summary.errors.isNotEmpty()) {
                                Text("Issues (${scan.summary.errors.size})", style = MaterialTheme.typography.titleSmall)
                                scan.summary.errors.take(10).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    if (scan.duplicates.isNotEmpty()) {
                        Text("Duplicate groups", style = MaterialTheme.typography.titleSmall)
                        scan.duplicates.take(20).forEach { group ->
                            Card {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("${group.items.size} copies • ${MediaFormatUtils.formatBytes(group.wastedBytes)} reclaimable")
                                    group.items.forEach {
                                        Text(it.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                is ScanState.Failed -> Text(scan.message, color = MaterialTheme.colorScheme.error)
                ScanState.Idle -> Unit
            }
        }
    }
}
