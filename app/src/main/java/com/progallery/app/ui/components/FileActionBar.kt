package com.progallery.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.progallery.app.data.model.MediaItem
import com.progallery.app.util.MediaFormatUtils

/** One labelled action in a [FileActionBar]. */
data class FileActionSpec(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A bottom action bar of labelled icons. Always clears the system navigation
 * bar so the actions stay tappable.
 */
@Composable
fun FileActionBar(
    actions: List<FileActionSpec>,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val resolved = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { spec ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = spec.onClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = spec.label,
                        tint = resolved,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = resolved,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Picks a destination folder: an existing one from the index, or a typed path.
 * Used by Move and Copy.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<String>,
    currentPath: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Current: ${currentPath ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                folders.forEach { folder ->
                    Text(
                        text = folder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(folder) }
                            .padding(vertical = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text("Or type a folder, e.g. Download/Keep") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(custom) }, enabled = custom.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Details for any file type, including the ones with no in-app preview. */
@Composable
fun FileInfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Name", item.displayName)
                InfoRow("Kind", item.mediaType.name.lowercase().replaceFirstChar { it.uppercase() })
                InfoRow("Type", item.mimeType.ifBlank { "—" })
                InfoRow("Size", MediaFormatUtils.formatBytes(item.sizeBytes))
                InfoRow("Dimensions", item.dimensionLabel)
                if (item.isVideo || item.isAudio) {
                    InfoRow("Duration", MediaFormatUtils.formatDuration(item.durationMs))
                }
                InfoRow("Album", item.bucketName)
                InfoRow("Path", item.relativePath ?: "—")
                InfoRow("Modified", MediaFormatUtils.formatDate(item.dateModifiedSeconds))
                InfoRow("Source", if (item.source.name == "DEEP_SCAN") "Deep scan" else "MediaStore")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
