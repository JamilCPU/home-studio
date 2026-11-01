package com.jamilcpu.homestudio.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.ui.rate.RateSheetController
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RateFromHistoryTest {
    @get:Rule
    val rule = createComposeRule()

    private val server = FakeServer().apply {
        ok("GET", "/api/history") {
            """{"entries":[{"play_id":17,"video":{"video_id":"dQw4w9WgXcQ","title":"How GPUs Work","channel":"C","thumbnail_url":null,"duration_s":1834,"chapters":[]},"started_at":"${Instant.now()}","seconds_listened":912.5,"end_reason":"finished","source":"agent","rating":null}]}"""
        }
        ok("GET", "/api/agent/requests") { """{"requests":[]}""" }
        ok("POST", "/api/ratings") { """{"id":12}""" }
    }

    private fun waitFor(check: () -> Unit) {
        rule.waitUntil(5_000) { runCatching(check).isSuccess }
    }

    @Test
    fun historyRowOpensRateSheetAndSaving() {
        val controller = RateSheetController()
        rule.setContent {
            HomeStudioTheme {
                HomeStudioRoot(MutableStateFlow("http://pi.test:8765"), server.api(), rateSheet = controller) {}
            }
        }
        rule.onNodeWithTag("tab_history").performClick()
        waitFor { rule.onNodeWithText("How GPUs Work").assertExists() }
        rule.onNodeWithText("Rate?").assertExists()
        rule.onNodeWithText("How GPUs Work").performClick()
        waitFor { rule.onNodeWithTag("rateSheet").assertExists() }
        assertEquals("dQw4w9WgXcQ", controller.target.value?.videoId)
        rule.onNodeWithText("👍 Like").performClick()
        rule.onNodeWithTag("rateSave").performClick()
        rule.waitUntil(5_000) { server.calls("POST", "/api/ratings").isNotEmpty() }
        assertEquals(
            """{"video_id":"dQw4w9WgXcQ","liked":true}""",
            server.calls("POST", "/api/ratings").single().bodyText(),
        )
        waitFor { rule.onNodeWithText("Rated").assertExists() }
        waitFor { rule.onNodeWithTag("rateSheet").assertDoesNotExist() }
    }
}
