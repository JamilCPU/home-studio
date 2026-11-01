package com.jamilcpu.homestudio.data

import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import com.jamilcpu.homestudio.data.api.StatusDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Polls the server for what is playing and for station health while it has at least one owner.
 *
 * Shared by the Now Playing screen and the notification service. Timing comes from [scope]'s
 * dispatcher, so tests drive it with a test dispatcher and virtual time.
 */
class NowPlayingRepository(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val nowIntervalMs: Long = NOW_INTERVAL_MS,
    private val statusIntervalMs: Long = STATUS_INTERVAL_MS,
) {
    private val _now = MutableStateFlow<NowPlayingDto?>(null)
    val now: StateFlow<NowPlayingDto?> = _now.asStateFlow()

    private val _status = MutableStateFlow<StatusDto?>(null)
    val status: StateFlow<StatusDto?> = _status.asStateFlow()

    private val _reachable = MutableStateFlow(true)
    val reachable: StateFlow<Boolean> = _reachable.asStateFlow()

    private var job: Job? = null
    private var owners = 0

    /** Bumped whenever a player response is published, so older in-flight polls can be discarded. */
    private val actionEpoch = AtomicInteger(0)

    /** Starts polling if this is the first owner. Every [acquire] must be paired with a [release]. */
    @Synchronized
    fun acquire() {
        owners++
        if (job?.isActive == true) return
        job = scope.launch {
            launch { pollLoop(nowIntervalMs) { pollNow() } }
            launch { pollLoop(statusIntervalMs) { pollStatus() } }
        }
    }

    /** Stops polling when the last owner releases. Extra releases are ignored. */
    @Synchronized
    fun release() {
        if (owners == 0) return
        owners--
        if (owners == 0) {
            job?.cancel()
            job = null
        }
    }

    /** Fetches both now and status immediately (the banner's Retry). */
    fun refresh() {
        scope.launch {
            pollNow()
            pollStatus()
        }
    }

    /** Publishes a player response (toggle, seek, ...) without waiting for the next poll. */
    fun publish(dto: NowPlayingDto) {
        actionEpoch.incrementAndGet()
        apply(dto)
    }

    private fun apply(dto: NowPlayingDto) {
        _now.value = dto
        _reachable.value = true
    }

    private suspend fun pollLoop(intervalMs: Long, poll: suspend () -> Unit) {
        while (currentCoroutineContext().isActive) {
            poll()
            delay(intervalMs)
        }
    }

    private suspend fun pollNow() {
        val epochAtStart = actionEpoch.get()
        when (val result = api.now()) {
            // A player action completed while this poll was in flight; its data may be older.
            is ApiResult.Ok -> if (actionEpoch.get() == epochAtStart) apply(result.value)
            is ApiResult.Failure -> noteFailure(result)
        }
    }

    private suspend fun pollStatus() {
        when (val result = api.status()) {
            is ApiResult.Ok -> {
                _status.value = result.value
                _reachable.value = true
            }
            is ApiResult.Failure -> noteFailure(result)
        }
    }

    private fun noteFailure(failure: ApiResult.Failure) {
        if (failure.kind == ApiResult.Failure.Kind.Unreachable) _reachable.value = false
    }

    private companion object {
        const val NOW_INTERVAL_MS = 1_000L
        const val STATUS_INTERVAL_MS = 15_000L
    }
}
