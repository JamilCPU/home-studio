package com.jamilcpu.homestudio.ui.rate

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RateSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: RateUiState, actions: RateActions = RateActions()) {
        rule.setContent { HomeStudioTheme { RateSheetContent("How GPUs Work", state, actions) } }
    }

    @Test
    fun saveDisabledWhenEmpty() {
        show(RateUiState())
        rule.onNodeWithTag("rateSave").assertIsNotEnabled()
    }

    @Test
    fun showsControlsAndSelection() {
        val liked = mutableListOf<Boolean>()
        val scores = mutableListOf<Int>()
        show(RateUiState(liked = true, satisfaction = 8), RateActions(onLiked = { liked += it }, onSatisfaction = { scores += it }))
        rule.onNodeWithText("How GPUs Work").assertExists()
        rule.onNodeWithTag("liked_true").assertIsSelected()
        rule.onNodeWithTag("sat_8").assertIsSelected()
        rule.onNodeWithTag("rateSave").assertIsEnabled()
        rule.onNodeWithText("👎 Dislike").performClick()
        rule.onNodeWithTag("sat_8").performClick()
        assertEquals(listOf(false), liked)
        assertEquals(listOf(8), scores)
    }

    @Test
    fun serverMessageShownInline() {
        show(RateUiState(notes = "x", error = "notes: String should have at most 2000 characters"))
        rule.onNodeWithText("notes: String should have at most 2000 characters").assertExists()
    }

    @Test
    fun unreachableShowsBannerWithRetry() {
        var saves = 0
        show(RateUiState(liked = true, unreachable = true), RateActions(onSave = { saves++ }))
        rule.onNodeWithText("Can't reach your Pi").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, saves)
    }
}
