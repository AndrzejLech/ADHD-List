package com.example.adhdlist.data.navigation

import android.net.Uri
import android.util.Log
import androidx.navigation.NavController
import com.example.adhdlist.data.model.TaskList
import com.google.gson.Gson

class NavigationRepository {
    private lateinit var navController: NavController

    fun setNavController(controller: NavController) {
        navController = controller
    }

    fun getNavController() = navController

    fun navigateTo(destination: String) {
        navController.navigate(destination)
    }

    fun navigateToList(list: TaskList){
        // A second tap lands while the first navigation is already on the back stack.
        if (navController.currentDestination?.route != Destinations.ListsScreen.value) return
        // Encode the JSON, not list.name: the object is the live item shown on the lists screen.
        navController.navigate("${Destinations.TasksScreen.value}/${Uri.encode(Gson().toJson(list))}")
    }

    fun popNavigation(){
        // Popping the start destination leaves an empty NavHost (blank screen) on a double back press.
        if (navController.previousBackStackEntry == null) return
        Log.d("NavigationRepository", "Navigating back")
        navController.popBackStack()
    }
}