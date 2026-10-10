package de.bolzwerk.tagwerk.ui.categories

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.bolzwerk.tagwerk.AppContainer
import de.bolzwerk.tagwerk.data.db.AssignedBy
import de.bolzwerk.tagwerk.data.db.CategoryWithCount
import de.bolzwerk.tagwerk.domain.CategoryColors
import de.bolzwerk.tagwerk.domain.CategoryMatcher
import de.bolzwerk.tagwerk.ui.components.ColorDot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.categoryRepository

    val categories: StateFlow<List<CategoryWithCount>> = repository.observeWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) = viewModelScope.launch { repository.findOrCreate(name, AssignedBy.USER) }

    fun save(id: Long, name: String, colorIndex: Int) = viewModelScope.launch {
        repository.setColor(id, colorIndex)
        repository.rename(id, name)
    }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryWithCount?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategorien") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "Kategorie anlegen") }
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Text(
                "Noch keine Kategorien. Die KI legt beim Diktieren passende Kategorien an – oder du erstellst sie selbst.",
                modifier = Modifier.padding(padding).padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(categories, key = { it.id }) { category ->
                ListItem(
                    leadingContent = { ColorDot(category.colorIndex, size = 16.dp) },
                    headlineContent = { Text(category.name) },
                    supportingContent = { Text(if (category.noteCount == 1) "1 Notiz" else "${category.noteCount} Notizen") },
                    modifier = Modifier.clickable { editing = category },
                )
            }
        }
    }

    editing?.let { category ->
        EditCategoryDialog(
            category = category,
            otherNames = categories.filter { it.id != category.id }.map { it.name },
            onDismiss = { editing = null },
            onSave = { name, color -> editing = null; viewModel.save(category.id, name, color) },
            onDelete = { editing = null; viewModel.delete(category.id) },
        )
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Neue Kategorie") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { creating = false; viewModel.create(name) }) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Abbrechen") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCategoryDialog(
    category: CategoryWithCount,
    otherNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    var color by remember { mutableIntStateOf(category.colorIndex) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mergeTarget = otherNames.firstOrNull { CategoryMatcher.normalize(it) == CategoryMatcher.normalize(name) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${category.name}“ löschen?") },
            text = { Text("Die Notizen bleiben erhalten, nur die Zuordnung zu dieser Kategorie wird entfernt.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorie bearbeiten") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = mergeTarget?.let { { Text("Wird mit „$it“ zusammengeführt.") } },
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryColors.palette.indices.forEach { index ->
                        Box(
                            Modifier
                                .clickable { color = index }
                                .then(
                                    if (index == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) { ColorDot(index, size = 24.dp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, color) }) {
                Text(if (mergeTarget != null) "Zusammenführen" else "Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
        },
    )
}
