package com.example.adhdlist.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.list.ListDao
import com.example.adhdlist.data.room.task.TaskDao

@Database(
    entities = [TaskList::class, Task::class],
    version = 4,
    exportSchema = true
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun listDao(): ListDao

    companion object {
        @Volatile
        var instance: TaskDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_list_table ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE task_list_table ADD COLUMN remote_id TEXT")
                db.execSQL("ALTER TABLE task_table ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE task_table ADD COLUMN remote_id TEXT")
                // Start the manual order from today's order, which was insertion order.
                db.execSQL("UPDATE task_list_table SET position = list_id")
                db.execSQL("UPDATE task_table SET position = task_id")
            }
        }

        fun getInstance(context: Context): TaskDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                    // The app is live: every schema change needs a Migration here (and a test in
                    // MigrationTest). No destructive fallback, so a missing one fails loudly in
                    // testing instead of silently wiping users' lists. v3 is the oldest shipped schema.
                    .addMigrations(MIGRATION_3_4)
                    .build()
            }

            return requireNotNull(instance)
        }
    }
}
