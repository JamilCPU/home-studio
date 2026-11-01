package com.jamilcpu.homestudio.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.net.ConnectException
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RootTest {
    @get:Rule
    val rule = createComposeRule()

    private val statusOk =
        """{"ok":true,"data":{"player":"idle","bluetooth":"connected","cookies":"ok","claude":"ok"},"error":null}"""

    private val softNote =
        "Works on home Wi-Fi only. Use your Pi's Tailscale name to connect from anywhere."

    private val tabs = listOf("now", "agent", "queue", "history", "taste")

    private fun apiResponding(reachable: Boolean = true): HomeStudioApi {
        val engine = MockEngine {
            if (!reachable) throw ConnectException("refused")
            respond(statusOk, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return HomeStudioApi({ null }, engine)
    }

    private fun show(
        url: String?,
        api: HomeStudioApi = apiResponding(),
        saved: MutableList<String> = mutableListOf(),
    ) {
        val flow = MutableStateFlow(url)
        rule.setContent {
            HomeStudioTheme {
                HomeStudioRoot(flow, api) {
                    saved += it
                    flow.value = it
                }
            }
        }
    }

    private fun waitForNode(tag: String, check: () -> Unit) {
        rule.waitUntil(5_000) { runCatching(check).isSuccess }
    }

    @Test
    fun showsConnectWhenNoUrl() {
        show(null)
        rule.onNodeWithText("Connect to your Pi").assertExists()
        rule.onNodeWithTag("tab_agent").assertDoesNotExist()
    }

    @Test
    fun startsOnAgentTab() {
        show("http://pi:8765")
        rule.onNodeWithTag("tab_agent").assertIsSelected()
        rule.onNodeWithTag("topBarTitle").assertTextEquals("Agent")
        rule.onNodeWithTag("screen_agent").assertExists()
    }

    @Test
    fun bottomBarHasFiveTabsInOrder() {
        show("http://pi:8765")
        val lefts = tabs.map { rule.onNodeWithTag("tab_$it").getUnclippedBoundsInRoot().left.value }
        assertEquals(lefts.sorted(), lefts)
        assertEquals(5, lefts.toSet().size)
        listOf("Now", "Agent", "Queue", "History", "Taste").forEach {
            rule.onNodeWithTag("tab_${it.lowercase()}").assert(hasText(it))
        }
    }

    @Test
    fun tabClickSwitchesScreen() {
        show("http://pi:8765")
        rule.onNodeWithTag("tab_queue").performClick()
        rule.onNodeWithTag("screen_queue").assertExists()
        rule.onNodeWithTag("topBarTitle").assertTextEquals("Queue")
    }

    @Test
    fun gearOpensMorning() {
        show("http://pi:8765")
        rule.onNodeWithContentDescription("Morning settings").performClick()
        rule.onNodeWithTag("screen_morning").assertExists()
        rule.onNodeWithTag("topBarTitle").assertTextEquals("Morning")
    }

    @Test
    fun saveEnabledOnlyAfterSuccessfulTest() {
        val saved = mutableListOf<String>()
        show(null, saved = saved)
        rule.onNodeWithTag("saveButton").assertIsNotEnabled()
        rule.onNodeWithTag("urlField").performTextInput("http://192.0.2.5:8765/")
        rule.onNodeWithTag("saveButton").assertIsNotEnabled()
        rule.onNodeWithTag("testButton").performClick()
        waitForNode("saveButton") { rule.onNodeWithTag("saveButton").assertIsEnabled() }
        rule.onNodeWithTag("saveButton").performClick()
        rule.waitForIdle()
        assertEquals(listOf("http://192.0.2.5:8765"), saved)
        rule.onNodeWithTag("screen_agent").assertExists()
    }

    @Test
    fun unreachableShowsInlineError() {
        show(null, api = apiResponding(reachable = false))
        rule.onNodeWithTag("urlField").performTextInput("http://192.0.2.5:8765")
        rule.onNodeWithTag("testButton").performClick()
        waitForNode("error") { rule.onNodeWithText("Can't reach your Pi at that address").assertExists() }
        rule.onNodeWithTag("saveButton").assertIsNotEnabled()
    }

    @Test
    fun invalidUrlShowsMessageWithoutCalling() {
        show(null)
        rule.onNodeWithTag("urlField").performTextInput("http://pi:8765/api")
        rule.onNodeWithTag("testButton").performClick()
        rule.onNodeWithText("Enter just the address, like http://192.168.x.x:8765").assertExists()
    }

    @Test
    fun softNoteShownForLanHost() {
        show(null)
        rule.onNodeWithTag("urlField").performTextInput("http://192.0.2.5:8765")
        rule.onNodeWithText(softNote).assertExists()
    }

    @Test
    fun softNoteHiddenForTailscaleHost() {
        show(null)
        rule.onNodeWithTag("urlField").performTextInput("https://pi.tail1234.ts.net:8765")
        rule.onNodeWithText(softNote).assertDoesNotExist()
    }
}
