package com.example.adhdlist.data.room.list

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.room.withTransaction
import com.example.adhdlist.data.model.ListWithProgress
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.remote.SharedListsRemote
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteTask
import com.example.adhdlist.data.room.TaskDatabase
import com.example.adhdlist.data.sync.SharedListsSync
import com.example.adhdlist.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ListRepository(
    context: Context,
    private val remote: SharedListsRemote,
    private val sync: SharedListsSync
) {
    private val taskDatabase = TaskDatabase.getInstance(context)
    private val listDao = taskDatabase.listDao()
    private val taskDao = taskDatabase.taskDao()
    private var taskList: Flow<MutableList<ListWithProgress>> = emptyFlow()
    private var listData: Flow<TaskList?> = emptyFlow()

    val canShare: Boolean get() = remote.isAvailable

    @WorkerThread
    fun getLists(): Result<Flow<MutableList<ListWithProgress>>, Exception> {
        return try {
            taskList = listDao.getAllLists()
            Result.Success(taskList)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    @WorkerThread
    fun getList(listId:Int): Result<Flow<TaskList?>, Exception> {
        return try {
            listData = listDao.getList(listId)
            Result.Success(listData)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    fun cleanDatabaseForTests(): Result<Boolean, Exception>{
        return try {
            listDao.clearLists()
            Result.Success(true)
        } catch (e: Exception){
            Result.Error(e)
        }
    }

    suspend fun insertList(taskList: TaskList): Result<Boolean, Exception> {
        return try {
            taskList.position = listDao.nextPosition()
            listDao.insert(taskList)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** For a shared list this leaves it: the others keep it, this device drops its copy. */
    suspend fun deleteList(taskList: TaskList): Result<Boolean, Exception> {
        return try {
            taskList.remoteId?.let {
                sync.markLeft(it)
                remote.leave(remote.signIn(), it)
            }
            listDao.delete(taskList)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateList(taskList: TaskList): Result<Boolean, Exception> {
        return try {
            taskList.remoteId?.let { remote.rename(it, taskList.name) }
            listDao.update(taskList)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** [ordered] is the full list in its new order. */
    suspend fun reorderLists(ordered: List<TaskList>): Result<Boolean, Exception> {
        return try {
            listDao.update(ordered.onEachIndexed { index, list -> list.position = index })
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Uploads the list (once) and returns its join code. On failure the list stays local, unchanged. */
    suspend fun shareList(listId: Int): Result<String, Exception> {
        return try {
            val list = requireNotNull(listDao.getListOnce(listId)) { "List $listId not found" }
            list.remoteId?.let { return Result.Success(it) }
            val uid = remote.signIn()
            val code = remote.newListId()
            val tasks = taskDao.getTasksOnce(listId).onEach { it.remoteId = remote.newTaskId(code) }
            // Room first: the sync must already know these ids when Firestore's snapshot arrives,
            // or it would insert the list and its tasks a second time.
            taskDatabase.withTransaction {
                listDao.update(list.apply { remoteId = code })
                taskDao.update(tasks)
            }
            try {
                remote.createList(uid, code, list.name, tasks.map { RemoteTask(it.remoteId!!, it.message, it.state, it.position) })
            } catch (e: Exception) {
                taskDatabase.withTransaction {
                    listDao.update(list.apply { remoteId = null })
                    taskDao.update(tasks.onEach { it.remoteId = null })
                }
                throw e
            }
            Result.Success(code)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** The list appears through the sync once Firestore confirms membership. */
    suspend fun joinList(code: String): Result<Boolean, Exception> {
        return try {
            sync.markJoined(code)
            remote.join(remote.signIn(), code)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
