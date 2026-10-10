package de.bolzwerk.tagwerk.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.bolzwerk.tagwerk.data.repo.NoteItem
import de.bolzwerk.tagwerk.ui.components.CategoryChip
import de.bolzwerk.tagwerk.ui.components.NoteRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onShowAllNotes: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onOpenSettings = onOpenSettings,
        onOpenNote = onOpenNote,
        onShowAllNotes = onShowAllNotes,
        onToggleDone = viewModel::toggleDone,
        onConfirmCategory = viewModel::confirmCategory,
        onRemoveCategory = viewModel::removeCategory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onShowAllNotes: () -> Unit,
    onToggleDone: (NoteItem) -> Unit,
    onConfirmCategory: (noteId: Long, categoryId: Long) -> Unit,
    onRemoveCategory: (noteId: Long, categoryId: Long) -> Unit,
    today: Date = Date(),
) {
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(today)) },
            actions = {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen") }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp),
        ) {
            if (!state.loading && !state.aiConfigured) {
                item { SetupCard(onOpenSettings) }
            }
            item {
                Text(
                    "Was beschäftigt dich?",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (state.processingCount > 0 || state.failedCount > 0) {
                    Text(
                        listOfNotNull(
                            state.processingCount.takeIf { it > 0 }?.let { "$it in Bearbeitung" },
                            state.failedCount.takeIf { it > 0 }?.let { "$it mit Fehler" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.toConfirm.isNotEmpty()) {
                item { SectionHeader("Zu bestätigen", "Tippen übernimmt den Vorschlag, ✕ verwirft ihn") }
                items(state.toConfirm, key = { "confirm-${it.id}" }) { note ->
                    SuggestionCard(
                        note = note,
                        onOpen = { onOpenNote(note.id) },
                        onConfirm = { onConfirmCategory(note.id, it) },
                        onRemove = { onRemoveCategory(note.id, it) },
                    )
                }
            }
            item { SectionHeader("Offene Notizen (${state.openCount})") }
            if (!state.loading && state.openNotes.isEmpty()) {
                item {
                    Text(
                        "Keine offenen Notizen. Tippe auf das Mikrofon oder halte es gedrückt, um etwas zu diktieren.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.openNotes, key = { it.id }) { note ->
                NoteRow(note, onClick = { onOpenNote(note.id) }, onToggleDone = { onToggleDone(note) })
            }
            if (state.openCount > state.openNotes.size) {
                item {
                    TextButton(onClick = onShowAllNotes, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Alle ${state.openCount} offenen Notizen anzeigen")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Key, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("KI-Verbindung einrichten", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Hinterlege Base-URL und API-Key, damit Diktate transkribiert und eingeordnet werden. Aufnahmen bleiben bis dahin gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)) {
            Text("Einrichten")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionCard(
    note: NoteItem,
    onOpen: () -> Unit,
    onConfirm: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(note.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                note.categories.filter { it.isSuggestion }.forEach { category ->
                    CategoryChip(category, onConfirm = { onConfirm(category.id) }, onRemove = { onRemove(category.id) })
                }
            }
        }
    }
}
