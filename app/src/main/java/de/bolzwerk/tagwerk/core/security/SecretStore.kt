package de.bolzwerk.tagwerk.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselt kleine Geheimnisse (API-Key, Datenbank-Passphrase, Datei-Schlüssel)
 * mit einem nicht exportierbaren AES-Schlüssel im Android Keystore.
 */
class SecretStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putString(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(name).apply()
        } else {
            prefs.edit().putString(name, encrypt(value.toByteArray(Charsets.UTF_8))).apply()
        }
    }

    fun getString(name: String): String? =
        prefs.getString(name, null)?.let { String(decrypt(it), Charsets.UTF_8) }

    /** Liefert zufällige Schlüsselbytes, die beim ersten Aufruf erzeugt und verschlüsselt abgelegt werden. */
    @Synchronized
    fun getOrCreateKeyBytes(name: String, length: Int = 32): ByteArray {
        prefs.getString(name, null)?.let { return decrypt(it) }
        val bytes = ByteArray(length).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(name, encrypt(bytes)).commit()
        return bytes
    }

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val out = cipher.iv + cipher.doFinal(plain)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, data, 0, IV_LENGTH))
        return cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH)
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tagwerk_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
