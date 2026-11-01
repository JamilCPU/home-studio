package com.jamilcpu.homestudio.ui.taste

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jamilcpu.homestudio.data.api.TasteDto
import com.jamilcpu.homestudio.data.api.TraitKind
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasteScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val profile = TasteDto(
        summary = "Prefers long-form engineering deep dives",
        likes = listOf("hardware teardowns"),
        dislikes = listOf("clickbait"),
        updatedAt = "2026-11-01T09:00:00+00:00",
    )

    private fun show(state: TasteUiState, actions: TasteActions = TasteActions()) {
        rule.setContent { HomeStudioTheme { TasteScreen(state, actions) } }
    }

    @Test
    fun showsSummaryTraitsAndRemoves() {
        val removed = mutableListOf<Pair<TraitKind, String>>()
        show(TasteUiState(taste = profile, loaded = true), TasteActions(onRemove = { k, t -> removed += k to t }))
        rule.onNodeWithText("Prefers long-form engineering deep dives").assertExists()
        rule.onNodeWithText("Likes").assertExists()
        rule.onNodeWithText("Dislikes").assertExists()
        rule.onNodeWithText("Correct it: \"I actually like…\"").assertExists()
        rule.onNodeWithContentDescription("Remove clickbait").performClick()
        assertEquals(listOf(TraitKind.Dislikes to "clickbait"), removed)
    }

    @Test
    fun showsUpdatingAndNote() {
        show(TasteUiState(taste = profile, loaded = true, updating = true))
        rule.onNodeWithText("Updating…").assertExists()
    }

    @Test
    fun showsTimeoutNote() {
        show(TasteUiState(taste = profile, loaded = true, note = TASTE_NEXT_RUN))
        rule.onNodeWithText("Will update after the agent's next run").assertExists()
    }

    @Test
    fun unreachableShowsBannerWithRetry() {
        var retries = 0
        show(TasteUiState(unreachable = true), TasteActions(onRetry = { retries++ }))
        rule.onNodeWithText("Can't reach your Pi").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
