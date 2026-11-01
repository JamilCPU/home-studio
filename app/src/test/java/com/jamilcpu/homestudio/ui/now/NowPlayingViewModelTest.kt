package com.jamilcpu.homestudio.ui.now

import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingViewModelTest {
    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val item =
        """{"id":1,"video_id":"abc123","title":"T","channel":"C","thumbnail_url":null,"duration_s":1000,"source":"user","reason":"why","chapters":[{"title":"Intro","start_s":0.0},{"title":"Part 2","start_s":300.0}]}"""

    private fun nowBody(pos: Double) =
        """{"ok":true,"data":{"state":"playing","item":$item,"position_s":$pos,"duration_s":1000.0,"output_device":"Echo"},"error":null}"""

    private val statusBody =
        """{"ok":true,"data":{"player":"playing","bluetooth":"connected","cookies":"ok","claude":"ok"},"error":null}"""

    private class Rig(val engine: MockEngine, val vm: NowPlayingViewModel, val repo: NowPlayingRepository) {
        fun posts(path: String): List<HttpRequestData> =
            engine.requestHistory.filter { it.method.value == "POST" && it.url.encodedPath == path }
    }

    private fun HttpRequestData.bodyText() = (body as TextContent).text

    private fun TestScope.rig(position: () -> Double = { 451.7 }): Rig {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val body = when (path) {
                "/api/status" -> statusBody
                "/api/ratings" -> """{"ok":true,"data":{"id":1},"error":null}"""
                else -> nowBody(position())
            }
            respond(body, headers = json)
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val api = HomeStudioApi({ "http://pi.test:8765" }, engine)
        val repo = NowPlayingRepository(api, scope)
        val vm = NowPlayingViewModel(repo, api, scope)
        return Rig(engine, vm, repo)
    }

    @Test
    fun dragDoesNotSnapBackAndSeeksOnce() = runTest {
        var pos = 100.0
        val r = rig { pos }
        r.repo.start()
        runCurrent()
        assertEquals(100.0, r.vm.uiState.value.positionS, 0.001)
        r.vm.onDragStart()
        r.vm.onDrag(400f)
        pos = 105.0
        advanceTimeBy(3_000)
        assertEquals(400f, r.vm.uiState.value.dragPositionS)
        assertEquals(400.0, r.vm.uiState.value.displayPositionS, 0.001)
        assertEquals(0, r.posts("/api/player/seek").size)
        r.vm.onDragEnd()
        r.vm.onDragEnd()
        runCurrent()
        assertEquals(1, r.posts("/api/player/seek").size)
        assertEquals("""{"position_s":400.0}""", r.posts("/api/player/seek").single().bodyText())
        assertNull(r.vm.uiState.value.dragPositionS)
        r.repo.stop()
    }

    @Test
    fun skipButtonsSendMinus15Plus30() = runTest {
        val r = rig()
        r.vm.skipBack()
        r.vm.skipForward()
        runCurrent()
        val bodies = r.posts("/api/player/skip").map { it.bodyText() }
        assertEquals(listOf("""{"delta_s":-15.0}""", """{"delta_s":30.0}"""), bodies)
    }

    @Test
    fun transportButtonsHitTheirEndpoints() = runTest {
        val r = rig()
        r.vm.toggle()
        r.vm.next()
        r.vm.previous()
        r.vm.chapterTapped(300.0)
        runCurrent()
        assertEquals(1, r.posts("/api/player/toggle").size)
        assertEquals(1, r.posts("/api/player/next").size)
        assertEquals(1, r.posts("/api/player/previous").size)
        assertEquals("""{"position_s":300.0}""", r.posts("/api/player/seek").single().bodyText())
    }

    @Test
    fun openInYoutubeUriHasTimestamp() = runTest {
        val r = rig { 451.7 }
        r.repo.start()
        runCurrent()
        assertEquals("https://www.youtube.com/watch?v=abc123&t=451s", r.vm.openInYoutubeUri().toString())
        r.repo.stop()
    }

    @Test
    fun quickRateSendsLikedOnly() = runTest {
        val r = rig()
        r.repo.start()
        runCurrent()
        r.vm.quickRate(true)
        runCurrent()
        assertEquals("""{"video_id":"abc123","liked":true}""", r.posts("/api/ratings").single().bodyText())
        assertEquals(true, r.vm.uiState.value.quickRating)
        r.repo.stop()
    }

    @Test
    fun currentChapterFollowsPosition() = runTest {
        val r = rig { 451.7 }
        r.repo.start()
        runCurrent()
        assertEquals("Part 2", r.vm.uiState.value.currentChapter?.title)
        assertNotNull(r.vm.uiState.value.item)
        r.repo.stop()
    }

    @Test
    fun formatsClock() {
        assertEquals("0:07", formatClock(7.9))
        assertEquals("7:31", formatClock(451.7))
        assertEquals("1:01:01", formatClock(3661.0))
        assertEquals("−1:00 left", remainingLabel(0.0, 60.0))
        assertEquals("7:31 elapsed", elapsedLabel(451.7))
    }
}
