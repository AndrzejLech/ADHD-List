package com.example.adhdlist

import android.net.Uri
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.navigation.Destinations
import com.example.adhdlist.data.navigation.NavigationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.URLEncoder

// Issue 1: navigation transitions must be idempotent (see docs/STATE_MACHINES.md, N1-N3).
class NavigationRepositoryTest {
    private val navController: NavController = mockk(relaxed = true)
    private val repository = NavigationRepository().apply { setNavController(navController) }

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.encode(any()) } answers { URLEncoder.encode(firstArg<String>(), "UTF-8") }
    }

    @After
    fun tearDown() = unmockkStatic(Uri::class)

    @Test
    fun `double back press does not pop the start destination`() {
        // Tasks -> Lists after the first pop; nothing below Lists.
        every { navController.previousBackStackEntry } returnsMany listOf(mockk<NavBackStackEntry>(), null)

        repository.popNavigation()
        repository.popNavigation()

        verify(exactly = 1) { navController.popBackStack() }
    }

    @Test
    fun `double tap on a list navigates once`() {
        every { navController.currentDestination?.route } returnsMany
            listOf(Destinations.ListsScreen.value, "${Destinations.TasksScreen.value}/{list}")

        val list = TaskList("a b").apply { id = 3 }
        repository.navigateToList(list)
        repository.navigateToList(list)

        verify(exactly = 1) { navController.navigate(any<String>()) }
    }

    @Test
    fun `navigating does not mutate the list shown on the lists screen`() {
        every { navController.currentDestination?.route } returns Destinations.ListsScreen.value

        val list = TaskList("a b").apply { id = 3 }
        repository.navigateToList(list)

        assertEquals("a b", list.name)
    }
}
