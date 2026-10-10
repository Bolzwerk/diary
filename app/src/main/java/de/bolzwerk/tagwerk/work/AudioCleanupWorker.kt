package de.bolzwerk.tagwerk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.bolzwerk.tagwerk.TagwerkApp
import java.util.concurrent.TimeUnit

/** Löscht Originalaufnahmen, die älter als die eingestellte Aufbewahrungsdauer sind. */
class AudioCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TagwerkApp).container
        container.audioStore.clearPlaybackCache()
        val days = container.settingsRepository.current.audioRetention.days ?: return Result.success()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val audioDao = container.database.audioDao()
        val pendingNoteAudio = container.database.noteDao().pending().mapNotNull { it.audioRecordingId }.toSet()
        for (recording in audioDao.olderThan(cutoff)) {
            if (recording.id in pendingNoteAudio) continue // noch nicht transkribiert
            audioDao.delete(recording.id)
            container.audioStore.delete(recording.fileName)
        }
        return Result.success()
    }
}
