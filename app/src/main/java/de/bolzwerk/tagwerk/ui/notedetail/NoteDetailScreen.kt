package de.bolzwerk.tagwerk.ui.notedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.bolzwerk.tagwerk.data.db.CategoryEntity
import de.bolzwerk.tagwerk.data.db.NoteSource
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.db.ProcessingState
import de.bolzwerk.tagwerk.data.repo.NoteItem
import de.bolzwerk.tagwerk.ui.components.CategoryChip
import de.bolzwerk.tagwerk.ui.components.ColorDot
import de.bolzwerk.tagwerk.ui.components.formatDateTime
import de.bolzwerk.tagwerk.ui.theme.ProseTextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(viewModel: NoteDetailViewModel, onBack: () -> Unit) {
    val note by viewModel.note.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var addCategory by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.deleted) { if (viewModel.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notiz") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
                },
            )
        },
    ) { padding ->
        val current = note
        if (current == null || !viewModel.loaded) {
            if (viewModel.loaded) Text("Notiz nicht gefunden", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ProcessingCard(current, onRetry = viewModel::retry)

            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Titel") },
                placeholder = { Text(if (current.isProcessing) "Wird von der KI vorgeschlagen …" else "Ohne Titel") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.text,
                onValueChange = viewModel::onTextChange,
                label = { Text("Notiz (Original)") },
                textStyle = ProseTextStyle,
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(checked = current.status == NoteStatus.DONE, onCheckedChange = { viewModel.toggleDone() })
                Text("Erledigt")
            }

            Text("Kategorien", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.categories.forEach { category ->
                    CategoryChip(
                        category = category,
                        onConfirm = { viewModel.confirmCategory(category.id) },
                        onRemove = { viewModel.removeCategory(category.id) },
                    )
                }
                AssistChip(
                    onClick = { addCategory = true },
                    label = { Text("Kategorie") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            if (current.hasSuggestions) {
                Text(
                    "✨ = Vorschlag der KI – antippen zum Übernehmen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = viewModel::recategorize, enabled = !current.isProcessing && current.text.isNotBlank()) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("KI-Vorschlag erneuern")
            }

            if (current.hasAudio) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::togglePlayback) {
                    Icon(if (viewModel.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (viewModel.isPlaying) "Wiedergabe stoppen" else "Aufnahme anhören")
                }
                viewModel.playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                buildString {
                    append("Erstellt am ${formatDateTime(current.createdAt)}")
                    append(
                        when (current.source) {
                            NoteSource.DICTATED -> " · diktiert"
                            NoteSource.TYPED -> " · getippt"
                            NoteSource.DIARY -> " · aus dem Tagebuch"
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Notiz löschen?") },
            text = { Text("Die Notiz und ihre Aufnahme werden endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
    if (addCategory) {
        AddCategoryDialog(
            available = allCategories.filter { c -> note?.categories?.none { it.id == c.id } ?: true },
            onDismiss = { addCategory = false },
            onAdd = { name -> addCategory = false; viewModel.addCategory(name) },
        )
    }
}

@Composable
private fun ProcessingCard(note: NoteItem, onRetry: () -> Unit) {
    val failed = note.processingState == ProcessingState.FAILED
    if (!note.isProcessing && !failed) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    failed -> "Verarbeitung fehlgeschlagen"
                    note.processingState == ProcessingState.PENDING_TRANSCRIPTION -> "Wird transkribiert …"
                    else -> "Titel und Kategorien werden ermittelt …"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            if (!failed) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            note.processingError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (failed) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Erneut versuchen")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCategoryDialog(
    available: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorie hinzufügen") },
        text = {
            Column {
                if (available.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { category ->
                            SuggestionChip(
                                onClick = { onAdd(category.name) },
                                label = { Text(category.name) },
                                icon = { ColorDot(category.colorIndex) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Neue Kategorie") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
