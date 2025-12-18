package com.fabiocunha.todoapplitroz.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.fabiocunha.todoapplitroz.data.local.dao.TaskDao
import com.fabiocunha.todoapplitroz.data.local.entity.TaskEntity

/**
 * Room database for the application
 */
@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}