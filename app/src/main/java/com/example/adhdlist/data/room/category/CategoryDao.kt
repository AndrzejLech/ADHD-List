package com.example.adhdlist.data.room.category

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.adhdlist.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Update
    suspend fun update(categories: List<Category>)

    @Delete
    suspend fun delete(category: Category)

    @Delete
    suspend fun delete(categories: List<Category>)

    @Query("SELECT * FROM category_table WHERE list_id = :list_id ORDER BY position, category_id")
    fun getForList(list_id: Int): Flow<List<Category>>

    @Query("SELECT * FROM category_table WHERE list_id = :list_id ORDER BY position, category_id")
    suspend fun getForListOnce(list_id: Int): List<Category>

    @Query("SELECT * FROM category_table WHERE category_id = :category_id")
    suspend fun getOnce(category_id: Int): Category?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM category_table WHERE list_id = :list_id")
    suspend fun nextPosition(list_id: Int): Int

    @Query("UPDATE category_table SET expanded = :expanded WHERE category_id = :category_id")
    suspend fun setExpanded(category_id: Int, expanded: Boolean)

    // Items of a deleted category move to the top of the list instead of disappearing.
    @Query("UPDATE task_table SET category_id = NULL WHERE category_id = :category_id")
    suspend fun releaseItems(category_id: Int)
}
