package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

    @Query("SELECT * FROM password_records ORDER BY isFavorite DESC, websiteName ASC")
    fun getAllRecords(): Flow<List<PasswordRecord>>

    @Query("SELECT * FROM password_records WHERE id = :id")
    suspend fun getRecordById(id: Int): PasswordRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PasswordRecord): Long

    @Update
    suspend fun updateRecord(record: PasswordRecord)

    @Delete
    suspend fun deleteRecord(record: PasswordRecord)

    @Query("DELETE FROM password_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PasswordRecord>)

    @Query("DELETE FROM password_records")
    suspend fun deleteAll()
}
