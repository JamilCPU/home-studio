package com.jamilcpu.homestudio.playback

import com.jamilcpu.homestudio.data.api.NowPlayingDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        var stops = 0
        backgroundScope.launch { stopWhenIdle(now, IDLE_STOP_MS) { stops++ } }
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
}
