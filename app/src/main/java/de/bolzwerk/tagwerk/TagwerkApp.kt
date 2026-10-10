package de.bolzwerk.tagwerk

import android.app.Application
import kotlinx.coroutines.launch

class TagwerkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.workScheduler.schedulePeriodicCleanup()
        container.appScope.launch { container.noteRepository.reschedulePending(replace = false) }
    }
}
