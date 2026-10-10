package de.bolzwerk.tagwerk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bolzwerk.tagwerk.data.db.NoteSource
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.db.ProcessingState
import de.bolzwerk.tagwerk.data.repo.NoteItem

@Composable
fun NoteRow(
    note: NoteItem,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = note.status == NoteStatus.DONE
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            Checkbox(checked = done, onCheckedChange = { onToggleDone() })
        },
        headlineContent = {
            Text(
                note.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            val preview = note.text.takeIf { note.title.isNotBlank() && it.isNotBlank() }
            if (preview != null || note.categories.isNotEmpty()) {
                androidx.compose.foundation.layout.Column {
                    if (preview != null) {
                        Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (note.categories.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            note.categories.forEach { category ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (category.isSuggestion) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Vorschlag", modifier = Modifier.size(12.dp))
                                    } else {
                                        ColorDot(category.colorIndex, size = 8.dp)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(category.name, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    note.isProcessing -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    note.processingState == ProcessingState.FAILED ->
                        Icon(Icons.Filled.ErrorOutline, contentDescription = "Fehler", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    note.source == NoteSource.DICTATED ->
                        Icon(Icons.Filled.Mic, contentDescription = "Diktiert", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(formatShortDate(note.createdAt), style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}
