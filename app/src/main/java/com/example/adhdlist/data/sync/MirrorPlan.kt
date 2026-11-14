package com.example.adhdlist.data.sync

import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteList
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteTask

// Pure diffing between a Firestore snapshot and the Room rows that mirror it.

data class ListMirrorPlan(val insert: List<RemoteList>, val rename: List<TaskList>, val delete: List<TaskList>)

/**
 * [seen] holds remote ids that were in an earlier snapshot. A shared list missing from the snapshot
 * is deleted only if it was seen before: a list shared a moment ago may not be in a snapshot yet.
 */
fun planListMirror(localShared: List<TaskList>, remotes: List<RemoteList>, seen: Set<String>): ListMirrorPlan {
    val localByRemoteId = localShared.associateBy { it.remoteId }
    val remoteIds = remotes.mapTo(HashSet()) { it.id }
    return ListMirrorPlan(
        insert = remotes.filter { it.id !in localByRemoteId },
        rename = remotes.mapNotNull { remote ->
            val local = localByRemoteId[remote.id] ?: return@mapNotNull null
            if (local.name == remote.name) null else local.copy(name = remote.name)
        },
        delete = localShared.filter { it.remoteId !in remoteIds && it.remoteId in seen }
    )
}

data class TaskMirrorPlan(val insert: List<Task>, val update: List<Task>, val delete: List<Task>)

fun planTaskMirror(listId: Int, locals: List<Task>, remotes: List<RemoteTask>): TaskMirrorPlan {
    val localByRemoteId = locals.filter { it.remoteId != null }.associateBy { it.remoteId }
    val remoteIds = remotes.mapTo(HashSet()) { it.id }
    val insert = mutableListOf<Task>()
    val update = mutableListOf<Task>()
    for (remote in remotes) {
        val local = localByRemoteId[remote.id]
        val row = Task(listId, remote.message, remote.state, id = local?.id ?: 0, position = remote.position, remoteId = remote.id)
        when {
            local == null -> insert += row
            local.message != row.message || local.state != row.state || local.position != row.position -> update += row
        }
    }
    return TaskMirrorPlan(insert, update, locals.filter { it.remoteId != null && it.remoteId !in remoteIds })
}
