package com.example.adhdlist.data.remote

import android.content.Context
import android.util.Log
import com.example.adhdlist.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Firestore layout:
 *   lists/{listId}                {name, members: [uid]}   listId doubles as the join code
 *   lists/{listId}/tasks/{taskId} {message, state, position}
 *
 * Writes are not awaited: Firestore applies them to its local cache at once (so the snapshot
 * listeners, and through them Room, update even offline) and sends them when it can.
 */
class SharedListsRemote(context: Context) {
    /** [confirmed]: the server has the list, not just this device's cache (true for lists created elsewhere). */
    data class RemoteList(val id: String, val name: String, val confirmed: Boolean = true)
    data class RemoteTask(val id: String, val message: String, val state: Boolean, val position: Int)

    private val app: FirebaseApp? = initFirebase(context)
    val isAvailable: Boolean get() = app != null

    private val auth by lazy { useEmulatorsOnce(app!!); FirebaseAuth.getInstance(app) }
    private val db by lazy { useEmulatorsOnce(app!!); FirebaseFirestore.getInstance(app) }

    private fun listRef(listId: String) = db.collection(LISTS).document(listId)
    private fun tasksRef(listId: String) = listRef(listId).collection(TASKS)

    /** Emits the signed-in user id, or null before the first share/join on this install. */
    fun userId(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Anonymous account: no sign-in screen; it persists across launches. */
    suspend fun signIn(): String =
        auth.currentUser?.uid ?: withTimeout(TIMEOUT_MS) { auth.signInAnonymously().await() }.user!!.uid

    fun myLists(uid: String): Flow<List<RemoteList>> = callbackFlow {
        // INCLUDE: also fire when a just-shared list goes from "pending" to "on the server".
        val registration = db.collection(LISTS).whereArrayContains(MEMBERS, uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot!!.documents.map {
                    RemoteList(it.id, it.getString(NAME).orEmpty(), confirmed = !it.metadata.hasPendingWrites())
                })
            }
        awaitClose { registration.remove() }
    }

    fun tasks(listId: String): Flow<List<RemoteTask>> = callbackFlow {
        val registration = tasksRef(listId).addSnapshotListener { snapshot, error ->
            // Firestore stops a listener after an error; closing lets the caller retry it.
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot!!.documents.map {
                RemoteTask(
                    id = it.id,
                    message = it.getString(MESSAGE).orEmpty(),
                    state = it.getBoolean(STATE) ?: false,
                    position = it.getLong(POSITION)?.toInt() ?: 0
                )
            })
        }
        awaitClose { registration.remove() }
    }

    fun newListId(): String = db.collection(LISTS).document().id
    fun newTaskId(listId: String): String = tasksRef(listId).document().id

    /**
     * Uploads a list and its tasks (ids chosen by the caller) in one batch. Awaited, unlike other
     * writes: if the server refuses it, the caller must turn the list back into a local one.
     */
    suspend fun createList(uid: String, listId: String, name: String, tasks: List<RemoteTask>) {
        val batch = db.batch()
        batch.set(listRef(listId), mapOf(NAME to name, MEMBERS to listOf(uid)))
        tasks.forEach { batch.set(tasksRef(listId).document(it.id), it.toMap()) }
        withTimeout(TIMEOUT_MS) { batch.commit().await() }
    }

    /** Awaited: the user needs to know whether the code worked. */
    suspend fun join(uid: String, listId: String) {
        withTimeout(TIMEOUT_MS) { listRef(listId).update(MEMBERS, FieldValue.arrayUnion(uid)).await() }
    }

    fun leave(uid: String, listId: String) {
        listRef(listId).update(MEMBERS, FieldValue.arrayRemove(uid)).logFailure("leave")
    }

    fun rename(listId: String, name: String) {
        listRef(listId).update(NAME, name).logFailure("rename")
    }

    fun setTask(listId: String, task: RemoteTask) {
        tasksRef(listId).document(task.id).set(task.toMap()).logFailure("setTask")
    }

    fun setMessage(listId: String, taskId: String, message: String) {
        tasksRef(listId).document(taskId).update(MESSAGE, message).logFailure("setMessage")
    }

    fun setState(listId: String, taskId: String, state: Boolean) {
        tasksRef(listId).document(taskId).update(STATE, state).logFailure("setState")
    }

    fun deleteTask(listId: String, taskId: String) {
        tasksRef(listId).document(taskId).delete().logFailure("deleteTask")
    }

    fun setAllStates(listId: String, taskIds: List<String>, state: Boolean) = batch("setAllStates") { batch ->
        taskIds.forEach { batch.update(tasksRef(listId).document(it), STATE, state) }
    }

    fun deleteAll(listId: String, taskIds: List<String>) = batch("deleteAll") { batch ->
        taskIds.forEach { batch.delete(tasksRef(listId).document(it)) }
    }

    fun setPositions(listId: String, positions: Map<String, Int>) = batch("setPositions") { batch ->
        positions.forEach { (taskId, position) -> batch.update(tasksRef(listId).document(taskId), POSITION, position) }
    }

    private fun batch(name: String, fill: (com.google.firebase.firestore.WriteBatch) -> Unit) {
        db.batch().also(fill).commit().logFailure(name)
    }

    private fun com.google.android.gms.tasks.Task<*>.logFailure(name: String) {
        addOnFailureListener { Log.e(TAG, name, it) }
    }

    private fun RemoteTask.toMap() = mapOf(MESSAGE to message, STATE to state, POSITION to position)

    companion object {
        private const val TAG = "SharedListsRemote"
        private const val LISTS = "lists"
        private const val TASKS = "tasks"
        private const val NAME = "name"
        private const val MEMBERS = "members"
        private const val MESSAGE = "message"
        private const val STATE = "state"
        private const val POSITION = "position"
        private const val TIMEOUT_MS = 15_000L
        private val EMULATOR_HOST = BuildConfig.FIREBASE_EMULATOR_HOST

        /** Must match the limit in firebase/firestore.rules, or long tasks would vanish from shared lists. */
        const val MAX_TASK_LENGTH = 1000

        private val CODE = Regex("\\b[A-Za-z0-9]{20}\\b")
        private val LABELLED_CODE = Regex("(?:join/|[Cc]ode:\\s*)([A-Za-z0-9]{20})\\b")

        /** The code after "join/" or "code:" first, so a 20-letter list name in the invite isn't taken for it. */
        fun findJoinCode(text: String): String? =
            LABELLED_CODE.find(text)?.groupValues?.get(1) ?: CODE.find(text)?.value

        private var emulatorsSet = false

        // Process-wide, like the Firebase instances themselves (tests build a new DI graph per test).
        @Synchronized
        private fun useEmulatorsOnce(app: FirebaseApp) {
            if (emulatorsSet || EMULATOR_HOST.isEmpty()) return
            FirebaseAuth.getInstance(app).useEmulator(EMULATOR_HOST, 9099)
            FirebaseFirestore.getInstance(app).useEmulator(EMULATOR_HOST, 8080)
            emulatorsSet = true
        }

        private const val EMULATOR_APP = "local-emulator"

        private fun initFirebase(context: Context): FirebaseApp? {
            if (EMULATOR_HOST.isEmpty()) {
                // The default app exists when app/google-services.json does (the plugin initialises it).
                return FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            }
            // Local emulator: always a separate "demo-" project, even when google-services.json is
            // present, so test data can never reach the real project.
            FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP }?.let { return it }
            val options = FirebaseOptions.Builder()
                .setProjectId("demo-adhdlist")
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-key")
                .build()
            return FirebaseApp.initializeApp(context, options, EMULATOR_APP)
        }
    }
}
