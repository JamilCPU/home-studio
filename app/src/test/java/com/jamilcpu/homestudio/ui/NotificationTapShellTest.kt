package com.jamilcpu.homestudio.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.ui.rate.RateSheetController
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the shell does with a notification tap: switch to Now Playing and show the Rate sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationTapShellTest {
    @get:Rule
    val rule = createComposeRule()

    private val server = FakeServer().apply {
        ok("GET", "/api/agent/requests") { """{"requests":[]}""" }
        ok("GET", "/api/history") { """{"entries":[]}""" }
    }

    private fun waitFor(check: () -> Unit) {
        rule.waitUntil(5_000) { runCatching(check).isSuccess }
    }

    @Test
    fun tabRequestOpensNowPlayingWithRateSheet() {
        val controller = RateSheetController()
        val tabs = TabRequests()
        rule.setContent {
            HomeStudioTheme {
                HomeStudioRoot(
                    MutableStateFlow("http://pi.test:8765"),
                    server.api(),
                    rateSheet = controller,
                    tabRequests = tabs,
                ) {}
            }
        }
        rule.onNodeWithTag("topBarTitle").assertTextEquals("Agent")
        tabs.request(Routes.NOW)
        controller.open("abc123", "How GPUs Work")
        waitFor { rule.onNodeWithTag("tab_now").assertIsSelected() }
        waitFor { rule.onNodeWithTag("rateSheet").assertExists() }
        rule.onNodeWithText("How GPUs Work").assertExists()
        assertNull(tabs.requested.value) // consumed once
    }
}
