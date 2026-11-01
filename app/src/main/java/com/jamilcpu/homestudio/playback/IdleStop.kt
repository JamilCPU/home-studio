package com.jamilcpu.homestudio.playback

import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** How long the Pi may sit idle (or out of reach) before the notification service stops itself. */
const val IDLE_STOP_MS = 10 * 60_000L

/** True when nothing is loaded on the Pi (unknown counts as idle; paused does not). */
fun NowPlayingDto?.isIdle(): Boolean = this == null || state == "idle"

/**
 * Idle for the service's purposes: nothing loaded, or the Pi can't be reached (its last answer
 * goes stale, so an unreachable Pi must not keep the service alive forever).
 */
fun idleSignal(now: Flow<NowPlayingDto?>, reachable: Flow<Boolean>): Flow<Boolean> =
    combine(now, reachable) { n, r -> !r || n.isIdle() }

fun NowPlayingRepository.idleSignal(): Flow<Boolean> = idleSignal(now, reachable)

/**
 * Calls [onIdle] once [idle] has stayed true for [timeoutMs]. Any false (playing, paused, or the
 * Pi answering again) restarts the countdown. Runs until cancelled.
 */
suspend fun stopWhenIdle(idle: Flow<Boolean>, timeoutMs: Long, onIdle: () -> Unit) {
    idle.distinctUntilChanged()
        .collectLatest { isIdle ->
            if (isIdle) {
                delay(timeoutMs)
                onIdle()
            }
        }
}
