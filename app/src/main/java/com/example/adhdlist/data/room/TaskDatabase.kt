package com.example.adhdlist.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.list.ListDao
import com.example.adhdlist.data.room.task.TaskDao

@Database(
    entities = [TaskList::class, Task::class],
    version = 3,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun listDao(): ListDao

    companion object {
        @Volatile
        var instance: TaskDatabase? = null

        fun getInstance(context: Context): TaskDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
            }

            return requireNotNull(instance)
        }
    }
}