package com.example.data

import android.content.Context
import com.example.security.SecurityManager
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

object BackupManager {

    /**
     * Exports database records to an encrypted Base64 string.
     */
    fun exportBackup(records: List<PasswordRecord>, secretKey: SecretKey): String {
        val jsonArray = JSONArray()
        for (record in records) {
            val jsonObject = JSONObject().apply {
                put("websiteName", record.websiteName)
                put("encryptedUsername", record.encryptedUsername)
                put("encryptedEmail", record.encryptedEmail)
                put("encryptedPassword", record.encryptedPassword)
                put("encryptedNotes", record.encryptedNotes)
                put("category", record.category)
                put("isFavorite", record.isFavorite)
                put("createdAt", record.createdAt)
                put("lastModifiedAt", record.lastModifiedAt)
            }
            jsonArray.put(jsonObject)
        }
        val plainTextJson = jsonArray.toString()
        return SecurityManager.encrypt(plainTextJson, secretKey)
    }

    /**
     * Decrypts and parses an encrypted Base64 backup string.
     * Returns the list of parsed PasswordRecord entities, or null if decryption/parsing fails.
     */
    fun decryptBackup(encryptedBackup: String, secretKey: SecretKey): List<PasswordRecord>? {
        return try {
            val decryptedJson = SecurityManager.decrypt(encryptedBackup, secretKey)
            val jsonArray = JSONArray(decryptedJson)
            val records = mutableListOf<PasswordRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val record = PasswordRecord(
                    websiteName = obj.optString("websiteName", ""),
                    encryptedUsername = obj.optString("encryptedUsername", ""),
                    encryptedEmail = obj.optString("encryptedEmail", ""),
                    encryptedPassword = obj.optString("encryptedPassword", ""),
                    encryptedNotes = obj.optString("encryptedNotes", ""),
                    category = obj.optString("category", "سایر"),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    lastModifiedAt = obj.optLong("lastModifiedAt", System.currentTimeMillis())
                )
                records.add(record)
            }
            records
        } catch (e: Exception) {
            null
        }
    }
}
