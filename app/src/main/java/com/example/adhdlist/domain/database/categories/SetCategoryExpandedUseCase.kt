package com.example.adhdlist.domain.database.categories

import com.example.adhdlist.data.model.Category
import com.example.adhdlist.data.room.category.CategoryRepository
import javax.inject.Inject

class SetCategoryExpandedUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend fun execute(category: Category, expanded: Boolean) = categoryRepository.setExpanded(category, expanded)
}
