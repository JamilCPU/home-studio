package com.jamilcpu.homestudio.ui.now

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class NowPlayingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val item = QueueItemDto(
        id = 1,
        videoId = "abc",
        title = "How GPUs Work",
        channel = "Some Channel",
        durationS = 1000,
        source = "agent",
        reason = "you liked hardware talks",
    )

    private val playing = NowPlayingUiState(
        item = item,
        state = "playing",
        positionS = 451.7,
        durationS = 1000.0,
        outputDevice = "Echo",
    )

    private fun show(state: NowPlayingUiState, actions: NowPlayingActions = NowPlayingActions()) {
        rule.setContent { HomeStudioTheme { NowPlayingScreen(state, actions) } }
    }

    @Test
    fun showsTrackDetails() {
        show(playing)
        rule.onNodeWithText("How GPUs Work").assertExists()
        rule.onNodeWithText("Some Channel").assertExists()
        rule.onNodeWithText("🤖 Picked because: you liked hardware talks").assertExists()
        rule.onNodeWithText("7:31 elapsed").assertExists()
        rule.onNodeWithText("−9:08 left").assertExists()
        rule.onNodeWithText("🔊 Echo").assertExists()
        rule.onNodeWithText("↗ Open in YouTube at 7:31").assertExists()
    }

    @Test
    fun statusNoticesRendered() {
        show(
            playing.copy(
                notices = listOf(NOTICE_COOKIES, NOTICE_BLUETOOTH, NOTICE_RATE_LIMITED, NOTICE_SOURCE_BROKEN),
            ),
        )
        rule.onNodeWithText("YouTube sign-in expired. Re-upload cookies on the Pi.").assertExists()
        rule.onNodeWithText("Speaker disconnected. Reconnecting…").assertExists()
        rule.onNodeWithText("Agent is rate-limited. Try again later.").assertExists()
        rule.onNodeWithText("Playback source broken (YouTube changed?). The Pi updates yt-dlp nightly.")
            .assertExists()
    }

    @Test
    fun nowPlayingShowsUnreachableBanner() {
        var retried = 0
        show(playing.copy(unreachable = true), NowPlayingActions(onRetry = { retried++ }))
        rule.onNodeWithText("Can't reach your Pi").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun emptyQueueShowsAskAgent() {
        var asked = 0
        show(NowPlayingUiState(queueEmpty = true), NowPlayingActions(onAskAgent = { asked++ }))
        rule.onNodeWithText("Queue empty").assertExists()
        rule.onNodeWithText("Ask the agent").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun rateButtonPassesVideoId() {
        var rated: String? = null
        show(playing, NowPlayingActions(onRate = { rated = it }))
        rule.onNodeWithText("✏️ Rate").performClick()
        assertEquals("abc", rated)
    }
}
