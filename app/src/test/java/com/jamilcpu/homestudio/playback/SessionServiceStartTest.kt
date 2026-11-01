package com.jamilcpu.homestudio.playback

import android.os.Looper
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import com.jamilcpu.homestudio.AppContainer
import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.HomeStudioApp
import com.jamilcpu.homestudio.MainActivity
import com.jamilcpu.homestudio.queueItemJson
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The activity itself keeps Now Playing polled, so the service starts from any tab. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionServiceStartTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: HomeStudioApp get() = RuntimeEnvironment.getApplication() as HomeStudioApp

    @Volatile
    private var piState = "idle"

    private val server = FakeServer().apply {
        ok("GET", "/api/now") {
            if (piState == "idle") {
                """{"state":"idle","item":null,"position_s":0.0,"duration_s":null,"output_device":null}"""
            } else {
                """{"state":"$piState","item":${queueItemJson(4)},"position_s":5.0,"duration_s":600.0,"output_device":null}"""
            }
        }
        ok("GET", "/api/status") { """{"player":"idle","bluetooth":"connected","cookies":"ok","claude":"ok"}""" }
        ok("GET", "/api/agent/requests") { """{"requests":[]}""" }
    }

    @Test
    fun startsSessionServiceWhenPollingSeesPlaybackOnAgentTab() {
        app.container = AppContainer(app, server.engine, MutableStateFlow("http://pi.test:8765"))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.onNodeWithTag("topBarTitle").assertTextEquals("Agent") // Now Playing is not showing
        awaitMain { server.calls("GET", "/api/now").isNotEmpty() }
        assertNull(shadowOf(app).peekNextStartedService()) // the Pi is idle so far

        piState = "playing" // arrives through polling, nothing is published by hand
        awaitMain { shadowOf(app).peekNextStartedService() != null }
        assertEquals(RemoteSessionService::class.java.name, shadowOf(app).nextStartedService.component?.className)

        scenario.close() // stopping the activity releases the repository: polling stops
        shadowOf(Looper.getMainLooper()).idle()
        val polls = server.calls("GET", "/api/now").size
        Thread.sleep(2_500)
        assertTrue(server.calls("GET", "/api/now").size <= polls + 1)
    }

    /** Runs the main looper until [condition] holds; the container polls on real threads. */
    private fun awaitMain(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met within ${timeoutMs}ms" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
