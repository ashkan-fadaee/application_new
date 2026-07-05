package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PasswordRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun passwordDao(): PasswordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "keyban_database"
                )
                    .fallbackToDestructiveMigration() // Simple for offline apps, prevents crashes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
