package com.example.adhdlist.data.room.task

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.adhdlist.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE task_table SET state = 0 WHERE list_id LIKE :list_id")
    fun resetAllTasks(list_id: Int)

    @Query("SELECT * FROM task_table WHERE list_id LIKE :list_id")
    fun getAllTasksFromList(list_id: Int): Flow<MutableList<Task>>

    @Query("DELETE FROM task_table WHERE list_id LIKE :list_id")
    suspend fun clearAllTaskFromList(list_id: Int)
}