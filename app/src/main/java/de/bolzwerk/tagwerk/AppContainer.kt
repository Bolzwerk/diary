package de.bolzwerk.tagwerk

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import de.bolzwerk.tagwerk.audio.AudioStore
import de.bolzwerk.tagwerk.core.security.FileCrypto
import de.bolzwerk.tagwerk.core.security.SecretStore
import de.bolzwerk.tagwerk.data.ai.AiProvider
import de.bolzwerk.tagwerk.data.ai.OpenAiProvider
import de.bolzwerk.tagwerk.data.db.TagwerkDatabase
import de.bolzwerk.tagwerk.data.repo.CategoryRepository
import de.bolzwerk.tagwerk.data.repo.NoteRepository
import de.bolzwerk.tagwerk.data.settings.SettingsRepository
import de.bolzwerk.tagwerk.domain.NoteProcessor
import de.bolzwerk.tagwerk.work.WorkScheduler

/** Einfache manuelle Dependency Injection – für eine Ein-Personen-App ausreichend und ohne Codegenerierung. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lebt so lange wie der Prozess – für Arbeiten, die einen Screen-Wechsel überdauern sollen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val secretStore = SecretStore(appContext)

    val settingsRepository = SettingsRepository(appContext, secretStore)

    val database: TagwerkDatabase by lazy {
        TagwerkDatabase.create(appContext, secretStore.getOrCreateKeyBytes(KEY_DB_PASSPHRASE))
    }

    val audioStore: AudioStore by lazy {
        AudioStore(appContext, FileCrypto(secretStore.getOrCreateKeyBytes(KEY_AUDIO)))
    }

    val aiProvider: AiProvider by lazy { OpenAiProvider() }

    val workScheduler: WorkScheduler by lazy { WorkScheduler(appContext) }

    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database) }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(database, audioStore, categoryRepository, workScheduler)
    }

    val noteProcessor: NoteProcessor by lazy {
        NoteProcessor(database, aiProvider, settingsRepository, categoryRepository, audioStore)
    }

    private companion object {
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val KEY_AUDIO = "audio_key"
    }
}
