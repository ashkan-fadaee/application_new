package com.example.data

import kotlinx.coroutines.flow.Flow

class PasswordRepository(private val passwordDao: PasswordDao) {

    val allRecords: Flow<List<PasswordRecord>> = passwordDao.getAllRecords()

    suspend fun getRecordById(id: Int): PasswordRecord? {
        return passwordDao.getRecordById(id)
    }

    suspend fun insertRecord(record: PasswordRecord): Long {
        return passwordDao.insertRecord(record)
    }

    suspend fun updateRecord(record: PasswordRecord) {
        passwordDao.updateRecord(record)
    }

    suspend fun deleteRecord(record: PasswordRecord) {
        passwordDao.deleteRecord(record)
    }

    suspend fun deleteRecordById(id: Int) {
        passwordDao.deleteRecordById(id)
    }

    suspend fun restoreBackup(records: List<PasswordRecord>) {
        passwordDao.deleteAll()
        passwordDao.insertAll(records)
    }

    suspend fun clearAll() {
        passwordDao.deleteAll()
    }
}
