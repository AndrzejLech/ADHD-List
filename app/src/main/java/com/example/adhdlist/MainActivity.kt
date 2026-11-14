package com.example.adhdlist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.Destinations
import com.example.adhdlist.data.navigation.NavigationRepository
import com.example.adhdlist.data.remote.SharedListsRemote
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

    // Join code from an adhdlist://join/<code> link or from text shared into the app.
    private var joinCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark status-bar icons always: they sit on the yellow top bar in light and dark mode.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) joinCode = intent.joinCode()
        setContent {
            ADHDListTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    navigationRepository.setNavController(rememberNavController())

                    // Short, soft fades: movement that is easy to follow and never startling.
                    NavHost(
                        navController = navigationRepository.getNavController() as NavHostController,
                        startDestination = Destinations.ListsScreen.value,
                        enterTransition = { fadeIn(tween(200)) },
                        exitTransition = { fadeOut(tween(200)) }
                    ) {
                        composable(route = Destinations.ListsScreen.value) {
                            ListsScreen(joinCode = joinCode, onJoinCodeHandled = { joinCode = null })
                        }
                        composable(route = Destinations.TasksScreen.value + "/{list}") { navBackStackEntry ->
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.joinCode()?.let { joinCode = it }
    }

    private fun Intent?.joinCode(): String? {
        val text = when (this?.action) {
            Intent.ACTION_VIEW -> data?.toString()
            Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        return SharedListsRemote.findJoinCode(text)
    }
}
