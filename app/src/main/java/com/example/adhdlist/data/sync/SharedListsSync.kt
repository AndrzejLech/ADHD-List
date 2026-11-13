package com.example.adhdlist.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.remote.SharedListsRemote
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteList
import com.example.adhdlist.data.remote.SharedListsRemote.RemoteTask
import com.example.adhdlist.data.room.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Mirrors the user's shared lists from Firestore into Room. The UI only ever reads Room, so
 * shared and local lists look the same to it; writes to shared lists go to Firestore and come
 * back through here.
 */
class SharedListsSync(context: Context, private val remote: SharedListsRemote) {
    private val database = TaskDatabase.getInstance(context)
    private val listDao = database.listDao()
    private val taskDao = database.taskDao()
    private val seen = HashSet<String>()
    private val taskJobs = HashMap<String, Job>()

    // Lists this device just left; a snapshot taken before the leave must not bring them back.
    private val left: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun markLeft(remoteId: String) {
        left += remoteId
    }

    /** Joining again after leaving: let the list back in. */
    fun markJoined(remoteId: String) {
        left -= remoteId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        if (!remote.isAvailable) return
        scope.launch {
            remote.userId()
                .flatMapLatest { uid -> if (uid == null) emptyFlow() else remote.myLists(uid).retryAfterError("myLists") }
                .collect { lists -> mirrorLists(scope, lists.filter { it.id !in left }) }
        }
    }

    private suspend fun mirrorLists(scope: CoroutineScope, remotes: List<RemoteList>) {
        database.withTransaction {
            val plan = planListMirror(listDao.getSharedLists(), remotes, seen)
            plan.insert.forEach {
                listDao.insert(TaskList(it.name).apply { remoteId = it.id; position = listDao.nextPosition() })
            }
            if (plan.rename.isNotEmpty()) listDao.update(plan.rename)
            plan.delete.forEach { listDao.delete(it) }
        }
        // Only ids the server has: a pending share the server then rejects disappears from the cache,
        // and counting it as seen would make that look like "deleted elsewhere" and delete the list.
        seen += remotes.filter { it.confirmed }.map { it.id }

        val ids = remotes.mapTo(HashSet()) { it.id }
        taskJobs.keys.filter { it !in ids }.forEach { taskJobs.remove(it)?.cancel() }
        // Only once the server has the list: before that, its rules reject reading the tasks.
        remotes.filter { it.confirmed && it.id !in taskJobs }.forEach { list ->
            taskJobs[list.id] = scope.launch {
                remote.tasks(list.id).retryAfterError("tasks").collect { mirrorTasks(list.id, it) }
            }
        }
    }

    private fun <T> Flow<T>.retryAfterError(name: String) = retryWhen { cause, _ ->
        Log.w(TAG, "$name listener failed, retrying", cause)
        delay(RETRY_MS)
        true
    }

    private suspend fun mirrorTasks(remoteListId: String, remotes: List<RemoteTask>) {
        database.withTransaction {
            val list = listDao.getByRemoteId(remoteListId) ?: return@withTransaction
            val plan = planTaskMirror(list.id, taskDao.getTasksOnce(list.id), remotes)
            plan.insert.forEach { taskDao.insert(it) }
            if (plan.update.isNotEmpty()) taskDao.update(plan.update)
            if (plan.delete.isNotEmpty()) taskDao.delete(plan.delete)
        }
    }

    private companion object {
        const val TAG = "SharedListsSync"
        const val RETRY_MS = 5_000L
    }
}
