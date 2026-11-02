package com.jamilcpu.homestudio.ui.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import com.jamilcpu.homestudio.data.api.RatingDto
import com.jamilcpu.homestudio.data.api.VideoDto
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.now().toString()

    private val rated = HistoryEntryDto(
        playId = 2,
        video = VideoDto(videoId = "b", title = "How GPUs Work"),
        startedAt = now,
        endReason = "finished",
        rating = RatingDto(liked = true, satisfaction = 8),
    )
    private val unrated = HistoryEntryDto(
        playId = 1,
        video = VideoDto(videoId = "a", title = "Clickbait thing"),
        startedAt = now,
        secondsListened = 75.0,
        endReason = "skipped",
    )

    private fun show(state: HistoryUiState, actions: HistoryActions = HistoryActions()) {
        rule.setContent { HomeStudioTheme { HistoryScreen(state, actions) } }
    }

    @Test
    fun rowsShowLabelsUnderToday() {
        show(HistoryUiState(entries = listOf(rated, unrated), loaded = true, unratedCount = 4))
        rule.onNodeWithText("Today").assertExists()
        rule.onNodeWithText("👍 8").assertExists()
        rule.onNodeWithText("skipped 1:15").assertExists()
        rule.onNodeWithText("Unrated (4)").assertExists()
    }

    @Test
    fun tappingARowOpensIt() {
        val opened = mutableListOf<String>()
        show(HistoryUiState(entries = listOf(rated, unrated), loaded = true), HistoryActions(onOpen = { opened += it.video.videoId }))
        rule.onNodeWithText("Clickbait thing").performClick()
        assertEquals(listOf("a"), opened)
    }

    @Test
    fun unratedChipFilters() {
        val filters = mutableListOf<Boolean>()
        show(HistoryUiState(loaded = true, unratedCount = 3), HistoryActions(onUnratedOnly = { filters += it }))
        rule.onNodeWithText("Unrated (3)").performClick()
        assertEquals(listOf(true), filters)
    }

    @Test
    fun unreachableShowsBannerWithRetry() {
        var retries = 0
        show(HistoryUiState(unreachable = true), HistoryActions(onRetry = { retries++ }))
        rule.onNodeWithText("Can't reach your Pi").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun playButtonPlaysAgainWithoutOpeningRate() {
        val played = mutableListOf<String>()
        val opened = mutableListOf<String>()
        show(
            HistoryUiState(entries = listOf(rated, unrated), loaded = true),
            HistoryActions(onPlay = { played += it.video.videoId }, onOpen = { opened += it.video.videoId }),
        )
        rule.onNodeWithTag("playHistory_${unrated.playId}").performClick()
        assertEquals(listOf(unrated.video.videoId), played)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun noticeShowsWithDismiss() {
        var dismissed = 0
        show(HistoryUiState(loaded = true, notice = "Playing again"), HistoryActions(onDismiss = { dismissed++ }))
        rule.onNodeWithText("Playing again").assertExists()
        rule.onNodeWithText("Dismiss").performClick()
        assertEquals(1, dismissed)
    }
}
