package de.bolzwerk.tagwerk.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Dünne Hülle um [MediaRecorder]: AAC/M4A, mono, sprachoptimiert und klein. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    private var pausedTotal = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Aufnahme läuft bereits" }
        val file = File(context.cacheDir, "rec-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(32_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaRecorder.release()
            file.delete()
            throw e
        }
        recorder = mediaRecorder
        outputFile = file
        startedAt = SystemClock.elapsedRealtime()
        pausedAt = 0L
        pausedTotal = 0L
        return file
    }

    fun pause() {
        recorder?.pause()
        pausedAt = SystemClock.elapsedRealtime()
    }

    fun resume() {
        recorder?.resume()
        if (pausedAt != 0L) pausedTotal += SystemClock.elapsedRealtime() - pausedAt
        pausedAt = 0L
    }

    fun elapsedMs(): Long {
        if (recorder == null) return 0L
        val now = if (pausedAt != 0L) pausedAt else SystemClock.elapsedRealtime()
        return now - startedAt - pausedTotal
    }

    /** Pegel 0..1 für die Wellenform-Anzeige. */
    fun amplitude(): Float = (recorder?.maxAmplitude ?: 0) / 32_767f

    /** Beendet die Aufnahme. Liefert Datei und Dauer oder `null`, wenn nichts Verwertbares aufgenommen wurde. */
    fun stop(): Pair<File, Long>? {
        val mediaRecorder = recorder ?: return null
        val file = outputFile
        val duration = elapsedMs()
        recorder = null
        outputFile = null
        val ok = runCatching { mediaRecorder.stop() }.isSuccess
        mediaRecorder.release()
        if (!ok || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }
        return file to duration
    }

    fun cancel() {
        val mediaRecorder = recorder ?: return
        recorder = null
        runCatching { mediaRecorder.stop() }
        mediaRecorder.release()
        outputFile?.delete()
        outputFile = null
    }
}
