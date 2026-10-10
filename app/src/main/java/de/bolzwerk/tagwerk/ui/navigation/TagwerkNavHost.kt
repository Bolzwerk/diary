package de.bolzwerk.tagwerk.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.bolzwerk.tagwerk.AppContainer
import de.bolzwerk.tagwerk.TagwerkApp
import de.bolzwerk.tagwerk.ui.categories.CategoriesScreen
import de.bolzwerk.tagwerk.ui.categories.CategoriesViewModel
import de.bolzwerk.tagwerk.ui.home.HomeScreen
import de.bolzwerk.tagwerk.ui.home.HomeViewModel
import de.bolzwerk.tagwerk.ui.notedetail.NewNoteScreen
import de.bolzwerk.tagwerk.ui.notedetail.NoteDetailScreen
import de.bolzwerk.tagwerk.ui.notedetail.NoteDetailViewModel
import de.bolzwerk.tagwerk.ui.notes.NotesScreen
import de.bolzwerk.tagwerk.ui.notes.NotesViewModel
import de.bolzwerk.tagwerk.ui.record.RecordButton
import de.bolzwerk.tagwerk.ui.record.RecordPanel
import de.bolzwerk.tagwerk.ui.record.RecordingUiState
import de.bolzwerk.tagwerk.ui.record.RecordingViewModel
import de.bolzwerk.tagwerk.ui.settings.SettingsScreen
import de.bolzwerk.tagwerk.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object NotesRoute
@Serializable data class NoteDetailRoute(val id: Long)
@Serializable data object NewNoteRoute
@Serializable data object SettingsRoute
@Serializable data object CategoriesRoute

private data class TopLevelDestination(val route: Any, val label: String, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, "Heute", Icons.Filled.WbSunny),
    TopLevelDestination(NotesRoute, "Notizen", Icons.AutoMirrored.Filled.Notes),
)

@Composable
private fun appContainer(): AppContainer = (LocalContext.current.applicationContext as TagwerkApp).container

@Composable
fun TagwerkNavHost() {
    val container = appContainer()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val isTopLevel = topLevelDestinations.any { top -> destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true }
    val snackbarHostState = remember { SnackbarHostState() }

    // Auf Activity-Ebene, damit eine laufende Aufnahme Navigation übersteht.
    val recording: RecordingViewModel = viewModel()
    val recordingState by recording.state.collectAsStateWithLifecycle()
    val savedNote by recording.savedNote.collectAsStateWithLifecycle()

    BackHandler(enabled = recordingState !is RecordingUiState.Idle) {
        if (recordingState is RecordingUiState.Recording) recording.cancel() else recording.dismiss()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isTopLevel) {
                    NavigationBar {
                        topLevelDestinations.forEach { top ->
                            val selected = destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(top.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(top.icon, contentDescription = null) },
                                label = { Text(top.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (isTopLevel) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SmallFloatingActionButton(
                            onClick = { navController.navigate(NewNoteRoute) },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) { Icon(Icons.Filled.Edit, contentDescription = "Notiz schreiben") }
                        RecordButton(
                            isRecording = recordingState is RecordingUiState.Recording,
                            onPress = recording::onPress,
                            onRelease = recording::onRelease,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(navController, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        viewModel = viewModel { HomeViewModel(container) },
                        contentPadding = innerPadding,
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenNote = { navController.navigate(NoteDetailRoute(it)) },
                        onShowAllNotes = {
                            navController.navigate(NotesRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<NotesRoute> {
                    NotesScreen(
                        viewModel = viewModel { NotesViewModel(container) },
                        contentPadding = innerPadding,
                        snackbarHostState = snackbarHostState,
                        onOpenNote = { navController.navigate(NoteDetailRoute(it)) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                composable<NoteDetailRoute> { entry ->
                    val id = entry.toRoute<NoteDetailRoute>().id
                    NoteDetailScreen(
                        viewModel = viewModel { NoteDetailViewModel(container, id) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<NewNoteRoute> {
                    NewNoteScreen(container.noteRepository, onDone = { navController.popBackStack() })
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = viewModel { SettingsViewModel(container) },
                        onBack = { navController.popBackStack() },
                        onOpenCategories = { navController.navigate(CategoriesRoute) },
                    )
                }
                composable<CategoriesRoute> {
                    CategoriesScreen(
                        viewModel = viewModel { CategoriesViewModel(container) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = recordingState !is RecordingUiState.Idle,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            RecordPanel(
                state = recordingState,
                savedNote = savedNote,
                onCancel = recording::cancel,
                onTogglePause = recording::togglePause,
                onFinish = recording::finish,
                onDismiss = recording::dismiss,
                onOpenNote = { id ->
                    recording.dismiss()
                    navController.navigate(NoteDetailRoute(id))
                },
                onConfirmCategory = { noteId, categoryId ->
                    container.appScope.launch { container.noteRepository.confirmCategory(noteId, categoryId) }
                },
                onRemoveCategory = { noteId, categoryId ->
                    container.appScope.launch { container.noteRepository.removeCategory(noteId, categoryId) }
                },
            )
        }
    }
}
