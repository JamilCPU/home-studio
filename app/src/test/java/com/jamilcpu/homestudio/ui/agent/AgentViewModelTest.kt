package com.jamilcpu.homestudio.ui.agent

import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.settle
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.net.ConnectException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModelTest {
    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun qi(id: Long, title: String = "V$id") =
        """{"id":$id,"video_id":"vid$id","title":"$title","channel":"C","thumbnail_url":null,"duration_s":600,"source":"agent","reason":null,"chapters":[]}"""

    private fun req(
        id: Long,
        status: String,
        prompt: String = "p$id",
        kind: String = "user",
        error: String? = null,
        items: List<Long> = emptyList(),
    ) = """{"id":$id,"prompt":"$prompt","kind":"$kind","play":"now","status":"$status","summary":"why $id","error":${error?.let { "\"$it\"" }},"items":[${items.joinToString(",") { qi(it) }}],"created_at":"2026-11-01T14:00:00+00:00","finished_at":null}"""

    private fun env(data: String) = """{"ok":true,"data":$data,"error":null}"""

    /** Scripted server state, mutated by the tests. */
    private var list: List<String> = emptyList()
    private var byId: MutableMap<Long, () -> String> = mutableMapOf()
    private var queueIds: List<Long> = emptyList()
    private var nowState = "playing"
    private var nowItemId = 0L
    private var reorderFailures = 0
    private var down = false
    private var queueGate: CompletableDeferred<Unit>? = null

    private class Rig(val engine: MockEngine, val vm: AgentViewModel) {
        fun calls(method: String, path: String): List<HttpRequestData> =
            engine.requestHistory.filter { it.method.value == method && it.url.encodedPath == path }
    }

    private fun HttpRequestData.bodyText() = (body as TextContent).text

    private fun TestScope.rig(): Rig {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method.value
            if (down) throw ConnectException("refused")
            if (path == "/api/queue" && method == "GET") queueGate?.await()
            if (path == "/api/queue/order" && reorderFailures > 0) {
                reorderFailures--
                return@MockEngine respond(
                    """{"ok":false,"data":null,"error":"ids do not match"}""",
                    HttpStatusCode.UnprocessableEntity,
                    json,
                )
            }
            val body = when {
                method == "POST" && path == "/api/agent/requests" -> env("""{"id":9}""")
                path == "/api/agent/requests" -> env("""{"requests":[${list.joinToString(",")}]}""")
                path.startsWith("/api/agent/requests/") ->
                    env(byId.getValue(path.substringAfterLast('/').toLong())())
                path == "/api/queue" && method == "GET" ->
                    env("""{"items":[${queueIds.joinToString(",") { qi(it) }}],"remaining_s":0}""")
                path == "/api/queue" && method == "POST" -> env(qi(99))
                path == "/api/queue/order" -> env("""{"items":[]}""")
                else -> env(
                    """{"state":"$nowState","item":${if (nowItemId > 0) qi(nowItemId) else "null"},"position_s":0.0,"duration_s":null,"output_device":null}""",
                )
            }
            respond(body, headers = json)
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return Rig(engine, AgentViewModel(HomeStudioApi({ "http://pi.test:8765" }, engine), scope))
    }

    @Test
    fun sendPostsAndPollsUntilDone() = runTest {
        var polls = 0
        byId[9] = { if (++polls < 3) req(9, "running", "a podcast") else req(9, "done", "a podcast", items = listOf(1, 2)) }
        val r = rig()
        r.vm.start()
        awaitUntil { r.calls("GET", "/api/agent/requests").isNotEmpty() }
        r.vm.onInput("a podcast")
        r.vm.send()
        awaitUntil { r.calls("POST", "/api/agent/requests").isNotEmpty() }
        assertEquals("""{"prompt":"a podcast","play":"now"}""", r.calls("POST", "/api/agent/requests").single().bodyText())
        awaitUntil { r.vm.uiState.value.active?.id == 9L }
        assertEquals("", r.vm.uiState.value.input)
        repeat(4) {
            advanceTimeBy(2_000)
            settle()
        }
        awaitUntil { r.vm.uiState.value.active == null }
        val last = r.vm.uiState.value.history.last()
        assertEquals("done", last.status)
        assertEquals(2, last.items.size)
        assertEquals(3, polls)
        advanceTimeBy(10_000)
        settle()
        assertEquals(3, polls) // stopped polling once done
    }

    @Test
    fun sendUsesChosenPlayMode() = runTest {
        byId[9] = { req(9, "done") }
        val r = rig()
        r.vm.onInput("x")
        r.vm.setPlayMode("end")
        r.vm.send()
        awaitUntil { r.calls("POST", "/api/agent/requests").isNotEmpty() }
        assertEquals("""{"prompt":"x","play":"end"}""", r.calls("POST", "/api/agent/requests").single().bodyText())
    }

    @Test
    fun resumesRunningRequestOnReopen() = runTest {
        list = listOf(req(4, "running"), req(3, "done", items = listOf(1)))
        var done = false
        byId[4] = { if (done) req(4, "done", items = listOf(5)) else req(4, "running") }
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.active?.id == 4L }
        assertEquals(listOf(3L, 4L), r.vm.uiState.value.history.map { it.id }) // oldest first
        done = true
        advanceTimeBy(2_000)
        awaitUntil { r.vm.uiState.value.active == null }
        assertEquals("done", r.vm.uiState.value.history.last().status)
    }

    @Test
    fun backgroundingStopsPollingAndReturnRechecksTheList() = runTest {
        list = listOf(req(4, "running"))
        var polls = 0
        byId[4] = { polls++; req(4, "running") }
        val r = rig()
        r.vm.start()
        awaitUntil { polls >= 1 }
        r.vm.stop()
        settle()
        val frozen = polls
        advanceTimeBy(10_000)
        settle()
        assertEquals(frozen, polls)
        list = listOf(req(4, "done", items = listOf(7)))
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.single().status == "done" }
        assertNull(r.vm.uiState.value.active)
    }

    @Test
    fun showsFailureError() = runTest {
        list = listOf(req(6, "failed", error = "rate limited"))
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        val failed = r.vm.uiState.value.history.single()
        assertEquals("failed", failed.status)
        assertEquals("rate limited", failed.error)
        assertNull(r.vm.uiState.value.active)
    }

    @Test
    fun tryAgainPrefillsTheSamePrompt() = runTest {
        list = listOf(req(6, "failed", prompt = "classical playlist", error = "boom"))
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.retry(6)
        assertEquals("classical playlist", r.vm.uiState.value.input)
    }

    @Test
    fun emptyInputCannotSend() = runTest {
        val r = rig()
        r.vm.onInput("   ")
        r.vm.send()
        settle()
        assertTrue(r.calls("POST", "/api/agent/requests").isEmpty())
        assertTrue(!r.vm.uiState.value.canSend)
    }

    @Test
    fun chipPrefillsInput() = runTest {
        val r = rig()
        r.vm.chip("Podcast on ")
        assertEquals("Podcast on ", r.vm.uiState.value.input)
    }

    @Test
    fun playNowMovesRequestItemsFirstThenStartsWhenIdle() = runTest {
        list = listOf(req(8, "done", items = listOf(12, 13)))
        queueIds = listOf(10, 11, 12, 13)
        nowState = "idle"
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.playNow(8)
        awaitUntil { r.calls("POST", "/api/player/next").isNotEmpty() }
        assertEquals("""{"ids":[12,13,10,11]}""", r.calls("PUT", "/api/queue/order").single().bodyText())
    }

    @Test
    fun playNowSkipsAnotherTrackThatIsPlaying() = runTest {
        list = listOf(req(8, "done", items = listOf(11)))
        queueIds = listOf(10, 11)
        nowItemId = 50
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.playNow(8)
        awaitUntil { r.calls("POST", "/api/player/next").isNotEmpty() }
        assertEquals("""{"ids":[11,10]}""", r.calls("PUT", "/api/queue/order").single().bodyText())
    }

    @Test
    fun playNowLeavesPlaybackAloneWhenCurrentTrackIsTheRequests() = runTest {
        list = listOf(req(8, "done", items = listOf(11)))
        queueIds = listOf(10, 11)
        nowItemId = 11
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.playNow(8)
        awaitUntil { r.calls("PUT", "/api/queue/order").isNotEmpty() && !r.vm.uiState.value.busy }
        assertTrue(r.calls("POST", "/api/player/next").isEmpty())
    }

    @Test
    fun playNowRetriesOnceWhenTheOrderIsRejected() = runTest {
        list = listOf(req(8, "done", items = listOf(11)))
        queueIds = listOf(10, 11)
        nowState = "idle"
        reorderFailures = 1
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.playNow(8)
        awaitUntil { r.calls("POST", "/api/player/next").isNotEmpty() }
        assertEquals(2, r.calls("PUT", "/api/queue/order").size)
        assertEquals(2, r.calls("GET", "/api/queue").size)
        assertNull(r.vm.uiState.value.error)
    }

    @Test
    fun playNowShowsErrorWhenTheRetryAlsoFails() = runTest {
        list = listOf(req(8, "done", items = listOf(11)))
        queueIds = listOf(10, 11)
        reorderFailures = 5
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.playNow(8)
        awaitUntil { r.vm.uiState.value.error != null }
        assertEquals(2, r.calls("PUT", "/api/queue/order").size)
        assertTrue(r.calls("POST", "/api/player/next").isEmpty())
    }

    @Test
    fun doubleTapRunsOneSequence() = runTest {
        list = listOf(req(8, "done", items = listOf(11)))
        queueIds = listOf(10, 11)
        nowState = "idle"
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        val gate = CompletableDeferred<Unit>()
        queueGate = gate
        r.vm.playNow(8)
        awaitUntil { r.vm.uiState.value.busy }
        r.vm.playNow(8)
        r.vm.addToEnd(8)
        gate.complete(Unit)
        awaitUntil { r.calls("POST", "/api/player/next").isNotEmpty() && !r.vm.uiState.value.busy }
        settle()
        assertEquals(1, r.calls("PUT", "/api/queue/order").size)
        assertEquals(1, r.calls("GET", "/api/queue").size)
        assertEquals(1, r.calls("POST", "/api/player/next").size)
    }

    @Test
    fun unreachableRefreshShowsBannerAndRetryRecovers() = runTest {
        list = listOf(req(4, "running"))
        var done = false
        byId[4] = { if (done) req(4, "done", items = listOf(5)) else req(4, "running") }
        down = true
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.unreachable }
        assertNull(r.vm.uiState.value.error)
        assertTrue(r.vm.uiState.value.history.isEmpty())
        down = false
        done = true
        r.vm.start() // what the banner's Retry calls
        awaitUntil { r.vm.uiState.value.history.singleOrNull()?.status == "done" }
        assertTrue(!r.vm.uiState.value.unreachable)
        assertNull(r.vm.uiState.value.active)
    }

    @Test
    fun retryResumesPollingAfterPollFailuresGaveUp() = runTest {
        list = listOf(req(4, "running"))
        var done = false
        byId[4] = { if (done) req(4, "done") else req(4, "running") }
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.active?.id == 4L }
        down = true
        repeat(6) {
            advanceTimeBy(2_000)
            settle()
        }
        awaitUntil { r.vm.uiState.value.unreachable }
        down = false
        done = true
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.single().status == "done" }
    }

    @Test
    fun playNowWhenItemsGoneSaysSo() = runTest {
        list = listOf(req(8, "done", items = listOf(12)))
        queueIds = listOf(10, 11)
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.playNow(8)
        awaitUntil { r.vm.uiState.value.notice != null }
        assertEquals("Those videos aren't in the queue anymore.", r.vm.uiState.value.notice)
        assertTrue(r.calls("PUT", "/api/queue/order").isEmpty())
    }

    @Test
    fun addToEndIsANoOpWhenAlreadyQueued() = runTest {
        list = listOf(req(8, "done", items = listOf(12)))
        queueIds = listOf(10, 12)
        val r = rig()
        r.vm.start()
        awaitUntil { r.vm.uiState.value.history.isNotEmpty() }
        r.vm.addToEnd(8)
        awaitUntil { r.vm.uiState.value.notice != null }
        assertEquals("Already in the queue", r.vm.uiState.value.notice)
        assertTrue(r.calls("POST", "/api/queue").isEmpty())
    }
}
