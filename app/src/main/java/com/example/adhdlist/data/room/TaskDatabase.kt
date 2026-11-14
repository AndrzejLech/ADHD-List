package com.example.adhdlist.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.adhdlist.data.model.Category
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.category.CategoryDao
import com.example.adhdlist.data.room.list.ListDao
import com.example.adhdlist.data.room.task.TaskDao

@Database(
    entities = [TaskList::class, Task::class, Category::class],
    version = 5,
    exportSchema = true
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun listDao(): ListDao
    abstract fun categoryDao(): CategoryDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `category_table` (`list_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `category_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `position` INTEGER NOT NULL DEFAULT 0, `remote_id` TEXT, `expanded` INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(`list_id`) REFERENCES `task_list_table`(`list_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_table_list_id` ON `category_table` (`list_id`)")
                db.execSQL("ALTER TABLE task_table ADD COLUMN category_id INTEGER")
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .build()
            }

            return requireNotNull(instance)
        }
    }
}
