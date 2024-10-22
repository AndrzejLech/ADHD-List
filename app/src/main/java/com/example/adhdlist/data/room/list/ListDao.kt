package com.example.adhdlist.data.room.list

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.adhdlist.data.model.TaskList
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Insert
    suspend fun insert(task: TaskList)

    @Update
    suspend fun update(task: TaskList)

    @Delete
    suspend fun delete(task: TaskList)

    @Query("SELECT * FROM task_list_table")
    fun getAllLists(): Flow<MutableList<TaskList>>

    @Query("SELECT * FROM task_list_table WHERE list_id LIKE :list_id")
    fun getList(list_id: Int): Flow<TaskList>
}