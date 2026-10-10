package de.bolzwerk.tagwerk.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bolzwerk.tagwerk.data.db.ProcessingState
import de.bolzwerk.tagwerk.data.repo.NoteItem
import de.bolzwerk.tagwerk.ui.components.CategoryChip
import de.bolzwerk.tagwerk.ui.components.Waveform
import de.bolzwerk.tagwerk.ui.components.formatDuration
import de.bolzwerk.tagwerk.ui.theme.ProseTextStyle

/**
 * Aufnahme-Panel am unteren Bildrand. Bewusst kein Dialog-Fenster, damit ein gedrückt gehaltener
 * Mikrofon-Button seine Berührung behält (Push-to-talk).
 */
@Composable
fun RecordPanel(
    state: RecordingUiState,
    savedNote: NoteItem?,
    onCancel: () -> Unit,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onConfirmCategory: (noteId: Long, categoryId: Long) -> Unit,
    onRemoveCategory: (noteId: Long, categoryId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            when (state) {
                is RecordingUiState.Recording -> RecordingContent(state, onCancel, onTogglePause, onFinish)
                is RecordingUiState.Saved -> SavedContent(state, savedNote, onDismiss, onOpenNote, onConfirmCategory, onRemoveCategory)
                is RecordingUiState.Error -> ErrorContent(state.message, onDismiss)
                RecordingUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun RecordingContent(
    state: RecordingUiState.Recording,
    onCancel: () -> Unit,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
) {
    Text(
        when {
            state.holding -> "Loslassen zum Speichern"
            state.paused -> "Pausiert"
            else -> "Notiz wird aufgenommen"
        },
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(4.dp))
    Text(formatDuration(state.elapsedMs), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    Waveform(state.levels, Modifier.fillMaxWidth().height(56.dp))
    if (!state.holding) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Abbrechen") }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = onTogglePause) {
                Icon(
                    if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (state.paused) "Fortsetzen" else "Pausieren",
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onFinish) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Fertig")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedContent(
    state: RecordingUiState.Saved,
    note: NoteItem?,
    onDismiss: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onConfirmCategory: (Long, Long) -> Unit,
    onRemoveCategory: (Long, Long) -> Unit,
) {
    val processing = note == null || note.isProcessing
    Text(
        when {
            note?.processingState == ProcessingState.FAILED -> "Gespeichert – Verarbeitung fehlgeschlagen"
            state.offline && processing -> "Gespeichert"
            note?.processingState == ProcessingState.PENDING_TRANSCRIPTION -> "Wird transkribiert …"
            note?.processingState == ProcessingState.PENDING_CATEGORIZATION -> "Wird eingeordnet …"
            else -> "Notiz gespeichert"
        },
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(8.dp))
    if (state.offline && processing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                "Kein Netz – die Aufnahme wird transkribiert, sobald du online bist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (processing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        note?.processingError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (note != null && note.text.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        if (note.title.isNotBlank()) Text(note.title, style = MaterialTheme.typography.titleSmall)
        Text(note.text, style = ProseTextStyle, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
    if (note?.processingState == ProcessingState.FAILED) {
        Text(note.processingError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (note != null && note.categories.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            note.categories.forEach { category ->
                CategoryChip(
                    category = category,
                    onConfirm = { onConfirmCategory(note.id, category.id) },
                    onRemove = { onRemoveCategory(note.id, category.id) },
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = { onOpenNote(state.noteId) }) { Text("Bearbeiten") }
        Spacer(Modifier.weight(1f))
        Button(onClick = onDismiss) { Text("Schließen") }
    }
}

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(12.dp))
        Text(message, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Button(onClick = onDismiss) { Text("OK") }
    }
}
