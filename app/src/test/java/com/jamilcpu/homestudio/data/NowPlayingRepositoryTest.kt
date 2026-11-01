package com.jamilcpu.homestudio.data

import com.jamilcpu.homestudio.data.api.HomeStudioApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.net.ConnectException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingRepositoryTest {
    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val nowBody =
        """{"ok":true,"data":{"state":"playing","item":null,"position_s":10.0,"duration_s":null,"output_device":"Echo"},"error":null}"""
    private val statusBody =
        """{"ok":true,"data":{"player":"playing","bluetooth":"connected","cookies":"ok","claude":"ok"},"error":null}"""

    private class Rig(val engine: MockEngine, val repo: NowPlayingRepository) {
        fun count(path: String) = engine.requestHistory.count { it.url.encodedPath == path }
    }

    private fun TestScope.rig(down: () -> Boolean = { false }): Rig {
        val engine = MockEngine { request ->
            if (down()) throw ConnectException("refused")
            val body = if (request.url.encodedPath == "/api/now") nowBody else statusBody
            respond(body, headers = json)
        }
        val api = HomeStudioApi({ "http://pi.test:8765" }, engine)
        val repo = NowPlayingRepository(api, CoroutineScope(StandardTestDispatcher(testScheduler)))
        return Rig(engine, repo)
    }

    @Test
    fun pollsNowEverySecondAndStatusEvery15s() = runTest {
        val r = rig()
        r.repo.start()
        runCurrent()
        assertEquals(1, r.count("/api/now"))
        assertEquals(1, r.count("/api/status"))
        advanceTimeBy(3_100)
        assertEquals(4, r.count("/api/now"))
        assertEquals(1, r.count("/api/status"))
        advanceTimeBy(12_000)
        assertEquals(2, r.count("/api/status"))
        assertNotNull(r.repo.now.value)
        assertNotNull(r.repo.status.value)
        r.repo.stop()
    }

    @Test
    fun startIsIdempotentAndStopCancels() = runTest {
        val r = rig()
        r.repo.start()
        r.repo.start()
        runCurrent()
        assertEquals(1, r.count("/api/now"))
        r.repo.stop()
        r.repo.stop()
        val before = r.engine.requestHistory.size
        advanceTimeBy(5_000)
        assertEquals(before, r.engine.requestHistory.size)
    }

    @Test
    fun reachableFollowsFailuresAndRecovers() = runTest {
        var down = false
        val r = rig { down }
        r.repo.start()
        runCurrent()
        assertTrue(r.repo.reachable.value)
        down = true
        advanceTimeBy(1_000)
        assertFalse(r.repo.reachable.value)
        down = false
        advanceTimeBy(1_000)
        assertTrue(r.repo.reachable.value)
        r.repo.stop()
    }
}
