package com.example.adhdlist.domain.navigation

import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.NavigationRepository
import javax.inject.Inject

class NavigateToListUseCase @Inject constructor(
    private val navigationRepository: NavigationRepository
) {
    fun execute(list: TaskList) = navigationRepository.navigateToList(list)
}