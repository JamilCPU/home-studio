package com.jamilcpu.homestudio.ui.queue

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.data.api.QueueItemDto
import com.jamilcpu.homestudio.queueItemJson
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val items = listOf(
        QueueItemDto(id = 1, videoId = "a", title = "How GPUs Work", durationS = 1834, source = "agent"),
        QueueItemDto(id = 2, videoId = "b", title = "Morning news", durationS = 600, source = "fallback"),
        QueueItemDto(id = 3, videoId = "c", title = "My pick", durationS = 300, source = "user"),
    )

    private fun show(state: QueueUiState, actions: QueueActions = QueueActions()) {
        rule.setContent { HomeStudioTheme { QueueScreen(state, actions) } }
    }

    @Test
    fun rowsShowTitlesTagsAndRemaining() {
        show(QueueUiState(items = items, remainingS = 6060, loaded = true))
        rule.onNodeWithText("How GPUs Work").assertExists()
        rule.onNodeWithText("Agent").assertExists()
        rule.onNodeWithText("Morning").assertExists()
        rule.onNodeWithText("You added").assertExists()
        rule.onNodeWithText("Ends in 1h 41m, then stops").assertExists()
    }

    @Test
    fun unreachableShowsBannerWithRetry() {
        var retries = 0
        show(QueueUiState(unreachable = true), QueueActions(onRetry = { retries++ }))
        rule.onNodeWithText("Can't reach your Pi").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun clearAsksForConfirmation() {
        var clears = 0
        show(QueueUiState(items = items, loaded = true), QueueActions(onClear = { clears++ }))
        rule.onNodeWithTag("clearQueue").performClick()
        rule.onNodeWithText("Clear the whole queue?").assertExists()
        assertEquals(0, clears)
        rule.onNodeWithTag("confirmClear").performClick()
        assertEquals(1, clears)
        rule.onNodeWithText("Clear the whole queue?").assertDoesNotExist()
    }

    @Test
    fun searchDialogLinkPasteEnqueuesUrl() {
        val server = FakeServer().apply {
            ok("GET", "/api/queue") { """{"items":[${queueItemJson(1)}],"remaining_s":600}""" }
            ok("POST", "/api/queue") { queueItemJson(9, source = "user") }
        }
        rule.setContent {
            val scope = rememberCoroutineScope()
            val vm = remember { QueueViewModel(server.api(), scope) }
            HomeStudioTheme { QueueRoute(vm) }
        }
        rule.onNodeWithText("🔍 Search / add").performClick()
        rule.onNodeWithTag("searchField").performTextInput("https://youtu.be/dQw4w9WgXcQ")
        rule.onNodeWithText("Add link").assertExists()
        rule.onNodeWithTag("searchSubmit").performClick()
        rule.waitUntil(5_000) { server.calls("POST", "/api/queue").isNotEmpty() }
        assertEquals(
            """{"url":"https://youtu.be/dQw4w9WgXcQ","where":"end"}""",
            server.calls("POST", "/api/queue").single().bodyText(),
        )
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("searchField").assertDoesNotExist() }.isSuccess }
        assertEquals(0, server.calls("GET", "/api/search").size)
    }

    @Test
    fun longPressDragOnHandleSendsOneReorder() {
        val server = FakeServer().apply {
            ok("GET", "/api/queue") { """{"items":[${queueItemJson(1)},${queueItemJson(2)},${queueItemJson(3)}],"remaining_s":1800}""" }
            ok("PUT", "/api/queue/order") { """{"items":[${queueItemJson(2)},${queueItemJson(1)},${queueItemJson(3)}]}""" }
        }
        lateinit var vm: QueueViewModel
        rule.setContent {
            val scope = rememberCoroutineScope()
            vm = remember { QueueViewModel(server.api(), scope) }
            HomeStudioTheme { QueueRoute(vm) }
        }
        rule.waitUntil(5_000) { vm.uiState.value.items.size == 3 }
        val rowHeight = rule.onNodeWithTag("queueRow_1").fetchSemanticsNode().size.height.toFloat()
        val handle = rule.onNodeWithTag("dragHandle_1", useUnmergedTree = true)
        handle.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 1f))
        }
        rule.waitForIdle()
        assertTrue(vm.uiState.value.dragging)
        handle.performTouchInput { repeat(6) { moveBy(Offset(0f, rowHeight * 0.2f)) } }
        rule.waitForIdle()
        assertEquals(listOf(2L, 1L, 3L), vm.uiState.value.items.map { it.id })
        assertTrue(server.calls("PUT", "/api/queue/order").isEmpty()) // nothing until the finger lifts
        handle.performTouchInput { up() }
        rule.waitUntil(5_000) { server.calls("PUT", "/api/queue/order").isNotEmpty() }
        rule.waitForIdle()
        assertEquals(1, server.calls("PUT", "/api/queue/order").size)
        assertEquals("""{"ids":[2,1,3]}""", server.calls("PUT", "/api/queue/order").single().bodyText())
    }

    @Test
    fun playButtonPlaysThatRow() {
        val played = mutableListOf<Long>()
        show(QueueUiState(items = items, loaded = true), QueueActions(onPlay = { played += it }))
        rule.onNodeWithTag("playQueue_2").performClick()
        assertEquals(listOf(2L), played)
    }
}
