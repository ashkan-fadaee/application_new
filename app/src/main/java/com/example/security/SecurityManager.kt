package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "KeybanBiometricKey"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128

    private const val PREFS_NAME = "keyban_secure_prefs"
    private const val PREF_MASTER_VERIFIER = "master_password_verifier"
    private const val PREF_SALT = "crypto_salt"
    private const val PREF_BIOMETRIC_ENCRYPTED_MASTER = "biometric_encrypted_master"
    private const val PREF_BIOMETRIC_IV = "biometric_iv"
    private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"

    private const val VERIFIER_PLAINTEXT = "KEYBAN_VERIFIER_SECURE_TOKEN_2026"

    private val secureRandom = SecureRandom()

    // --- Key Derivation (PBKDF2) ---

    fun getSalt(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltStr = prefs.getString(PREF_SALT, null)
        return if (saltStr != null) {
            Base64.decode(saltStr, Base64.DEFAULT)
        } else {
            val salt = ByteArray(16)
            secureRandom.nextBytes(salt)
            prefs.edit().putString(PREF_SALT, Base64.encodeToString(salt, Base64.DEFAULT)).apply()
            salt
        }
    }

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derived = factory.generateSecret(spec).encoded
        return SecretKeySpec(derived, "AES")
    }

    // --- Core AES-256-GCM Encryption/Decryption ---

    fun encrypt(plaintext: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Format: IV_base64:Ciphertext_base64
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivBase64:$ciphertextBase64"
    }

    fun decrypt(encryptedData: String, secretKey: SecretKey): String {
        val parts = encryptedData.split(":")
        if (parts.size != 2) throw IllegalArgumentException("فرمت اطلاعات رمزگذاری شده نادرست است")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    // --- Master Password Verification Flow ---

    fun setupMasterPassword(context: Context, password: String): SecretKey {
        val salt = getSalt(context)
        val secretKey = deriveKey(password, salt)
        val encryptedVerifier = encrypt(VERIFIER_PLAINTEXT, secretKey)
        
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_MASTER_VERIFIER, encryptedVerifier)
            .apply()
        
        return secretKey
    }

    fun verifyMasterPassword(context: Context, password: String): SecretKey? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedVerifier = prefs.getString(PREF_MASTER_VERIFIER, null) ?: return null
        val salt = getSalt(context)
        
        return try {
            val secretKey = deriveKey(password, salt)
            val decrypted = decrypt(encryptedVerifier, secretKey)
            if (decrypted == VERIFIER_PLAINTEXT) {
                secretKey
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isMasterPasswordCreated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREF_MASTER_VERIFIER)
    }

    fun changeMasterPassword(context: Context, oldKey: SecretKey, newPassword: String, recordsDecryptor: (SecretKey) -> List<Pair<Int, DecryptedFields>>, recordsEncryptor: (SecretKey, List<Pair<Int, DecryptedFields>>) -> Unit): Boolean {
        return try {
            // 1. Decrypt all data using old key
            val decryptedData = recordsDecryptor(oldKey)
            
            // 2. Setup new master password and derive new key
            val newKey = setupMasterPassword(context, newPassword)
            
            // 3. Re-encrypt all data using new key
            recordsEncryptor(newKey, decryptedData)
            
            // 4. Update biometric if enabled
            if (isBiometricEnabled(context)) {
                storeMasterPasswordForBiometric(context, newPassword)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- Android Keystore & Biometrics Storage Flow ---

    private fun getOrCreateAndroidKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun storeMasterPasswordForBiometric(context: Context, masterPassword: String): Boolean {
        return try {
            val key = getOrCreateAndroidKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(masterPassword.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_BIOMETRIC_ENCRYPTED_MASTER, Base64.encodeToString(ciphertext, Base64.DEFAULT))
                .putString(PREF_BIOMETRIC_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                .putBoolean(PREF_BIOMETRIC_ENABLED, true)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getMasterPasswordFromBiometric(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedMaster = prefs.getString(PREF_BIOMETRIC_ENCRYPTED_MASTER, null) ?: return null
        val ivStr = prefs.getString(PREF_BIOMETRIC_IV, null) ?: return null

        return try {
            val key = getOrCreateAndroidKeystoreKey()
            val ciphertext = Base64.decode(encryptedMaster, Base64.DEFAULT)
            val iv = Base64.decode(ivStr, Base64.DEFAULT)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun isBiometricEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!enabled) {
            prefs.edit()
                .remove(PREF_BIOMETRIC_ENCRYPTED_MASTER)
                .remove(PREF_BIOMETRIC_IV)
                .putBoolean(PREF_BIOMETRIC_ENABLED, false)
                .apply()
        } else {
            prefs.edit().putBoolean(PREF_BIOMETRIC_ENABLED, true).apply()
        }
    }

    // --- Password Generator Logic ---

    fun generatePassword(
        length: Int,
        useUppercase: Boolean,
        useLowercase: Boolean,
        useNumbers: Boolean,
        useSymbols: Boolean
    ): String {
        val uppercaseChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercaseChars = "abcdefghijklmnopqrstuvwxyz"
        val numberChars = "0123456789"
        val symbolChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val charPool = StringBuilder()
        val forcedChars = StringBuilder()

        if (useUppercase) {
            charPool.append(uppercaseChars)
            forcedChars.append(uppercaseChars[secureRandom.nextInt(uppercaseChars.length)])
        }
        if (useLowercase) {
            charPool.append(lowercaseChars)
            forcedChars.append(lowercaseChars[secureRandom.nextInt(lowercaseChars.length)])
        }
        if (useNumbers) {
            charPool.append(numberChars)
            forcedChars.append(numberChars[secureRandom.nextInt(numberChars.length)])
        }
        if (useSymbols) {
            charPool.append(symbolChars)
            forcedChars.append(symbolChars[secureRandom.nextInt(symbolChars.length)])
        }

        // Fallback if none are selected
        if (charPool.isEmpty()) {
            charPool.append(lowercaseChars).append(numberChars)
            forcedChars.append(lowercaseChars[secureRandom.nextInt(lowercaseChars.length)])
            forcedChars.append(numberChars[secureRandom.nextInt(numberChars.length)])
        }

        val result = StringBuilder()
        result.append(forcedChars)

        val remainingLength = length - forcedChars.length
        for (i in 0 until remainingLength) {
            val randomIndex = secureRandom.nextInt(charPool.length)
            result.append(charPool[randomIndex])
        }

        // Shuffle result
        val charList = result.toString().toList().shuffled(secureRandom)
        return charList.joinToString("")
    }

    // --- Password Strength Evaluator ---

    enum class Strength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }

    fun evaluateStrength(password: String): Strength {
        if (password.length < 6) return Strength.WEAK

        var score = 0
        if (password.length >= 8) score += 1
        if (password.length >= 12) score += 1
        if (password.any { it.isUpperCase() }) score += 1
        if (password.any { it.isLowerCase() }) score += 1
        if (password.any { it.isDigit() }) score += 1
        if (password.any { !it.isLetterOrDigit() }) score += 1

        return when {
            score <= 2 -> Strength.WEAK
            score == 3 || score == 4 -> Strength.MEDIUM
            score == 5 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }
}

// Data holder for decrypted records during master password change
data class DecryptedFields(
    val websiteName: String,
    val username: String,
    val email: String,
    val passwordStr: String,
    val notes: String,
    val category: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val lastModifiedAt: Long
)
