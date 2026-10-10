package de.bolzwerk.tagwerk.core.security

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM-Verschlüsselung für Dateien (Audioaufnahmen). Der Schlüssel liegt geschützt im [SecretStore]. */
class FileCrypto(private val keyBytes: ByteArray) {

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, data, 0, IV_LENGTH))
        return cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH)
    }

    fun encryptFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        target.writeBytes(encrypt(source.readBytes()))
    }

    fun decryptFile(source: File): ByteArray = decrypt(source.readBytes())

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
