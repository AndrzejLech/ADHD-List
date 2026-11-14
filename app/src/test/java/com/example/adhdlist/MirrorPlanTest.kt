package com.example.adhdlist

import com.example.adhdlist.data.model.Category
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteCategory
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteList
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteTask
import com.example.adhdlist.data.sync.planCategoryMirror
import com.example.adhdlist.data.sync.planListMirror
import com.example.adhdlist.data.sync.planTaskMirror
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The Firestore → Room diff behind shared lists (docs/STATE_MACHINES.md §8).
class MirrorPlanTest {
    private fun localList(id: Int, name: String, remoteId: String, position: Int = id) =
        TaskList(name).apply { this.id = id; this.remoteId = remoteId; this.position = position }

    private fun localTask(id: Int, message: String, remoteId: String?, state: Boolean = false, position: Int = 0) =
        Task(1, message, state).apply { this.id = id; this.remoteId = remoteId; this.position = position }

    @Test
    fun `new remote list is inserted, renamed one keeps its local id and position`() {
        val plan = planListMirror(
            localShared = listOf(localList(5, "Old name", "A", position = 9)),
            remotes = listOf(RemoteList("A", "New name"), RemoteList("B", "Joined")),
            seen = emptySet()
        )

        assertEquals(listOf("B"), plan.insert.map { it.id })
        val renamed = plan.rename.single()
        assertEquals(listOf(5, 9, "A", "New name"), listOf(renamed.id, renamed.position, renamed.remoteId, renamed.name))
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `missing list is deleted only after it was seen`() {
        val local = listOf(localList(1, "Just shared", "NEW"), localList(2, "Left by me", "GONE"))

        val plan = planListMirror(local, remotes = emptyList(), seen = setOf("GONE"))

        assertEquals(listOf(2), plan.delete.map { it.id })
    }

    @Test
    fun `categories mirror by remote id and keep this phone's open or closed state`() {
        val locals = listOf(
            Category(1, "Warzywa", id = 5, remoteId = "w", expanded = false),
            Category(1, "Stare", id = 6, remoteId = "old")
        )
        val plan = planCategoryMirror(
            listId = 1,
            locals = locals,
            remotes = listOf(RemoteCategory("w", "Warzywa i owoce", 0), RemoteCategory("n", "Nabiał", 1))
        )

        assertEquals(listOf("n"), plan.insert.map { it.remoteId })
        assertEquals(listOf(Category(1, "Warzywa i owoce", id = 5, remoteId = "w", expanded = false)), plan.update)
        assertEquals(listOf(6), plan.delete.map { it.id })
    }

    @Test
    fun `an item's category comes from the remote id map, unknown ones fall back to none`() {
        val plan = planTaskMirror(
            listId = 1,
            locals = listOf(localTask(1, "cukinia", "t1")),
            remotes = listOf(
                RemoteTask("t1", "cukinia", false, 0, category = "w"),
                RemoteTask("t2", "jabłko", false, 1, category = "missing")
            ),
            categoryIds = mapOf("w" to 5)
        )

        assertEquals(5, plan.update.single().categoryId)
        assertEquals(null, plan.insert.single().categoryId)
    }

    @Test
    fun `tasks are inserted, updated and deleted by remote id`() {
        val locals = listOf(
            localTask(1, "same", "r1"),
            localTask(2, "checked elsewhere", "r2", state = false),
            localTask(3, "deleted elsewhere", "r3"),
            localTask(4, "not uploaded", null)
        )
        val remotes = listOf(
            RemoteTask("r1", "same", false, 0),
            RemoteTask("r2", "checked elsewhere", true, 0),
            RemoteTask("r9", "added elsewhere", false, 5)
        )

        val plan = planTaskMirror(listId = 1, locals = locals, remotes = remotes)

        assertEquals(listOf("r9"), plan.insert.map { it.remoteId })
        assertEquals(0, plan.insert.single().id)
        assertEquals(5, plan.insert.single().position)
        assertEquals(listOf(2), plan.update.map { it.id })
        assertTrue(plan.update.single().state)
        assertEquals(listOf(3), plan.delete.map { it.id })
    }
}
