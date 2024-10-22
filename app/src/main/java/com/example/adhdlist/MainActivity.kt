package com.example.adhdlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.Destinations
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.presentation.view.lists.layout.ListsScreen
import com.example.adhdlist.presentation.view.tasks.layout.TasksScreen
import com.example.adhdlist.ui.theme.ADHDListTheme
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationRepository: NavigationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ADHDListTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    navigationRepository.setNavController(rememberNavController())

                    NavHost(
                        navController = navigationRepository.getNavController() as NavHostController,
                        startDestination = Destinations.ListsScreen.value
                    ) {
                        composable(
                            route = Destinations.ListsScreen.value,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(500)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(500)
                                )
                            }
                        ) {
                            ListsScreen()
                        }
                        composable(
                            route = Destinations.TasksScreen.value + "/{list}",
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(500)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(500)
                                )
                            }
                        ) { navBackStackEntry ->
                            val gson: Gson = GsonBuilder().create()
                            val listJson = navBackStackEntry.arguments?.getString("list")
                            val list = gson.fromJson(listJson, TaskList::class.java)

                            TasksScreen(list)
                        }
                    }
                }
            }
        }
    }
}