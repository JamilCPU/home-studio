package com.jamilcpu.homestudio.ui.morning

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jamilcpu.homestudio.data.api.MorningDto
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MorningScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val dto = MorningDto(
        enabled = true,
        wakeTime = "07:45",
        days = listOf(0, 2),
        topics = listOf("AI", "hardware"),
        count = 3,
        buildLeadMin = 15,
    )

    private fun show(state: MorningUiState, actions: MorningActions = MorningActions()) {
        rule.setContent { HomeStudioTheme { MorningScreen(state, actions) } }
    }

    @Test
    fun leadTimeLabelFollowsTheStepper() {
        val leads = mutableListOf<Int>()
        show(MorningUiState(saved = dto, draft = dto), MorningActions(onLead = { leads += it }))
        rule.onNodeWithText("7:45 AM").assertExists()
        rule.onNodeWithText("Central Time · agent starts picking at 7:30 AM").assertExists()
        rule.onNodeWithTag("lead_value").assertExists()
        rule.onNodeWithTag("lead_plus").performScrollTo().performClick()
        rule.onNodeWithTag("lead_minus").performScrollTo().performClick()
        assertEquals(listOf(16, 14), leads)
    }

    @Test
    fun daysTopicsAndCount() {
        val days = mutableListOf<Int>()
        val removed = mutableListOf<String>()
        val counts = mutableListOf<Int>()
        show(
            MorningUiState(saved = dto, draft = dto),
            MorningActions(onToggleDay = { days += it }, onRemoveTopic = { removed += it }, onCount = { counts += it }),
        )
        rule.onNodeWithTag("day_0").assertIsSelected()
        rule.onNodeWithTag("day_1").assertIsNotSelected()
        rule.onNodeWithTag("day_6").performScrollTo().performClick()
        rule.onNodeWithTag("topic_hardware").performScrollTo().performClick()
        rule.onNodeWithTag("count_plus").performScrollTo().performClick()
        rule.onNodeWithText("+ add").assertExists()
        rule.onNodeWithText("▶ Test it now").performScrollTo().assertExists()
        assertEquals(listOf(6), days)
        assertEquals(listOf("hardware"), removed)
        assertEquals(listOf(4), counts)
    }

    @Test
    fun serverErrorShownInline() {
        show(MorningUiState(saved = dto, draft = dto.copy(count = 2), error = "count: Input should be less than or equal to 6"))
        rule.onNodeWithText("count: Input should be less than or equal to 6").assertExists()
    }

    @Test
    fun unreachableShowsBannerWithRetry() {
        var retries = 0
        show(MorningUiState(unreachable = true), MorningActions(onRetry = { retries++ }))
        rule.onNodeWithText("Can't reach your Pi").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
