package com.jamilcpu.homestudio.ui.now

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingLifecycleTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val nowRequests = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private fun api(): HomeStudioApi {
        val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath == "/api/now") {
                nowRequests.incrementAndGet()
                """{"ok":true,"data":{"state":"idle","item":null,"position_s":0.0,"duration_s":null,"output_device":null},"error":null}"""
            } else {
                """{"ok":true,"data":{"player":"idle","bluetooth":"connected","cookies":"ok","claude":"ok"},"error":null}"""
            }
            respond(body, headers = json)
        }
        return HomeStudioApi({ "http://pi.test:8765" }, engine)
    }

    @Test
    fun pollingStopsWhenStoppedAndResumesWhenStarted() {
        val api = api()
        val repo = NowPlayingRepository(api, scope, nowIntervalMs = 20, statusIntervalMs = 1_000)
        rule.setContent { HomeStudioTheme { NowPlayingRoute(repo, api, onRate = {}, onAskAgent = {}) } }

        rule.waitUntil(5_000) { nowRequests.get() >= 2 }

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(200) // let any in-flight request finish
        val frozen = nowRequests.get()
        Thread.sleep(400)
        assertEquals(frozen, nowRequests.get())

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.waitUntil(5_000) { nowRequests.get() > frozen }
    }
}
