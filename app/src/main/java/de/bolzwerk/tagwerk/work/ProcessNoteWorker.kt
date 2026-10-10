package de.bolzwerk.tagwerk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.bolzwerk.tagwerk.TagwerkApp
import de.bolzwerk.tagwerk.domain.NoteProcessor

class ProcessNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)
        if (noteId < 0) return Result.failure()
        val processor = (applicationContext as TagwerkApp).container.noteProcessor
        return when (processor.process(noteId, isLastAttempt = runAttemptCount >= MAX_ATTEMPTS)) {
            NoteProcessor.Outcome.DONE -> Result.success()
            NoteProcessor.Outcome.RETRY -> Result.retry()
            NoteProcessor.Outcome.FAILED -> Result.failure()
        }
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"
        private const val MAX_ATTEMPTS = 8
    }
}
