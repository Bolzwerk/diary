package de.bolzwerk.tagwerk.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Plant Hintergrundarbeit: KI-Verarbeitung (wartet auf Netz) und das Aufräumen alter Aufnahmen. */
class WorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun enqueueProcessing(noteId: Long, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<ProcessNoteWorker>()
            .setInputData(workDataOf(ProcessNoteWorker.KEY_NOTE_ID to noteId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_PROCESSING)
            .build()
        workManager.enqueueUniqueWork(
            "process-note-$noteId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun schedulePeriodicCleanup() {
        val request = PeriodicWorkRequestBuilder<AudioCleanupWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork("audio-cleanup", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val TAG_PROCESSING = "note-processing"
    }
}
