package com.jamilcpu.homestudio.playback

import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import com.jamilcpu.homestudio.queueItemJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdleStopTest {
    private val playing = NowPlayingDto(state = "playing")
    private val paused = NowPlayingDto(state = "paused")
    private val idle = NowPlayingDto(state = "idle")

    @Test
    fun stopsAfterTenMinutesIdle() = runTest {
        assertEquals(10 * 60_000L, IDLE_STOP_MS)
        val now = MutableStateFlow<NowPlayingDto?>(playing)
        val reachable = MutableStateFlow(true)
        var stops = 0
        backgroundScope.launch { stopWhenIdle(idleSignal(now, reachable), IDLE_STOP_MS) { stops++ } }
        runCurrent()

        advanceTimeBy(IDLE_STOP_MS * 3) // playing never times out
        now.value = paused
        advanceTimeBy(IDLE_STOP_MS * 3) // nor does paused
        runCurrent()
        assertEquals(0, stops)

        now.value = idle
        runCurrent()
        advanceTimeBy(IDLE_STOP_MS - 1)
        runCurrent()
        assertEquals(0, stops)

        now.value = playing // playback came back: the countdown starts over
        runCurrent()
        now.value = idle
        runCurrent()
        advanceTimeBy(IDLE_STOP_MS - 1)
        runCurrent()
        assertEquals(0, stops)
        now.value = idle.copy(outputDevice = "Echo") // still idle: the countdown keeps going
        runCurrent()
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, stops)
    }

    @Test
    fun briefOutageDoesNotStop() = runTest {
        val now = MutableStateFlow<NowPlayingDto?>(playing)
        val reachable = MutableStateFlow(true)
        var stops = 0
        backgroundScope.launch { stopWhenIdle(idleSignal(now, reachable), IDLE_STOP_MS) { stops++ } }
        runCurrent()
        reachable.value = false
        runCurrent()
        advanceTimeBy(IDLE_STOP_MS - 1)
        reachable.value = true // the Pi answered again in time
        runCurrent()
        advanceTimeBy(IDLE_STOP_MS * 2)
        runCurrent()
        assertEquals(0, stops)
    }

    @Test
    fun unreachablePiForTenMinutesStopsTheService() = runTest {
        val server = FakeServer().apply {
            ok("GET", "/api/now") {
                """{"state":"playing","item":${queueItemJson(1)},"position_s":10.0,"duration_s":600.0,"output_device":null}"""
            }
            ok("GET", "/api/status") { """{"player":"playing","bluetooth":"connected","cookies":"ok","claude":"ok"}""" }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        // A slow poll keeps the number of real requests small over ten virtual minutes.
        val repo = NowPlayingRepository(server.api(), scope, nowIntervalMs = 60_000L, statusIntervalMs = 60_000L)
        var stops = 0
        scope.launch { stopWhenIdle(repo.idleSignal(), IDLE_STOP_MS) { stops++ } }
        repo.acquire()
        awaitUntil { repo.now.value?.state == "playing" }

        server.down = true
        advanceTimeBy(60_001L)
        awaitUntil { !repo.reachable.value }
        assertEquals("playing", repo.now.value?.state) // the last answer stays, but is stale
        advanceTimeBy(IDLE_STOP_MS - 2_000L)
        runCurrent()
        assertEquals(0, stops)
        advanceTimeBy(2_000L)
        awaitUntil { stops == 1 }
        assertFalse(repo.reachable.value)

        repo.release()
        scope.cancel()
    }
}
