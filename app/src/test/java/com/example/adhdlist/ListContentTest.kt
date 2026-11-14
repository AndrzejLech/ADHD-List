package com.example.adhdlist

import com.example.adhdlist.data.remote.SharedListsRemote.RemoteCategory
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteTask
import com.example.adhdlist.data.sync.listContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// docs/STATE_MACHINES.md §3c: a shared list's items must keep syncing even when its categories can't be read.
class ListContentTest {
    private val milk = RemoteTask("t1", "milk", false, 0)
    private val vegetables = RemoteCategory("w", "Warzywa", 0)

    @Test
    fun `items arrive together with their categories when both can be read`() = runTest {
        val (categories, tasks) = listContent(flowOf(listOf(vegetables)), flowOf(listOf(milk))).first()

        assertEquals(listOf(vegetables), categories)
        assertEquals(listOf(milk), tasks)
    }

    @Test
    fun `items still arrive when categories are refused, with categories marked unknown`() = runTest {
        val refused = flow<List<RemoteCategory>> { throw IllegalStateException("PERMISSION_DENIED") }

        val (categories, tasks) = listContent(refused, flowOf(listOf(milk))).first()

        assertEquals(null, categories)
        assertEquals(listOf(milk), tasks)
    }
}
