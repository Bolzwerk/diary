package de.bolzwerk.tagwerk.audio

import android.media.MediaPlayer
import java.io.File

/** Spielt eine entschlüsselte Aufnahme ab und räumt die temporäre Datei danach auf. */
class AudioPlayer(private val onFinished: () -> Unit) {

    private var player: MediaPlayer? = null
    private var file: File? = null

    val isPlaying: Boolean get() = player?.isPlaying == true

    fun play(source: File) {
        stop()
        file = source
        player = MediaPlayer().apply {
            setDataSource(source.absolutePath)
            setOnCompletionListener {
                stop()
                onFinished()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        file?.delete()
        file = null
    }
}
