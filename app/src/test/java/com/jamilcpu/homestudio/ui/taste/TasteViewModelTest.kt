package com.jamilcpu.homestudio.ui.taste

import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.data.api.TraitKind
import com.jamilcpu.homestudio.settle
import com.jamilcpu.homestudio.ui.now.NOTICE_RATE_LIMITED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasteViewModelTest {
    private fun taste(updatedAt: String, likes: List<String> = listOf("hardware teardowns", "long-form")) =
        """{"summary":"Prefers deep dives","likes":[${likes.joinToString(",") { "\"$it\"" }}],"dislikes":["clickbait"],"updated_at":"$updatedAt"}"""

    @Volatile
    private var current = taste("2026-11-01T09:00:00+00:00")

    @Volatile
    private var claude = "ok"

    private val server = FakeServer().apply {
        ok("GET", "/api/taste") { current }
        ok("POST", "/api/taste/corrections") { """{"id":5}""" }
        ok("DELETE", "/api/taste/traits") {
            taste("2026-11-01T15:00:00+00:00", likes = listOf("long-form")).also { current = it }
        }
        ok("GET", "/api/status") {
            """{"player":"idle","bluetooth":"connected","cookies":"ok","claude":"$claude"}"""
        }
    }

    private fun TestScope.started(): TasteViewModel =
        TasteViewModel(server.api(), CoroutineScope(StandardTestDispatcher(testScheduler))).also {
            it.start()
            awaitUntil { it.uiState.value.loaded }
        }

    private fun TestScope.tick() {
        advanceTimeBy(TASTE_POLL_MS)
        settle()
    }

    @Test
    fun removeTraitCallsApi() = runTest {
        val vm = started()
        vm.removeTrait(TraitKind.Likes, "hardware teardowns")
        awaitUntil { vm.uiState.value.taste?.likes == listOf("long-form") }
        assertEquals(
            """{"kind":"likes","text":"hardware teardowns"}""",
            server.calls("DELETE", "/api/taste/traits").single().bodyText(),
        )
        settle()
        assertFalse(vm.uiState.value.updating) // the response already carries a newer updated_at
        assertNull(vm.uiState.value.note)
    }

    @Test
    fun correctionPollsUntilUpdatedAtChanges() = runTest {
        val vm = started()
        vm.onInput("less politics, more hardware")
        vm.submitCorrection()
        awaitUntil { vm.uiState.value.updating }
        assertEquals("""{"text":"less politics, more hardware"}""", server.calls("POST", "/api/taste/corrections").single().bodyText())
        assertEquals("", vm.uiState.value.input)
        tick()
        assertTrue(vm.uiState.value.updating)
        current = taste("2026-11-01T15:05:00+00:00", likes = listOf("hardware"))
        tick()
        awaitUntil { !vm.uiState.value.updating }
        assertEquals(listOf("hardware"), vm.uiState.value.taste?.likes)
        assertNull(vm.uiState.value.note)
    }

    @Test
    fun correctionGivesUpAfterSixtySeconds() = runTest {
        val vm = started()
        vm.onInput("more jazz")
        vm.submitCorrection()
        awaitUntil { vm.uiState.value.updating }
        repeat((TASTE_TIMEOUT_MS / TASTE_POLL_MS).toInt()) { tick() }
        awaitUntil { !vm.uiState.value.updating }
        assertEquals(TASTE_NEXT_RUN, vm.uiState.value.note)
        val polls = server.calls("GET", "/api/taste").size
        tick()
        tick()
        assertEquals(polls, server.calls("GET", "/api/taste").size) // stopped
        assertEquals(21, polls) // the first load plus 20 polls
    }

    @Test
    fun claudeProblemShownInsteadOfWaiting() = runTest {
        claude = "rate_limited"
        val vm = started()
        vm.onInput("more jazz")
        vm.submitCorrection()
        awaitUntil { vm.uiState.value.updating }
        tick()
        awaitUntil { !vm.uiState.value.updating }
        assertEquals(NOTICE_RATE_LIMITED, vm.uiState.value.note)
    }

    @Test
    fun claudeStatesMapToNotices() {
        assertNull(claudeProblem("ok"))
        assertNull(claudeProblem("unknown"))
        assertEquals(NOTICE_RATE_LIMITED, claudeProblem("rate_limited"))
        assertEquals("Agent isn't logged in on the Pi.", claudeProblem("not_logged_in"))
        assertEquals(NOTICE_CLAUDE_MISSING, claudeProblem("missing"))
    }

    @Test
    fun noProfileYet() = runTest {
        current = "null"
        val vm = started()
        assertNull(vm.uiState.value.taste)
    }

    @Test
    fun unreachableSetsFlag() = runTest {
        server.down = true
        val vm = TasteViewModel(server.api(), CoroutineScope(StandardTestDispatcher(testScheduler)))
        vm.start()
        awaitUntil { vm.uiState.value.unreachable }
        server.down = false
        vm.start()
        awaitUntil { vm.uiState.value.loaded && !vm.uiState.value.unreachable }
    }
}
