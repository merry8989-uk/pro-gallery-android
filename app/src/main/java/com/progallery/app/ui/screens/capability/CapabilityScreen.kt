package com.progallery.app.ui.screens.capability

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.capability.Capability
import com.progallery.app.data.capability.FormatCapability

/**
 * What this device can open, told before the attempt rather than after it.
 *
 * The point of the screen is the **reason**: being told that `.wmv` needs another app is a
 * different experience from a file that fails when tapped. Every line here is generated from the
 * same tables the player and the readers use, so it cannot promise something the app does not do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityScreen(
    onBack: () -> Unit,
    viewModel: CapabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = state.visible

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Format support") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "${state.entries.size} formats the app knows. " +
                        "Generated from the same tables the player and the readers use, so this " +
                        "cannot promise something the app does not do.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.verdict == null,
                        onClick = { viewModel.setVerdict(null) },
                        label = { Text("All ${state.entries.size}") },
                    )
                    Capability.entries.forEach { verdict ->
                        FilterChip(
                            selected = state.verdict == verdict,
                            onClick = { viewModel.setVerdict(verdict) },
                            label = { Text("${verdict.label} ${state.counts[verdict] ?: 0}") },
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search a format or a reason") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (visible.isEmpty()) {
                item {
                    Text(
                        text = "Nothing matches that.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Grouped by verdict, so the answer to "can I open this?" is the section it is in.
            Capability.entries.forEach { verdict ->
                val group = visible.filter { it.verdict == verdict }
                if (group.isEmpty()) return@forEach

                item(key = "header-${verdict.name}") {
                    Column {
                        HorizontalDivider()
                        Text(
                            text = "${verdict.label} · ${group.size}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                }
                items(group, key = { "${verdict.name}-${it.extension}" }) { entry ->
                    FormatRow(entry)
                }
            }
        }
    }
}

@Composable
private fun FormatRow(entry: FormatCapability) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = ".${entry.extension}",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = entry.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The reason is the whole point of the row: it is what turns a failure into an
            // expectation.
            entry.reason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.verdict == Capability.NEEDS_ANOTHER_APP) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
