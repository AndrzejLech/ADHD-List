package com.example.adhdlist.data.room.list

import android.content.Context
import androidx.annotation.WorkerThread
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.TaskDatabase
import com.example.adhdlist.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ListRepository(context: Context) {
    private val taskDatabase = TaskDatabase.getInstance(context)
    private val listDao = taskDatabase.listDao()
    private var taskList: Flow<MutableList<TaskList>> = emptyFlow()
    private var listData: Flow<TaskList> = emptyFlow()

    @WorkerThread
    fun getLists(): Result<Flow<MutableList<TaskList>>, Exception> {
        return try {
            taskList = listDao.getAllLists()
            Result.Success(taskList)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    @WorkerThread
    fun getList(listId:Int): Result<Flow<TaskList>, Exception> {
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
            listDao.insert(taskList)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteList(taskList: TaskList): Result<Boolean, Exception> {
        return try {
            listDao.delete(taskList)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateList(taskList: TaskList): Result<Boolean, Exception> {
        return try {
            listDao.update(taskList)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}