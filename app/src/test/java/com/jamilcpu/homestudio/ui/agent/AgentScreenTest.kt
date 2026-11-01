package com.jamilcpu.homestudio.ui.agent

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jamilcpu.homestudio.data.api.AgentRequestDto
import com.jamilcpu.homestudio.data.api.QueueItemDto
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val item = QueueItemDto(id = 1, videoId = "a", title = "How GPUs Work", durationS = 600, source = "agent")

    private fun request(
        id: Long,
        status: String,
        prompt: String = "gpu stuff",
        kind: String = "user",
        error: String? = null,
        items: List<QueueItemDto> = emptyList(),
    ) = AgentRequestDto(id, prompt, kind, "now", status, summary = "you like hardware", error = error, items = items)

    private fun show(
        state: AgentUiState,
        actions: AgentActions = AgentActions(),
        micAvailable: Boolean = true,
    ) {
        rule.setContent { HomeStudioTheme { AgentScreen(state, actions, micAvailable) } }
    }

    @Test
    fun morningRequestShowsLabelNotTemplate() {
        show(AgentUiState(history = listOf(request(1, "done", prompt = "LONG TEMPLATE TEXT", kind = "morning"))))
        rule.onNodeWithText("🌅 Morning set").assertExists()
        rule.onNodeWithText("LONG TEMPLATE TEXT").assertDoesNotExist()
    }

    @Test
    fun runningShowsWorkingBubble() {
        show(AgentUiState(history = listOf(request(1, "running"))))
        rule.onNodeWithText("Working… searching, reading transcripts").assertExists()
    }

    @Test
    fun doneShowsSummaryListAndActions() {
        val played = mutableListOf<Long>()
        val added = mutableListOf<Long>()
        show(
            AgentUiState(history = listOf(request(1, "done", items = listOf(item)))),
            AgentActions(onPlayNow = { played += it }, onAddToEnd = { added += it }),
        )
        rule.onNodeWithText("Queued 1 video (10 min):").assertExists()
        rule.onNodeWithText("How GPUs Work").assertExists()
        rule.onNodeWithText("Why: you like hardware").assertExists()
        rule.onNodeWithTag("playNow_1").performClick()
        rule.onNodeWithTag("addToEnd_1").performClick()
        assertEquals(listOf(1L), played)
        assertEquals(listOf(1L), added)
    }

    @Test
    fun failedShowsErrorAndTryAgain() {
        val retried = mutableListOf<Long>()
        show(
            AgentUiState(history = listOf(request(2, "failed", error = "rate limited"))),
            AgentActions(onRetry = { retried += it }),
        )
        rule.onNodeWithText("rate limited").assertExists()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf(2L), retried)
    }

    @Test
    fun micHiddenWhenUnavailable() {
        show(AgentUiState(), micAvailable = false)
        rule.onNodeWithTag("micButton").assertDoesNotExist()
    }

    @Test
    fun chipsAndPlaceholderShown() {
        val chips = mutableListOf<String>()
        show(AgentUiState(), AgentActions(onChip = { chips += it }))
        rule.onNodeWithText("Ask for something to play…").assertExists()
        rule.onNodeWithText("Classical playlist").performClick()
        assertEquals(listOf("Classical playlist"), chips)
    }
}
