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
        list.name = Uri.encode(list.name)
        navController.navigate(
            "${Destinations.TasksScreen.value}/{list}"
            .replace(oldValue = "{list}", newValue =  Gson().toJson(list))
        )
    }

    fun popNavigation(){
        Log.d("NavigationRepository", "Navigating back")
        navController.popBackStack()
    }
}