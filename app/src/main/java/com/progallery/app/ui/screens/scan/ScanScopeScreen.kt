package com.progallery.app.ui.screens.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.scan.ScanScope
import com.progallery.app.data.scan.ScanTarget
import com.progallery.app.data.scan.ScopeLine

/**
 * What a scan would do, shown before it starts.
 *
 * Settings described a scan and offered no way to run one, and the two questions worth asking first
 * — *what will it look at* and *what will it miss* — could only be answered by reading the code.
 * This screen answers both, and then runs it.
 *
 * The lines that are **not** included are drawn as deliberately as the ones that are. A scope that
 * only listed what it would scan would be a sales page; this is meant to be a description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScopeScreen(
    onBack: () -> Unit,
    onStart: (ScanTarget) -> Unit,
    viewModel: ScanScopeViewModel = hiltViewModel(),
) {
    val scope by viewModel.scope.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What this scan will do") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = scope
        if (current == null) {
            // One frame, while the settings and the folder list arrive.
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Heading("Methods") }
            items(current.methods) { line -> ScopeRow(line) }

            item { Heading("File types") }
            item {
                Text(
                    text = if (current.kinds.isEmpty()) {
                        "None — every category is switched off, so nothing would be collected."
                    } else {
                        current.kinds.joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current.kinds.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            item { Heading("Folders you granted") }
            item {
                Text(
                    text = if (current.folders.isEmpty()) {
                        "None. Folder scanning has nothing to walk until you add one."
                    } else {
                        current.folders.joinToString("\n")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item { Heading("Limits") }
            items(current.limits) { line -> ScopeRow(line, showIcon = false) }

            if (current.notes.isNotEmpty()) {
                item { Heading("What it will not do") }
                items(current.notes) { note ->
                    Surface(
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { onStart(current.target) },
                    enabled = current.canStart,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (current.canStart) "Run the scan" else "Nothing to scan")
                }
            }
            item {
                Text(
                    text = if (current.canStart) {
                        "Starts now, using these settings. You can cancel from the scan screen."
                    } else {
                        "Fix the warnings above, or change the settings, and this becomes available."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ScopeRow(line: ScopeLine, showIcon: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (showIcon) {
            Icon(
                imageVector = if (line.included) Icons.Filled.CheckCircle else Icons.Filled.RemoveCircleOutline,
                contentDescription = if (line.included) "Included" else "Not included",
                tint = if (line.included) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Column {
            Text(
                text = line.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (!showIcon || line.included) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = line.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
