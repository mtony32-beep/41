package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.*
import com.example.data.entities.*

@Database(
    entities = [
        ChatMessage::class,
        ProjectEntity::class,
        LogEntity::class,
        BuildQueueEntity::class,
        ReviewEntity::class,
        BugScanEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun projectDao(): ProjectDao
    abstract fun logDao(): LogDao
    abstract fun buildQueueDao(): BuildQueueDao
    abstract fun reviewDao(): ReviewDao
    abstract fun bugScanDao(): BugScanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rerev7_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
