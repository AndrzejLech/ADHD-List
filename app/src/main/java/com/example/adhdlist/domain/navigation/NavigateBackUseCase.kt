package com.example.adhdlist.domain.navigation

import com.example.adhdlist.data.navigation.NavigationRepository
import javax.inject.Inject

class NavigateBackUseCase @Inject constructor(
    private val navigationRepository: NavigationRepository
) {
    fun execute() = navigationRepository.popNavigation()
}