package com.example.adhdlist.data.room.task

import android.content.Context
import androidx.annotation.WorkerThread
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.room.TaskDatabase
import com.example.adhdlist.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class TaskRepository(context: Context) {
    private val taskDatabase = TaskDatabase.getInstance(context)
    private val taskDao = taskDatabase.taskDao()
    private var taskList: Flow<MutableList<Task>> = emptyFlow()

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
            taskDao.insert(task)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteTask(task: Task): Result<Boolean, Exception> {
        return try {
            taskDao.delete(task)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateTask(task: Task): Result<Boolean, Exception> {
        return try {
            taskDao.update(task)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    @WorkerThread
    fun resetAllTasks(listId: Int): Result<Boolean, Exception>{
        return try {
            taskDao.resetAllTasks(listId)
            Result.Success(true)
        } catch (e: Exception){
            Result.Error(e)
        }
    }

    suspend fun clearAllTasksFromList(listId: Int): Result<Boolean, Exception> {
        return try {
            taskDao.clearAllTaskFromList(listId)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}