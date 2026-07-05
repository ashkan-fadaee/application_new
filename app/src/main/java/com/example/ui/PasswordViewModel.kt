package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BackupManager
import com.example.security.DecryptedFields
import com.example.data.PasswordRecord
import com.example.data.PasswordRepository
import com.example.security.SecurityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

class PasswordViewModel(private val repository: PasswordRepository) : ViewModel() {

    private val PREFS_SETTINGS = "keyban_settings_prefs"
    private val KEY_THEME = "theme_mode"
    private val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
    private val KEY_CLIPBOARD_TIMEOUT = "clipboard_timeout"
    private val KEY_CATEGORIES = "custom_categories"
    private val KEY_SECURE_MODE = "secure_mode_enabled"

    // --- State Streams ---

    private val _secureModeEnabled = MutableStateFlow(false)
    val secureModeEnabled: StateFlow<Boolean> = _secureModeEnabled.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private var ignoreNextPause = false

    fun setIgnoreNextPause(ignore: Boolean) {
        ignoreNextPause = ignore
    }

    private val _isMasterPasswordCreated = MutableStateFlow(false)
    val isMasterPasswordCreated: StateFlow<Boolean> = _isMasterPasswordCreated.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _themeMode = MutableStateFlow("system") // "system", "light", "dark"
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _autoLockTimeout = MutableStateFlow(60) // in seconds, default 1 min
    val autoLockTimeout: StateFlow<Int> = _autoLockTimeout.asStateFlow()

    private val _clipboardTimeout = MutableStateFlow(30) // in seconds, default 30s
    val clipboardTimeout: StateFlow<Int> = _clipboardTimeout.asStateFlow()

    val rawRecords: StateFlow<List<PasswordRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var activeSecretKey: SecretKey? = null
    private var clipboardClearJob: Job? = null
    private var lastPauseTimestamp: Long = 0

    fun initSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        _themeMode.value = prefs.getString(KEY_THEME, "system") ?: "system"
        _autoLockTimeout.value = prefs.getInt(KEY_AUTO_LOCK_TIMEOUT, 60)
        _clipboardTimeout.value = prefs.getInt(KEY_CLIPBOARD_TIMEOUT, 30)
        _secureModeEnabled.value = prefs.getBoolean(KEY_SECURE_MODE, false) // Default to false so streaming emulator is visible
        _isMasterPasswordCreated.value = SecurityManager.isMasterPasswordCreated(context)

        val defaultCats = listOf("شخصی", "بانکی", "شبکه‌های اجتماعی", "کاری", "سایر")
        val savedCats = prefs.getString(KEY_CATEGORIES, null)
        if (savedCats != null) {
            _categories.value = savedCats.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            _categories.value = defaultCats
            prefs.edit().putString(KEY_CATEGORIES, defaultCats.joinToString(",")).apply()
        }
    }

    fun setSecureModeEnabled(context: Context, enabled: Boolean) {
        _secureModeEnabled.value = enabled
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SECURE_MODE, enabled)
            .apply()
    }

    fun addCategory(context: Context, newCat: String): Boolean {
        val trimmed = newCat.trim()
        if (trimmed.isEmpty() || _categories.value.contains(trimmed)) return false
        val newList = _categories.value + trimmed
        _categories.value = newList
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATEGORIES, newList.joinToString(","))
            .apply()
        return true
    }

    fun deleteCategory(context: Context, catToDelete: String): Boolean {
        if (!_categories.value.contains(catToDelete)) return false
        val newList = _categories.value - catToDelete
        _categories.value = newList
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATEGORIES, newList.joinToString(","))
            .apply()
        return true
    }

    // --- Authentication Actions ---

    fun login(context: Context, passwordStr: String): Boolean {
        val key = SecurityManager.verifyMasterPassword(context, passwordStr)
        return if (key != null) {
            activeSecretKey = key
            _isLoggedIn.value = true
            true
        } else {
            false
        }
    }

    fun loginWithBiometric(context: Context): Boolean {
        val savedMasterPassword = SecurityManager.getMasterPasswordFromBiometric(context)
        return if (savedMasterPassword != null) {
            login(context, savedMasterPassword)
        } else {
            false
        }
    }

    fun logout() {
        activeSecretKey = null
        _isLoggedIn.value = false
    }

    fun setupMasterPassword(context: Context, passwordStr: String) {
        val key = SecurityManager.setupMasterPassword(context, passwordStr)
        activeSecretKey = key
        _isMasterPasswordCreated.value = true
        _isLoggedIn.value = true
    }

    suspend fun changeMasterPassword(context: Context, newPasswordStr: String): Boolean {
        val currentKey = activeSecretKey ?: return false
        
        return try {
            // 1. Decrypt all data using old key
            val decryptedList = rawRecords.value.map { record ->
                record.id to decryptRecordFields(record, currentKey)
            }

            // 2. Setup new master password and derive new key
            val newKey = SecurityManager.setupMasterPassword(context, newPasswordStr)

            // 3. Re-encrypt all data using new key
            val encryptedRecords = decryptedList.map { (id, fields) ->
                PasswordRecord(
                    id = id,
                    websiteName = fields.websiteName,
                    encryptedUsername = if (fields.username.isNotEmpty()) SecurityManager.encrypt(fields.username, newKey) else "",
                    encryptedEmail = if (fields.email.isNotEmpty()) SecurityManager.encrypt(fields.email, newKey) else "",
                    encryptedPassword = if (fields.passwordStr.isNotEmpty()) SecurityManager.encrypt(fields.passwordStr, newKey) else "",
                    encryptedNotes = if (fields.notes.isNotEmpty()) SecurityManager.encrypt(fields.notes, newKey) else "",
                    category = fields.category,
                    isFavorite = fields.isFavorite,
                    createdAt = fields.createdAt,
                    lastModifiedAt = fields.lastModifiedAt
                )
            }

            // 4. Update database synchronously to prevent intermediate decryption errors
            repository.restoreBackup(encryptedRecords)

            // 5. Update biometric if enabled
            if (SecurityManager.isBiometricEnabled(context)) {
                SecurityManager.storeMasterPasswordForBiometric(context, newPasswordStr)
            }

            activeSecretKey = newKey
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- Background Lock Handling ---

    fun onAppPaused() {
        if (ignoreNextPause) {
            ignoreNextPause = false
            return
        }
        lastPauseTimestamp = System.currentTimeMillis()
    }

    fun onAppResumed() {
        if (!_isLoggedIn.value || activeSecretKey == null) return

        val now = System.currentTimeMillis()
        val pausedDurationSec = (now - lastPauseTimestamp) / 1000

        val timeout = _autoLockTimeout.value
        // If timeout == 0 (immediate) or paused duration exceeds timeout, lock app
        if (timeout == 0 || (lastPauseTimestamp > 0 && pausedDurationSec >= timeout)) {
            logout()
        }
        lastPauseTimestamp = 0
    }

    // --- Record Cryptography Map ---

    fun decryptRecordFields(record: PasswordRecord, key: SecretKey? = activeSecretKey): DecryptedFields {
        val k = key ?: return DecryptedFields(record.websiteName, "", "", "", "", record.category, record.isFavorite, record.createdAt, record.lastModifiedAt)
        return try {
            val username = if (record.encryptedUsername.isNotEmpty()) SecurityManager.decrypt(record.encryptedUsername, k) else ""
            val email = if (record.encryptedEmail.isNotEmpty()) SecurityManager.decrypt(record.encryptedEmail, k) else ""
            val password = if (record.encryptedPassword.isNotEmpty()) SecurityManager.decrypt(record.encryptedPassword, k) else ""
            val notes = if (record.encryptedNotes.isNotEmpty()) SecurityManager.decrypt(record.encryptedNotes, k) else ""
            
            DecryptedFields(
                websiteName = record.websiteName,
                username = username,
                email = email,
                passwordStr = password,
                notes = notes,
                category = record.category,
                isFavorite = record.isFavorite,
                createdAt = record.createdAt,
                lastModifiedAt = record.lastModifiedAt
            )
        } catch (e: Exception) {
            DecryptedFields(record.websiteName, "خطا در رمزگشایی", "خطا در رمزگشایی", "خطا در رمزگشایی", "خطا در رمزگشایی", record.category, record.isFavorite, record.createdAt, record.lastModifiedAt)
        }
    }

    fun addRecord(
        websiteName: String,
        username: String,
        email: String,
        passwordStr: String,
        notes: String,
        category: String,
        isFavorite: Boolean
    ) {
        val key = activeSecretKey ?: return
        viewModelScope.launch {
            val record = PasswordRecord(
                websiteName = websiteName,
                encryptedUsername = if (username.isNotEmpty()) SecurityManager.encrypt(username, key) else "",
                encryptedEmail = if (email.isNotEmpty()) SecurityManager.encrypt(email, key) else "",
                encryptedPassword = if (passwordStr.isNotEmpty()) SecurityManager.encrypt(passwordStr, key) else "",
                encryptedNotes = if (notes.isNotEmpty()) SecurityManager.encrypt(notes, key) else "",
                category = category,
                isFavorite = isFavorite,
                createdAt = System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis()
            )
            repository.insertRecord(record)
        }
    }

    fun updateRecord(
        id: Int,
        websiteName: String,
        username: String,
        email: String,
        passwordStr: String,
        notes: String,
        category: String,
        isFavorite: Boolean,
        createdAt: Long
    ) {
        val key = activeSecretKey ?: return
        viewModelScope.launch {
            val record = PasswordRecord(
                id = id,
                websiteName = websiteName,
                encryptedUsername = if (username.isNotEmpty()) SecurityManager.encrypt(username, key) else "",
                encryptedEmail = if (email.isNotEmpty()) SecurityManager.encrypt(email, key) else "",
                encryptedPassword = if (passwordStr.isNotEmpty()) SecurityManager.encrypt(passwordStr, key) else "",
                encryptedNotes = if (notes.isNotEmpty()) SecurityManager.encrypt(notes, key) else "",
                category = category,
                isFavorite = isFavorite,
                createdAt = createdAt,
                lastModifiedAt = System.currentTimeMillis()
            )
            repository.updateRecord(record)
        }
    }

    fun toggleFavorite(record: PasswordRecord) {
        viewModelScope.launch {
            repository.updateRecord(record.copy(isFavorite = !record.isFavorite))
        }
    }

    fun deleteRecord(recordId: Int) {
        viewModelScope.launch {
            repository.deleteRecordById(recordId)
        }
    }

    // --- Clipboard Security ---

    fun copyToClipboard(context: Context, label: String, text: String, isSensitive: Boolean) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)

        // Automatic clear timeout
        if (isSensitive && _clipboardTimeout.value > 0) {
            clipboardClearJob?.cancel()
            clipboardClearJob = viewModelScope.launch {
                delay(_clipboardTimeout.value * 1000L)
                try {
                    val currentClip = clipboard.primaryClip
                    if (currentClip != null && currentClip.itemCount > 0) {
                        val firstItem = currentClip.getItemAt(0).text
                        if (firstItem == text) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                } catch (e: Exception) {
                    // Safe clipboard clear on background thread to prevent SecurityException on Android 10+
                    try {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    } catch (ex: Exception) {
                        // ignore secondary failure
                    }
                }
            }
        }
    }

    // --- Backup & Restore Actions ---

    fun exportEncryptedBackup(): String? {
        val key = activeSecretKey ?: return null
        return BackupManager.exportBackup(rawRecords.value, key)
    }

    fun restoreEncryptedBackup(context: Context, encryptedBackup: String): String {
        val key = activeSecretKey ?: return "ابتدا وارد برنامه شوید"
        val parsedRecords = BackupManager.decryptBackup(encryptedBackup, key)
            ?: return "رمزگشایی شکست خورد. رمز عبور مستر فعلی شما با رمز فایل بکاپ همخوانی ندارد یا فایل مخدوش است."
        
        viewModelScope.launch {
            repository.restoreBackup(parsedRecords)
        }
        return "بازیابی اطلاعات با موفقیت انجام شد."
    }

    // --- Settings Storage Actions ---

    fun updateThemeMode(context: Context, mode: String) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode)
            .apply()
    }

    fun updateAutoLockTimeout(context: Context, seconds: Int) {
        _autoLockTimeout.value = seconds
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AUTO_LOCK_TIMEOUT, seconds)
            .apply()
    }

    fun updateClipboardTimeout(context: Context, seconds: Int) {
        _clipboardTimeout.value = seconds
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CLIPBOARD_TIMEOUT, seconds)
            .apply()
    }

    fun toggleBiometricSetting(context: Context, enabled: Boolean, currentMasterPassword: String? = null): Boolean {
        if (enabled) {
            if (currentMasterPassword == null) return false
            val success = SecurityManager.storeMasterPasswordForBiometric(context, currentMasterPassword)
            if (success) {
                SecurityManager.setBiometricEnabled(context, true)
                return true
            }
            return false
        } else {
            SecurityManager.setBiometricEnabled(context, false)
            return true
        }
    }
}

// ViewModel Factory to satisfy Clean Architecture / Constructor Injection without external framework
class PasswordViewModelFactory(private val repository: PasswordRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PasswordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PasswordViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
