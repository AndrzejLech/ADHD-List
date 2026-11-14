package com.example.adhdlist.tests

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.adhdlist.data.room.TaskDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Upgrading from the released schema (v3) must keep every list and task, in today's order.
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun v3DataSurvivesUpgradeToTheLatestVersionInItsOriginalOrder() = runBlocking {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            // Exactly what Room 2.6.1 created for version 3 (from the generated TaskDatabase_Impl).
            db.execSQL("CREATE TABLE IF NOT EXISTS `task_list_table` (`name` TEXT NOT NULL, `list_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `task_table` (`list_id` INTEGER NOT NULL, `message` TEXT NOT NULL, `state` INTEGER NOT NULL, `task_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`list_id`) REFERENCES `task_list_table`(`list_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '898c2d1b1a75bc317cc4a12c077acc23')")
            db.execSQL("INSERT INTO task_list_table (name) VALUES ('Groceries'), ('Packing')")
            db.execSQL("INSERT INTO task_table (list_id, message, state) VALUES (1, 'milk', 1), (1, 'eggs', 0), (2, 'socks', 0)")
            db.version = 3
        }

        // No destructive fallback here: if the migration were wrong, Room would throw instead of wiping.
        val room = Room.databaseBuilder(context, TaskDatabase::class.java, name)
            .addMigrations(TaskDatabase.MIGRATION_3_4, TaskDatabase.MIGRATION_4_5)
            .build()
        try {
            val lists = room.listDao().getAllLists().first()
            assertEquals(listOf("Groceries", "Packing"), lists.map { it.list.name })
            assertEquals(listOf(2 to 1, 1 to 0), lists.map { it.total to it.done })
            assertNull(lists.first().list.remoteId)

            val tasks = room.taskDao().getTasksOnce(1)
            assertEquals(listOf("milk", "eggs"), tasks.map { it.message })
            assertEquals(listOf(true, false), tasks.map { it.state })
            assertEquals(tasks.map { it.id }, tasks.map { it.position })
            // v5: categories exist but are empty; every item starts at the top (no category).
            assertEquals(emptyList<Any>(), room.categoryDao().getForListOnce(1))
            assertEquals(listOf(null, null), tasks.map { it.categoryId })
        } finally {
            room.close()
        }
    }
}
