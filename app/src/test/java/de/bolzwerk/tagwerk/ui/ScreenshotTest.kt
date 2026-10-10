package de.bolzwerk.tagwerk.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import de.bolzwerk.tagwerk.data.db.AssignedBy
import de.bolzwerk.tagwerk.data.db.CategoryEntity
import de.bolzwerk.tagwerk.data.db.NoteSource
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.db.ProcessingState
import de.bolzwerk.tagwerk.data.repo.NoteCategory
import de.bolzwerk.tagwerk.data.repo.NoteItem
import de.bolzwerk.tagwerk.ui.home.HomeContent
import de.bolzwerk.tagwerk.ui.home.HomeUiState
import de.bolzwerk.tagwerk.ui.notes.NotesContent
import de.bolzwerk.tagwerk.ui.notes.NotesUiState
import de.bolzwerk.tagwerk.ui.record.RecordButton
import de.bolzwerk.tagwerk.ui.record.RecordPanel
import de.bolzwerk.tagwerk.ui.record.RecordingUiState
import de.bolzwerk.tagwerk.ui.theme.TagwerkTheme
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin

private object NoOpResultRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) = Unit
    }
}

/** Rendert die wichtigsten Screens ohne Gerät (Paparazzi) – zur visuellen Kontrolle. */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10, 18, 30) }.timeInMillis

    private val auto = NoteCategory(1, "Auto", 0, AssignedBy.AI, confirmed = true)
    private val arbeit = NoteCategory(2, "Arbeit", 2, AssignedBy.USER, confirmed = true)
    private val finanzenSuggestion = NoteCategory(3, "Finanzen", 1, AssignedBy.AI, confirmed = false)
    private val familie = NoteCategory(4, "Familie", 5, AssignedBy.AI, confirmed = true)

    private fun note(
        id: Long,
        title: String,
        text: String,
        categories: List<NoteCategory>,
        status: NoteStatus = NoteStatus.OPEN,
        state: ProcessingState = ProcessingState.DONE,
        source: NoteSource = NoteSource.DICTATED,
    ) = NoteItem(id, title, text, status, source, source == NoteSource.DICTATED, null, state, null, now, now, categories)

    private val notes = listOf(
        note(1, "Reifenwechsel buchen", "Reifenwechsel bis Ende Oktober in der Werkstatt buchen, Winterreifen sind im Keller.", listOf(auto)),
        note(2, "Steuerberater zurückrufen", "Steuerberater wegen der Unterlagen für 2025 zurückrufen.", listOf(finanzenSuggestion)),
        note(3, "", "", emptyList(), state = ProcessingState.PENDING_TRANSCRIPTION),
        note(4, "Präsentation vorbereiten", "Folien für das Quartalsmeeting am Donnerstag vorbereiten.", listOf(arbeit), source = NoteSource.TYPED),
        note(5, "Geschenk für Anna", "Annas Geburtstag am 3.11. – Idee: Kochkurs.", listOf(familie)),
    )

    @Composable
    private fun Screen(dark: Boolean = false, content: @Composable BoxScope.() -> Unit) {
        TagwerkTheme(darkTheme = dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Composable
    private fun Home() = HomeContent(
        state = HomeUiState(
            loading = false,
            aiConfigured = true,
            toConfirm = notes.filter { it.hasSuggestions },
            openNotes = notes,
            openCount = 7,
            processingCount = 1,
        ),
        contentPadding = PaddingValues(),
        onOpenSettings = {}, onOpenNote = {}, onShowAllNotes = {}, onToggleDone = {},
        onConfirmCategory = { _, _ -> }, onRemoveCategory = { _, _ -> },
        today = java.util.Date(now),
    )

    @Test
    fun home() = paparazzi.snapshot {
        Screen {
            Home()
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoOpResultRegistryOwner) {
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                    RecordButton(isRecording = false, onPress = {}, onRelease = {})
                }
            }
        }
    }

    @Test
    fun homeDark() = paparazzi.snapshot { Screen(dark = true) { Home() } }

    @Test
    fun homeUnconfigured() = paparazzi.snapshot {
        Screen {
            HomeContent(
                state = HomeUiState(loading = false, aiConfigured = false),
                contentPadding = PaddingValues(),
                onOpenSettings = {}, onOpenNote = {}, onShowAllNotes = {}, onToggleDone = {},
                onConfirmCategory = { _, _ -> }, onRemoveCategory = { _, _ -> },
                today = java.util.Date(now),
            )
        }
    }

    @Test
    fun notes() = paparazzi.snapshot {
        Screen {
            NotesContent(
                state = NotesUiState(
                    loading = false,
                    notes = notes + note(6, "Milch kaufen", "Milch und Brot kaufen", emptyList(), status = NoteStatus.DONE),
                    categories = listOf(
                        CategoryEntity(2, "Arbeit", 2, AssignedBy.USER, now),
                        CategoryEntity(1, "Auto", 0, AssignedBy.AI, now),
                        CategoryEntity(4, "Familie", 5, AssignedBy.AI, now),
                        CategoryEntity(3, "Finanzen", 1, AssignedBy.AI, now),
                    ),
                ),
                contentPadding = PaddingValues(),
                onOpenNote = {}, onOpenSettings = {}, onQueryChange = {}, onStatusFilter = {},
                onCategoryFilter = {}, onToggleDone = {}, onDelete = {},
            )
        }
    }

    @Test
    fun recording() = paparazzi.snapshot {
        Screen {
            Home()
            RecordPanel(
                state = RecordingUiState.Recording(
                    elapsedMs = 14_000,
                    levels = List(48) { abs(sin(it / 3.0)).toFloat() * (0.3f + (it % 7) / 10f) },
                    paused = false,
                    holding = false,
                ),
                savedNote = null,
                onCancel = {}, onTogglePause = {}, onFinish = {}, onDismiss = {}, onOpenNote = {},
                onConfirmCategory = { _, _ -> }, onRemoveCategory = { _, _ -> },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    @Test
    fun saved() = paparazzi.snapshot {
        Screen {
            Home()
            RecordPanel(
                state = RecordingUiState.Saved(2, offline = false),
                savedNote = notes[1],
                onCancel = {}, onTogglePause = {}, onFinish = {}, onDismiss = {}, onOpenNote = {},
                onConfirmCategory = { _, _ -> }, onRemoveCategory = { _, _ -> },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
