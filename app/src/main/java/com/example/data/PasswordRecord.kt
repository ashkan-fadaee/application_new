package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "password_records")
data class PasswordRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val websiteName: String, // Plaintext to enable search and sort, or we can index it. Website name is safe to search.
    val encryptedUsername: String,
    val encryptedEmail: String,
    val encryptedPassword: String,
    val encryptedNotes: String,
    val category: String, // Plaintext to allow filter and sort easily
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)
