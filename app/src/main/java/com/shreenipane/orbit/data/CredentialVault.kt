package com.shreenipane.orbit.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The credential locker.
 *
 * Your VTOP registration number and password are encrypted with AES-256-GCM.
 * The key is generated inside Android Keystore (hardware-backed on most
 * phones), can't be exported, and can only be used for a single operation
 * right after a strong biometric (fingerprint) check. What's stored on disk
 * is only ciphertext + IV; GCM's authentication tag makes any tampering fail
 * to decrypt.
 *
 * The saved login stays until you sign out, with one exception Android
 * enforces: enrolling a new fingerprint invalidates the key, and you'll be
 * asked for your password once more.
 */
class CredentialVault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    data class Credentials(val regNo: String, val password: String)

    val hasSaved: Boolean get() = prefs.contains(KEY_BLOB) && keyExists()

    /** Shown in Settings without unlocking; the password itself never is. */
    val savedRegNo: String? get() = if (hasSaved) prefs.getString(KEY_REG, null) else null

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keyExists() = runCatching { keyStore().containsAlias(ALIAS) }.getOrDefault(false)

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 30) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    /** A cipher to hand to BiometricPrompt before [save]. */
    fun encryptCipher(): Cipher = try {
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        clear()
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
    }

    /** A cipher to hand to BiometricPrompt before [open]; null if nothing is saved or the key was invalidated. */
    fun decryptCipher(): Cipher? {
        val iv = prefs.getString(KEY_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            null
        }
    }

    /** [cipher] must come from [encryptCipher] and be unlocked by BiometricPrompt. */
    fun save(cipher: Cipher, creds: Credentials) {
        val plain = (creds.regNo + "\u0000" + creds.password).toByteArray(Charsets.UTF_8)
        val blob = cipher.doFinal(plain)
        plain.fill(0)
        prefs.edit()
            .putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_REG, creds.regNo.uppercase())
            .apply()
    }

    /** [cipher] must come from [decryptCipher] and be unlocked by BiometricPrompt. */
    fun open(cipher: Cipher): Credentials? {
        val blob = prefs.getString(KEY_BLOB, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val plain = runCatching { cipher.doFinal(blob) }.getOrNull() ?: return null
        val text = String(plain, Charsets.UTF_8)
        plain.fill(0)
        val i = text.indexOf('\u0000')
        return if (i <= 0) null else Credentials(text.substring(0, i), text.substring(i + 1))
    }

    fun clear() {
        prefs.edit().clear().apply()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "orbit_vtop_login"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BLOB = "blob"
        const val KEY_IV = "iv"
        const val KEY_REG = "reg"
    }
}
