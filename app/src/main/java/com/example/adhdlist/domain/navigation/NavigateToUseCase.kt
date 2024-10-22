package com.example.adhdlist.domain.navigation

import com.example.adhdlist.data.navigation.Destinations
import com.example.adhdlist.data.navigation.NavigationRepository
import javax.inject.Inject

class NavigateToUseCase @Inject constructor(
    private val navigationRepository: NavigationRepository
) {
    fun execute(destinations: Destinations) = navigationRepository.navigateTo(destinations.value)
}