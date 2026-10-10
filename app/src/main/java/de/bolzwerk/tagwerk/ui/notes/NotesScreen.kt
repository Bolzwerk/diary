package de.bolzwerk.tagwerk.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.repo.NoteItem
import de.bolzwerk.tagwerk.ui.components.ColorDot
import de.bolzwerk.tagwerk.ui.components.NoteRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onOpenNote: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    fun delete(note: NoteItem) {
        viewModel.requestDelete(note.id)
        scope.launch {
            var undone = false
            try {
                undone = snackbarHostState.showSnackbar(
                    message = "„${note.displayTitle}“ gelöscht",
                    actionLabel = "Rückgängig",
                    duration = SnackbarDuration.Short,
                ) == SnackbarResult.ActionPerformed
            } finally {
                // Auch wenn der Screen verlassen wird (Coroutine abgebrochen), wird das Löschen abgeschlossen.
                if (undone) viewModel.undoDelete(note.id) else viewModel.commitDelete(note.id)
            }
        }
    }

    NotesContent(
        state = state,
        contentPadding = contentPadding,
        onOpenNote = onOpenNote,
        onOpenSettings = onOpenSettings,
        onQueryChange = viewModel::setQuery,
        onStatusFilter = viewModel::setStatusFilter,
        onCategoryFilter = viewModel::setCategoryFilter,
        onToggleDone = viewModel::toggleDone,
        onDelete = ::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesContent(
    state: NotesUiState,
    contentPadding: PaddingValues,
    onOpenNote: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onQueryChange: (String) -> Unit,
    onStatusFilter: (StatusFilter) -> Unit,
    onCategoryFilter: (Long?) -> Unit,
    onToggleDone: (NoteItem) -> Unit,
    onDelete: (NoteItem) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(state.filter.query.isNotEmpty()) }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = {
                if (searching) {
                    OutlinedTextField(
                        value = state.filter.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Suchen …") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    )
                } else {
                    Text("Notizen")
                }
            },
            actions = {
                IconButton(onClick = {
                    if (searching) onQueryChange("")
                    searching = !searching
                }) {
                    Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Suche")
                }
                if (!searching) {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen") }
                }
            },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            StatusFilter.entries.forEachIndexed { index, filter ->
                SegmentedButton(
                    selected = state.filter.status == filter,
                    onClick = { onStatusFilter(filter) },
                    shape = SegmentedButtonDefaults.itemShape(index, StatusFilter.entries.size),
                ) { Text(filter.label) }
            }
        }
        if (state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.filter.categoryId == null,
                        onClick = { onCategoryFilter(null) },
                        label = { Text("Alle Kategorien") },
                    )
                }
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.filter.categoryId == category.id,
                        onClick = {
                            onCategoryFilter(if (state.filter.categoryId == category.id) null else category.id)
                        },
                        label = { Text(category.name) },
                        leadingIcon = { ColorDot(category.colorIndex) },
                    )
                }
            }
        }
        if (!state.loading && state.notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    if (state.filter.query.isNotBlank()) "Keine Treffer." else "Hier ist noch nichts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 180.dp)) {
            items(state.notes, key = { it.id }) { note ->
                SwipeableNoteRow(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onToggleDone = { onToggleDone(note) },
                    onDelete = { onDelete(note) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

/** Wischen nach rechts: erledigt/offen umschalten. Wischen nach links: löschen (mit Rückgängig). */
@Composable
private fun SwipeableNoteRow(
    note: NoteItem,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onToggleDone(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val toDone = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (toDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = if (toDone) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (toDone) {
                    Icon(Icons.Filled.Done, contentDescription = if (note.status == NoteStatus.DONE) "Wieder öffnen" else "Erledigt")
                } else {
                    Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                }
            }
        },
    ) {
        NoteRow(note, onClick = onClick, onToggleDone = onToggleDone)
    }
}
