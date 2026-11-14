package com.example.adhdlist.data.room.task

import android.content.Context
import androidx.annotation.WorkerThread
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.remote.SharedListsRemote
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteTask
import com.example.adhdlist.data.room.TaskDatabase
import com.example.adhdlist.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Tasks of a shared list are written to Firestore only; SharedListsSync mirrors the result into
 * Room. Writing Room as well would race the mirror and could insert a task twice.
 */
class TaskRepository(context: Context, private val remote: SharedListsRemote) {
    private val taskDatabase = TaskDatabase.getInstance(context)
    private val taskDao = taskDatabase.taskDao()
    private val listDao = taskDatabase.listDao()
    private var taskList: Flow<MutableList<Task>> = emptyFlow()

    private suspend fun remoteListId(listId: Int) = listDao.getListOnce(listId)?.remoteId

    @WorkerThread
    fun getTasks(id: Int): Result<Flow<MutableList<Task>>, Exception> {
        return try {
            taskList = taskDao.getAllTasksFromList(id)
            Result.Success(taskList)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun insertTask(task: Task): Result<Boolean, Exception> {
        return try {
            task.position = taskDao.nextPosition(task.listId)
            val listRemoteId = remoteListId(task.listId)
            if (listRemoteId == null) taskDao.insert(task)
            else remote.setTask(listRemoteId, RemoteTask(remote.newTaskId(listRemoteId), task.message, task.state, task.position))
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Undo of a delete: puts the task back with its old id and position. */
    suspend fun restoreTask(task: Task): Result<Boolean, Exception> {
        return try {
            val listRemoteId = remoteListId(task.listId)
            val taskRemoteId = task.remoteId
            if (listRemoteId == null || taskRemoteId == null) taskDao.insert(task)
            else remote.setTask(listRemoteId, RemoteTask(taskRemoteId, task.message, task.state, task.position))
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteTask(task: Task): Result<Boolean, Exception> {
        return try {
            val listRemoteId = remoteListId(task.listId)
            val taskRemoteId = task.remoteId
            if (listRemoteId == null || taskRemoteId == null) taskDao.delete(task)
            else remote.deleteTask(listRemoteId, taskRemoteId)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** New text for an item; only that field is sent, so a check made elsewhere at the same time survives. */
    suspend fun editTask(task: Task): Result<Boolean, Exception> {
        return try {
            val listRemoteId = remoteListId(task.listId)
            val taskRemoteId = task.remoteId
            if (listRemoteId == null || taskRemoteId == null) taskDao.update(task)
            else remote.setMessage(listRemoteId, taskRemoteId, task.message)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Only the checked state changes; sending just that field keeps others' edits intact. */
    suspend fun updateTask(task: Task): Result<Boolean, Exception> {
        return try {
            val listRemoteId = remoteListId(task.listId)
            val taskRemoteId = task.remoteId
            if (listRemoteId == null || taskRemoteId == null) taskDao.update(task)
            else remote.setState(listRemoteId, taskRemoteId, task.state)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** [ordered] is the whole list in its new order. Room is written too so the drop shows at once. */
    suspend fun reorderTasks(listId: Int, ordered: List<Task>): Result<Boolean, Exception> {
        return try {
            ordered.forEachIndexed { index, task -> task.position = index }
            taskDao.update(ordered)
            remoteListId(listId)?.let { listRemoteId ->
                remote.setPositions(listRemoteId, ordered.filter { it.remoteId != null }.associate { it.remoteId!! to it.position })
            }
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Ticks or unticks every item in the list. */
    suspend fun setAllTasks(listId: Int, checked: Boolean): Result<Boolean, Exception>{
        return try {
            val listRemoteId = remoteListId(listId)
            if (listRemoteId == null) taskDao.setAllStates(listId, checked)
            else remote.setAllStates(listRemoteId, taskDao.getTasksOnce(listId).mapNotNull { it.remoteId }, checked)
            Result.Success(true)
        } catch (e: Exception){
            Result.Error(e)
        }
    }

    suspend fun clearAllTasksFromList(listId: Int): Result<Boolean, Exception> {
        return try {
            val listRemoteId = remoteListId(listId)
            if (listRemoteId == null) taskDao.clearAllTaskFromList(listId)
            else remote.deleteAll(listRemoteId, taskDao.getTasksOnce(listId).mapNotNull { it.remoteId })
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
