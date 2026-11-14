package com.example.adhdlist.domain.database.categories

import com.example.adhdlist.data.room.category.CategoryRepository
import javax.inject.Inject

class CreateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend fun execute(listId: Int, name: String) = categoryRepository.createCategory(listId, name)
}
