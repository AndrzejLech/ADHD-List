package com.example.adhdlist

import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.list.ListRepository
import com.example.adhdlist.domain.database.lists.GetListsUseCase
import com.example.adhdlist.domain.util.Result
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    private val listRepositoryMockk: ListRepository = mockk()
    private val getListsUseCaseMockk: GetListsUseCase = GetListsUseCase(listRepositoryMockk)

    @Test
    fun `getListUseCase returns data on result success`() = runTest {
        // Arrange
        val fakeTaskFlow = flowOf(mutableListOf(TaskList(name = "test")))
        val fakeResult = Result.Success(fakeTaskFlow)
        coEvery { listRepositoryMockk.getLists() } returns fakeResult

        // Act
        val actResult = getListsUseCaseMockk.execute()

        // Assert
        assertEquals(actResult, fakeResult)
    }

    @Test
    fun `getListUseCase returns error on result error`() = runTest {
        // Arrange
        val fakeError = Exception("Error")
        val fakeResult = Result.Error(fakeError)
        coEvery { listRepositoryMockk.getLists() } returns fakeResult

        // Act
        val actResult = getListsUseCaseMockk.execute()

        // Assert
        assertEquals(actResult, fakeResult)
    }
}