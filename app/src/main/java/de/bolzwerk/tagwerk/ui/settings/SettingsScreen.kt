package de.bolzwerk.tagwerk.ui.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import de.bolzwerk.tagwerk.domain.ModelCatalog
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.bolzwerk.tagwerk.data.settings.AudioRetention
import de.bolzwerk.tagwerk.data.settings.CategorizationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenCategories: () -> Unit) {
    val draft = viewModel.draft
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    var showKey by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("KI-Verbindung")
            Text(
                "Jeder OpenAI-kompatible Endpunkt funktioniert. Audio und Text werden zur Verarbeitung an diese Adresse gesendet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.baseUrl,
                onValueChange = { v -> viewModel.edit { it.copy(baseUrl = v) } },
                label = { Text("Base-URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = if (draft.baseUrl.trim().startsWith("http://")) {
                    { Text("Achtung: unverschlüsselte Verbindung – der API-Key wird im Klartext übertragen.") }
                } else null,
                isError = draft.baseUrl.trim().startsWith("http://"),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { v -> viewModel.edit { it.copy(apiKey = v) } },
                label = { Text("API-Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Key anzeigen")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Eigener Endpunkt für die Transkription – nur ausfüllen, wenn die Transkription über einen anderen Server laufen soll als der Text " +
                    "(z. B. weil dein Server keine Audio-Schnittstelle hat). Leer = Endpunkt von oben.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.transcriptionBaseUrl,
                onValueChange = { v -> viewModel.edit { it.copy(transcriptionBaseUrl = v) } },
                label = { Text("Base-URL für Transkription (optional)") },
                placeholder = { Text(draft.baseUrl.ifBlank { "wie oben" }) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.transcriptionApiKey,
                onValueChange = { v -> viewModel.edit { it.copy(transcriptionApiKey = v) } },
                label = { Text("API-Key für Transkription (optional)") },
                placeholder = { Text("wie oben") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::saveAndTest) { Text("Speichern & testen") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = viewModel::save, enabled = viewModel.hasUnsavedChanges) { Text("Speichern") }
            }
            ConnectionTestResult(viewModel.connectionTest)
            viewModel.savedMessage?.takeIf { viewModel.connectionTest == ConnectionTest.Idle }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            SectionTitle("Modelle")
            Text(
                "Die Liste kommt vom Server und ist nach Aufgabe gefiltert. Eigene Eingaben sind weiterhin möglich.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ModelField(
                label = "Modell für Text",
                value = draft.chatModel,
                onValueChange = { v -> viewModel.edit { it.copy(chatModel = v) } },
                onSelect = { v -> viewModel.edit { it.copy(chatModel = v) }; viewModel.save() },
                available = viewModel.chatEndpointModels,
                purpose = ModelCatalog.Purpose.CHAT,
            )
            ModelField(
                label = "Modell für Transkription",
                value = draft.transcriptionModel,
                onValueChange = { v -> viewModel.edit { it.copy(transcriptionModel = v) } },
                onSelect = { v -> viewModel.edit { it.copy(transcriptionModel = v) }; viewModel.save() },
                available = viewModel.transcriptionEndpointModels,
                purpose = ModelCatalog.Purpose.TRANSCRIPTION,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::refreshModels, enabled = draft.isAiConfigured && !viewModel.loadingModels) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Modellliste laden")
                }
                if (viewModel.loadingModels) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::save, enabled = viewModel.hasUnsavedChanges) { Text("Speichern") }
            }
            viewModel.modelsError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            SectionTitle("Transkription")
            OutlinedTextField(
                value = draft.glossary,
                onValueChange = { v -> viewModel.edit { it.copy(glossary = v) } },
                label = { Text("Glossar") },
                placeholder = { Text("z. B. Namen, Orte, Fachbegriffe") },
                supportingText = { Text("Hilft der Spracherkennung bei ungewöhnlichen Wörtern. Mit „Speichern“ übernehmen.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Kategorien")
            RadioOption(
                selected = draft.categorizationMode == CategorizationMode.ASSIGN,
                title = "Automatisch vergeben",
                subtitle = "Die KI ordnet neue Notizen direkt ein.",
                onClick = { viewModel.setCategorizationMode(CategorizationMode.ASSIGN) },
            )
            RadioOption(
                selected = draft.categorizationMode == CategorizationMode.SUGGEST,
                title = "Nur vorschlagen",
                subtitle = "Vorschläge erscheinen unter „Zu bestätigen“.",
                onClick = { viewModel.setCategorizationMode(CategorizationMode.SUGGEST) },
            )
            ListItem(
                headlineContent = { Text("Kategorien verwalten") },
                supportingContent = { Text("Umbenennen, zusammenführen, Farben") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.selectable(selected = false, onClick = onOpenCategories, role = Role.Button),
            )

            SectionTitle("Originalaufnahmen aufbewahren")
            listOf(
                AudioRetention.DELETE_AFTER_TRANSCRIPTION to "Nach der Transkription löschen",
                AudioRetention.DAYS_30 to "30 Tage",
                AudioRetention.DAYS_90 to "90 Tage",
                AudioRetention.FOREVER to "Unbegrenzt",
            ).forEach { (retention, label) ->
                RadioOption(
                    selected = draft.audioRetention == retention,
                    title = label,
                    onClick = { viewModel.setAudioRetention(retention) },
                )
            }

            SectionTitle("Verbrauch in diesem Monat")
            val minutes = (usage.audioSeconds ?: 0.0) / 60.0
            Text("Audio: %.1f Minuten".format(minutes), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tokens: ${usage.inputTokens ?: 0} Eingabe · ${usage.outputTokens ?: 0} Ausgabe",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionTitle("Datenschutz")
            Text(
                "Alle Notizen und Aufnahmen liegen verschlüsselt auf diesem Gerät. Es gibt keine Synchronisation und kein " +
                    "Cloud-Backup. Nur zur Verarbeitung wird der jeweilige Inhalt an den oben eingestellten KI-Endpunkt gesendet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Modellfeld mit Auswahlliste: zeigt die für die Aufgabe passenden Modelle des Servers,
 * auf Wunsch alle. Freie Eingabe bleibt möglich (z. B. wenn der Server keine Liste liefert).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    available: List<String>,
    purpose: ModelCatalog.Purpose,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val suitable = remember(available, purpose) { ModelCatalog.forPurpose(available, purpose) }
    // Erkennt das Namensmuster kein Modell als passend, lieber alle zeigen als keine.
    val effectiveShowAll = showAll || suitable.isEmpty()
    val options = remember(available, purpose, effectiveShowAll) {
        ModelCatalog.forPurpose(available, purpose, includeAll = effectiveShowAll)
    }
    val query = value.trim()
    val filtered = if (query.isEmpty() || options.any { it.equals(query, ignoreCase = true) }) {
        options
    } else {
        options.filter { it.contains(query, ignoreCase = true) }.ifEmpty { options }
    }
    val unknown = available.isNotEmpty() && query.isNotEmpty() && available.none { it.equals(query, ignoreCase = true) }
    val menuOpen = expanded && available.isNotEmpty()

    ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { if (available.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
            supportingText = when {
                unknown -> { { Text("Nicht in der Modellliste des Servers") } }
                available.isNotEmpty() -> { { Text("${suitable.size} passende von ${available.size} Modellen") } }
                else -> null
            },
            isError = unknown,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = available.isNotEmpty()),
        )
        ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { expanded = false }) {
            filtered.forEach { model ->
                val fits = ModelCatalog.matches(model, purpose)
                DropdownMenuItem(
                    text = {
                        Text(
                            model,
                            color = if (fits) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon = if (model.equals(query, ignoreCase = true)) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        onSelect(model)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            if (suitable.isNotEmpty() && suitable.size < available.size) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            if (showAll) "Nur passende Modelle anzeigen" else "Alle ${available.size} Modelle anzeigen",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { showAll = !showAll },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun RadioOption(selected: Boolean, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ConnectionTestResult(test: ConnectionTest) {
    when (test) {
        ConnectionTest.Idle -> Unit
        ConnectionTest.Running -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Verbindung wird geprüft …", style = MaterialTheme.typography.bodySmall)
        }
        is ConnectionTest.Done -> Column(Modifier.padding(top = 8.dp)) {
            test.checks.forEach { check ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (check.ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (check.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${check.name}: ${check.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (check.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
