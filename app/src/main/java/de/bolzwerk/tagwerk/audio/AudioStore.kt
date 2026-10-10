package de.bolzwerk.tagwerk.audio

import android.content.Context
import de.bolzwerk.tagwerk.core.security.FileCrypto
import java.io.File
import java.util.UUID

/** Legt Aufnahmen verschlüsselt im App-internen Speicher ab. */
class AudioStore(context: Context, private val crypto: FileCrypto) {

    private val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
    private val playbackDir = File(context.cacheDir, "playback").apply { mkdirs() }

    /** Verschlüsselt die Rohaufnahme, löscht sie und liefert den Dateinamen der verschlüsselten Kopie. */
    fun importRecording(plainFile: File): String {
        val fileName = "${UUID.randomUUID()}.m4a.enc"
        crypto.encryptFile(plainFile, File(audioDir, fileName))
        plainFile.delete()
        return fileName
    }

    fun read(fileName: String): ByteArray = crypto.decryptFile(File(audioDir, fileName))

    /** Entschlüsselt in eine temporäre Datei zum Abspielen. Aufrufer löscht sie mit [clearPlaybackCache]. */
    fun decryptForPlayback(fileName: String): File {
        val target = File(playbackDir, "play-${System.nanoTime()}.m4a")
        target.writeBytes(read(fileName))
        return target
    }

    fun clearPlaybackCache() {
        playbackDir.listFiles()?.forEach { it.delete() }
    }

    fun delete(fileName: String) {
        File(audioDir, fileName).delete()
    }
}
