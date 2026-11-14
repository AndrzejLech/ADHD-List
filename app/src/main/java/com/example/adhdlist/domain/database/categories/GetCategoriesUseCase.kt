package com.example.adhdlist.domain.database.categories

import com.example.adhdlist.data.room.category.CategoryRepository
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    fun execute(listId: Int) = categoryRepository.getCategories(listId)
}
