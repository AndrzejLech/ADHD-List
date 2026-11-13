package com.example.adhdlist.data.room.list

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.adhdlist.data.model.ListWithProgress
import com.example.adhdlist.data.model.TaskList
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Insert
    suspend fun insert(task: TaskList): Long

    @Update
    suspend fun update(task: TaskList)

    @Update
    suspend fun update(lists: List<TaskList>)

    @Delete
    suspend fun delete(task: TaskList)

    @Query(
        """SELECT l.*,
            (SELECT COUNT(*) FROM task_table t WHERE t.list_id = l.list_id) AS total,
            (SELECT COUNT(*) FROM task_table t WHERE t.list_id = l.list_id AND t.state = 1) AS done
        FROM task_list_table l ORDER BY l.position, l.list_id"""
    )
    fun getAllLists(): Flow<MutableList<ListWithProgress>>

    @Query("SELECT * FROM task_list_table WHERE list_id = :list_id")
    fun getList(list_id: Int): Flow<TaskList?>

    @Query("SELECT * FROM task_list_table WHERE list_id = :list_id")
    suspend fun getListOnce(list_id: Int): TaskList?

    @Query("SELECT * FROM task_list_table WHERE remote_id = :remoteId")
    suspend fun getByRemoteId(remoteId: String): TaskList?

    @Query("SELECT * FROM task_list_table WHERE remote_id IS NOT NULL")
    suspend fun getSharedLists(): List<TaskList>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM task_list_table")
    suspend fun nextPosition(): Int

    @Query("DELETE FROM task_list_table")
    fun clearLists()
}
