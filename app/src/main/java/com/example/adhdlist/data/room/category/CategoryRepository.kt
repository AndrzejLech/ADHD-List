package com.example.adhdlist.data.room.category

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.room.withTransaction
import com.example.adhdlist.data.model.Category
import com.example.adhdlist.data.remote.SharedListsRemote
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteCategory
import com.example.adhdlist.data.room.TaskDatabase
import com.example.adhdlist.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Categories of a shared list are written to Firestore only and come back through SharedListsSync,
 * like items (see TaskRepository). Open/closed is always local.
 */
class CategoryRepository(context: Context, private val remote: SharedListsRemote) {
    private val taskDatabase = TaskDatabase.getInstance(context)
    private val categoryDao = taskDatabase.categoryDao()
    private val listDao = taskDatabase.listDao()
    private val taskDao = taskDatabase.taskDao()

    private suspend fun remoteListId(listId: Int) = listDao.getListOnce(listId)?.remoteId

    @WorkerThread
    fun getCategories(listId: Int): Result<Flow<List<Category>>, Exception> {
        return try {
            Result.Success(categoryDao.getForList(listId))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun createCategory(listId: Int, name: String): Result<Boolean, Exception> {
        return try {
            val position = categoryDao.nextPosition(listId)
            val listRemoteId = remoteListId(listId)
            if (listRemoteId == null) categoryDao.insert(Category(listId, name, position = position))
            else remote.setCategory(listRemoteId, RemoteCategory(remote.newCategoryId(listRemoteId), name, position))
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun renameCategory(category: Category): Result<Boolean, Exception> {
        return try {
            val listRemoteId = remoteListId(category.listId)
            val categoryRemoteId = category.remoteId
            if (listRemoteId == null || categoryRemoteId == null) categoryDao.update(category)
            else remote.setCategory(listRemoteId, RemoteCategory(categoryRemoteId, category.name, category.position))
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** The category goes; its items stay and move to the top of the list. */
    suspend fun deleteCategory(category: Category): Result<Boolean, Exception> {
        return try {
            val listRemoteId = remoteListId(category.listId)
            val categoryRemoteId = category.remoteId
            if (listRemoteId == null || categoryRemoteId == null) taskDatabase.withTransaction {
                categoryDao.releaseItems(category.id)
                categoryDao.delete(category)
            } else remote.deleteCategory(
                listRemoteId,
                categoryRemoteId,
                taskDao.getTasksOnce(category.listId).filter { it.categoryId == category.id }.mapNotNull { it.remoteId }
            )
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun setExpanded(category: Category, expanded: Boolean): Result<Boolean, Exception> {
        return try {
            categoryDao.setExpanded(category.id, expanded)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
