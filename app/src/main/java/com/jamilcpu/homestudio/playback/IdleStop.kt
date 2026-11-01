package com.jamilcpu.homestudio.playback

import com.jamilcpu.homestudio.data.api.NowPlayingDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How long the Pi may sit idle before the notification service stops itself. */
const val IDLE_STOP_MS = 10 * 60_000L

/** True when nothing is loaded on the Pi (unknown counts as idle; paused does not). */
fun NowPlayingDto?.isIdle(): Boolean = this == null || state == "idle"

/**
 * Calls [onIdle] once [now] has stayed idle for [timeoutMs]. Any playing or paused state restarts
 * the countdown. Runs until cancelled.
 */
suspend fun stopWhenIdle(now: Flow<NowPlayingDto?>, timeoutMs: Long, onIdle: () -> Unit) {
    now.map { it.isIdle() }
        .distinctUntilChanged()
        .collectLatest { idle ->
            if (idle) {
                delay(timeoutMs)
                onIdle()
            }
        }
}
