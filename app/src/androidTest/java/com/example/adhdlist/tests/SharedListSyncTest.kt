package com.example.adhdlist.tests

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.adhdlist.BaseUiTest
import com.example.adhdlist.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.example.adhdlist.data.model.Category
import com.example.adhdlist.data.model.Task
import com.example.adhdlist.data.model.TaskList
import com.example.adhdlist.data.room.TaskDatabase
import com.example.adhdlist.data.sync.SharedListsSync
import com.example.adhdlist.domain.util.Result
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Two people on one list, end to end, against the local Firebase emulator. The app is one person.
 * The other person writes straight to the emulator over REST as "owner" (rules bypassed).
 * Runs only when built with -PfirebaseEmulatorHost=10.0.2.2 and `npm run emulators` is up in firebase/.
 */
@HiltAndroidTest
class SharedListSyncTest : BaseUiTest() {
    @Inject
    lateinit var sync: SharedListsSync

    private val host = BuildConfig.FIREBASE_EMULATOR_HOST
    private val project = "projects/demo-adhdlist/databases/(default)/documents"

    @Before
    fun startSync() {
        assumeTrue("Firebase emulator not configured", host.isNotEmpty())
        http("DELETE", "http://$host:8080/emulator/v1/$project")
        // A test account left in the app's storage by an earlier run is unknown to a fresh auth
        // emulator (INVALID_REFRESH_TOKEN), so start every test signed out.
        FirebaseAuth.getInstance(FirebaseApp.getInstance("local-emulator")).signOut()
        // HiltTestApplication doesn't run AdhdListApplication.onCreate, so start the sync here.
        sync.start(MainScope())
    }

    @Test
    fun editsFlowBothWaysBetweenTwoPeople() {
        // Person A (the app) makes a list with one task and shares it.
        composeTestRule.onNodeWithTag("ListsTextField").performTextInput("Trip")
        composeTestRule.onNodeWithTag("AddButton").performClick()
        waitFor { textShown("Trip") }
        composeTestRule.onNodeWithText("Trip").performClick()
        waitFor { textShown("Nothing here yet") }
        composeTestRule.onNodeWithTag("TasksTextField").performTextInput("passport")
        composeTestRule.onNodeWithTag("AddButton").performClick()
        waitFor { textShown("passport") }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val code = runBlocking {
            val listId = TaskDatabase.getInstance(context).listDao().getAllLists().first().single().list.id
            (listRepository.shareList(listId) as Result.Success).data
        }
        waitFor { textShown("Shared list") }

        // It reached the cloud.
        waitFor { remoteTasks(code).any { it.message == "passport" } }

        // Person B adds a task and renames the list; A sees both.
        commit("""{"update":{"name":"$project/lists/$code/tasks/fromB",
            "fields":{"message":{"stringValue":"sunscreen"},"state":{"booleanValue":false},"position":{"integerValue":"1"}}}}""",
            """{"update":{"name":"$project/lists/$code","fields":{"name":{"stringValue":"Beach trip"}}},
            "updateMask":{"fieldPaths":["name"]}}""")
        waitFor { textShown("sunscreen") && textShown("Beach trip") }

        // A checks B's task; B sees it.
        composeTestRule.onAllNodesWithTag("TaskCheckBox")[1].performClick()
        waitFor { remoteTasks(code).single { it.id == "fromB" }.state }

        // A edits B's item by pressing and holding it; B sees the new text, and the check survives.
        composeTestRule.onNodeWithTag("TaskListElement1").performTouchInput { longClick() }
        composeTestRule.onNodeWithTag("DialogTextField").performTextClearance()
        composeTestRule.onNodeWithTag("DialogTextField").performTextInput("SPF 50")
        composeTestRule.onNodeWithText("Save").performClick()
        waitFor { remoteTasks(code).single { it.id == "fromB" }.let { it.message == "SPF 50" && it.state } }

        // B deletes A's task; it disappears for A.
        val passportId = remoteTasks(code).single { it.message == "passport" }.id
        commit("""{"delete":"$project/lists/$code/tasks/$passportId"}""")
        waitFor { taskTexts() == listOf("SPF 50") }
    }

    @Test
    fun aShareTheServerRejectsLeavesTheListLocalAndIntact() {
        // A task over the rules' 1000-character limit makes the server reject the whole share batch.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = TaskDatabase.getInstance(context)
        val listId = runBlocking {
            db.listDao().insert(TaskList("Too long")).toInt().also {
                db.taskDao().insert(Task(it, "x".repeat(1001)))
                db.taskDao().insert(Task(it, "short"))
            }
        }

        val result = runBlocking { listRepository.shareList(listId) }
        assert(result is Result.Error) { "share should fail, got $result" }

        Thread.sleep(5_000) // give a wrong mirror deletion time to happen
        runBlocking {
            val list = db.listDao().getListOnce(listId)
            assertEquals(null, list?.remoteId)
            assertEquals("Too long", list?.name)
            val tasks = db.taskDao().getTasksOnce(listId)
            assertEquals(2, tasks.size)
            assert(tasks.all { it.remoteId == null })
        }
    }

    @Test
    fun joiningUnderTheRealRulesThenLeavingAndRejoining() {
        // Someone else's list, written as that person (seeded past the rules).
        val code = "JoinMeJoinMeJoinMe12"
        commit("""{"update":{"name":"$project/lists/$code",
            "fields":{"name":{"stringValue":"Book club"},"members":{"arrayValue":{"values":[{"stringValue":"someoneElse"}]}}}}}""",
            """{"update":{"name":"$project/lists/$code/tasks/t1",
            "fields":{"message":{"stringValue":"Read chapter 3"},"state":{"booleanValue":false},"position":{"integerValue":"0"}}}}""")

        // The app joins as itself, so the security rules apply to every step from here.
        runBlocking { listRepository.joinList(code) }.let { assert(it is Result.Success) { "join failed: $it" } }
        waitFor { textShown("Book club") }
        composeTestRule.onNodeWithText("Book club").performClick()
        waitFor { taskTexts() == listOf("Read chapter 3") }
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Leave, then join again: the list must come back without restarting the app.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val local = runBlocking { TaskDatabase.getInstance(context).listDao().getByRemoteId(code)!! }
        runBlocking { listRepository.deleteList(local) }.let { assert(it is Result.Success) { "leave failed: $it" } }
        waitFor { !textShown("Book club") }
        runBlocking { listRepository.joinList(code) }.let { assert(it is Result.Success) { "join failed: $it" } }
        waitFor { textShown("Book club") }
    }

    @Test
    fun categoriesTravelWithTheListAndBothWays() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = TaskDatabase.getInstance(context)
        // A local list with a category and an item in it, then shared.
        val listId = runBlocking {
            db.listDao().insert(TaskList("Zakupy")).toInt().also { id ->
                val category = db.categoryDao().insert(Category(id, "Warzywa")).toInt()
                db.taskDao().insert(Task(id, "cukinia", categoryId = category))
            }
        }
        val code = runBlocking { (listRepository.shareList(listId) as Result.Success).data }

        // The cloud has the category, and the item points at it.
        waitFor { remoteCategories(code).values.contains("Warzywa") }
        val categoryDoc = remoteCategories(code).entries.single { it.value == "Warzywa" }.key
        waitFor { remoteTasks(code).single().category == categoryDoc }

        // The other person adds "papryka" into Warzywa; here it appears under that header.
        commit("""{"update":{"name":"$project/lists/$code/tasks/fromB",
            "fields":{"message":{"stringValue":"papryka"},"state":{"booleanValue":false},"position":{"integerValue":"1"},
            "category":{"stringValue":"$categoryDoc"}}}}""")
        composeTestRule.onNodeWithText("Zakupy").performClick()
        waitFor { taskTexts() == listOf("cukinia", "papryka") }
        composeTestRule.onNodeWithText("Warzywa").performClick() // close it: both are inside
        waitFor { taskTexts().isEmpty() }
        composeTestRule.onNodeWithText("Warzywa").performClick()
        waitFor { taskTexts().size == 2 }

        // Here, "papryka" moves to no category; in the cloud its category field goes away.
        composeTestRule.onAllNodesWithTag("TaskListElement1")[0].performTouchInput { longClick() }
        composeTestRule.onAllNodesWithText("No category").filterToOne(hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNodeWithText("Save").performClick()
        waitFor { remoteTasks(code).single { it.id == "fromB" }.category == null }
    }

    private fun remoteCategories(code: String): Map<String, String> {
        val body = JSONObject(http("GET", "http://$host:8080/v1/$project/lists/$code/categories"))
        val docs = body.optJSONArray("documents") ?: JSONArray()
        return (0 until docs.length()).associate { i ->
            val doc = docs.getJSONObject(i)
            doc.getString("name").substringAfterLast('/') to doc.getJSONObject("fields").getJSONObject("name").getString("stringValue")
        }
    }

    private data class RemoteTask(val id: String, val message: String, val state: Boolean, val category: String? = null)

    private fun remoteTasks(code: String): List<RemoteTask> {
        val body = JSONObject(http("GET", "http://$host:8080/v1/$project/lists/$code/tasks"))
        val docs = body.optJSONArray("documents") ?: JSONArray()
        return (0 until docs.length()).map { i ->
            val doc = docs.getJSONObject(i)
            val fields = doc.getJSONObject("fields")
            RemoteTask(
                id = doc.getString("name").substringAfterLast('/'),
                message = fields.getJSONObject("message").getString("stringValue"),
                state = fields.getJSONObject("state").getBoolean("booleanValue"),
                category = fields.optJSONObject("category")?.getString("stringValue")
            )
        }
    }

    private fun commit(vararg writes: String) {
        http("POST", "http://$host:8080/v1/$project:commit", """{"writes":[${writes.joinToString(",")}]}""")
    }

    private fun http(method: String, url: String, body: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer owner")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val ok = connection.responseCode in 200..299
        val text = (if (ok) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
        check(ok) { "$method $url -> ${connection.responseCode}: $text" }
        return text
    }

    private fun textShown(text: String) =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun taskTexts() = composeTestRule.onAllNodesWithTag("TaskText", useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.Text].joinToString() }

    private fun waitFor(condition: () -> Boolean) = composeTestRule.waitUntil(15_000, condition)
}
