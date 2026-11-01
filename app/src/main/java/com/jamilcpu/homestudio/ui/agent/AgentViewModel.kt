package com.jamilcpu.homestudio.ui.agent

import com.jamilcpu.homestudio.data.api.AgentPlay
import com.jamilcpu.homestudio.data.api.AgentRequestDto
import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.QueueItemDto
import com.jamilcpu.homestudio.data.api.QueuePlacement
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 2_000L
private const val HISTORY_LIMIT = 20
private const val MAX_POLL_FAILURES = 5

const val NOTICE_GONE = "Those videos aren't in the queue anymore."
const val NOTICE_ALREADY_QUEUED = "Already in the queue"

/**
 * Drives the Agent tab. Polling the active request is bound to [start]/[stop], which the screen
 * ties to the lifecycle; [start] also re-checks the recent list so a request that finished while
 * the app was in the background shows its result.
 */
class AgentViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
) {
    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private val busy = AtomicBoolean(false)

    /** Bumped whenever history changes, so an older whole-list refresh cannot overwrite newer data. */
    private val historyEpoch = AtomicInteger(0)

    fun start() {
        pollJob?.cancel()
        pollJob = scope.launch {
            refreshHistory()
            pollActive()
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onInput(text: String) = _uiState.update { it.copy(input = text) }

    /** Appends dictated text to whatever is already typed. */
    fun appendInput(text: String) = _uiState.update {
        it.copy(input = (it.input.trimEnd() + " " + text.trim()).trim())
    }

    fun chip(text: String) = onInput(text)

    fun setPlayMode(mode: String) = _uiState.update { it.copy(playMode = mode) }

    fun dismissMessages() = _uiState.update { it.copy(notice = null, error = null) }

    /** Puts a request's prompt (and play mode) back in the input box. */
    fun retry(requestId: Long) {
        val request = _uiState.value.history.firstOrNull { it.id == requestId } ?: return
        _uiState.update {
            it.copy(
                input = request.prompt,
                playMode = if (request.play == PLAY_END) PLAY_END else PLAY_NOW,
                error = null,
                notice = null,
            )
        }
    }

    fun send() {
        val s = _uiState.value
        if (!s.canSend) return
        val prompt = s.input.trim()
        val play = if (s.playMode == PLAY_END) AgentPlay.End else AgentPlay.Now
        _uiState.update { it.copy(sending = true, error = null, notice = null) }
        scope.launch {
            when (val result = api.submitAgent(prompt, play)) {
                is ApiResult.Ok -> {
                    val placeholder = AgentRequestDto(
                        id = result.value.id,
                        prompt = prompt,
                        play = play.wire,
                        status = STATUS_PENDING,
                    )
                    historyEpoch.incrementAndGet()
                    _uiState.update { it.withHistory(it.history + placeholder).copy(input = "", sending = false, unreachable = false) }
                    pollJob?.cancel()
                    pollJob = scope.launch { pollActive() }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(sending = false, error = result.message) }
            }
        }
    }

    /**
     * Moves the request's queued videos to the front and makes sure they are what plays: starts
     * playback when idle, and skips whatever else is playing or paused (the label promises playback).
     */
    fun playNow(requestId: Long) {
        val request = _uiState.value.history.firstOrNull { it.id == requestId } ?: return
        runExclusive {
            val mine = request.items.map { it.id }.toSet()
            when (val moved = moveToFront(mine)) {
                is ApiResult.Failure -> return@runExclusive fail(moved)
                is ApiResult.Ok -> if (!moved.value) return@runExclusive notify(NOTICE_GONE)
            }
            when (val now = api.now()) {
                is ApiResult.Ok -> {
                    val current = now.value.item
                    val alreadyPlaying = now.value.state != "idle" && current != null && current.id in mine
                    if (!alreadyPlaying) (api.next() as? ApiResult.Failure)?.let { fail(it) }
                }
                is ApiResult.Failure -> fail(now)
            }
        }
    }

    /** Appends the request's videos that are no longer queued; says so when nothing needed adding. */
    fun addToEnd(requestId: Long) {
        val request = _uiState.value.history.firstOrNull { it.id == requestId } ?: return
        runExclusive {
            val queued = when (val r = api.queue()) {
                is ApiResult.Ok -> r.value.items.map { it.id }.toSet()
                is ApiResult.Failure -> return@runExclusive fail(r)
            }
            var missing = request.items.filter { it.id !in queued }
            if (missing.isNotEmpty()) {
                val playingId = (api.now() as? ApiResult.Ok)?.value?.item?.id
                missing = missing.filter { it.id != playingId }
            }
            if (missing.isEmpty()) return@runExclusive notify(NOTICE_ALREADY_QUEUED)
            enqueueAll(missing)
        }
    }

    /** Runs one queue action at a time; taps while one is running are ignored. */
    private fun runExclusive(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        _uiState.update { it.copy(busy = true, notice = null, error = null) }
        scope.launch {
            try {
                block()
            } finally {
                busy.set(false)
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Puts the request's videos first in the queue. Ok(false) means none of them is queued any more.
     * If the server rejects the order (the queue changed between our read and write) it re-reads once.
     */
    private suspend fun moveToFront(mine: Set<Long>): ApiResult<Boolean> {
        var retried = false
        while (true) {
            val queue = when (val r = api.queue()) {
                is ApiResult.Ok -> r.value.items.map { it.id }
                is ApiResult.Failure -> return r
            }
            val first = queue.filter { it in mine }
            if (first.isEmpty()) return ApiResult.Ok(false)
            when (val r = api.reorder(first + queue.filter { it !in mine })) {
                is ApiResult.Ok -> return ApiResult.Ok(true)
                is ApiResult.Failure -> {
                    if (retried || r.kind == ApiResult.Failure.Kind.Unreachable) return r
                    retried = true
                }
            }
        }
    }

    private suspend fun enqueueAll(items: List<QueueItemDto>) {
        for (item in items) {
            val r = api.enqueue(videoId = item.videoId, where = QueuePlacement.End)
            if (r is ApiResult.Failure) return fail(r)
        }
        notify("Added ${items.size} to the end of the queue")
    }

    private fun notify(message: String) = _uiState.update { it.copy(notice = message) }

    private fun fail(failure: ApiResult.Failure) = _uiState.update {
        if (failure.kind == ApiResult.Failure.Kind.Unreachable) {
            it.copy(unreachable = true, error = null)
        } else {
            it.copy(error = failure.message)
        }
    }

    private suspend fun refreshHistory() {
        val epochAtStart = historyEpoch.get()
        when (val r = api.agentRequests(HISTORY_LIMIT)) {
            is ApiResult.Ok -> if (historyEpoch.get() == epochAtStart) {
                historyEpoch.incrementAndGet()
                _uiState.update { it.withHistory(r.value.requests.reversed()).copy(error = null, unreachable = false) }
            }
            is ApiResult.Failure -> fail(r)
        }
    }

    /** Fetches the newest in-flight request every [pollIntervalMs] until none is left. */
    private suspend fun pollActive() {
        var failures = 0
        while (true) {
            val active = _uiState.value.active ?: return
            when (val r = api.agentRequest(active.id)) {
                is ApiResult.Ok -> {
                    failures = 0
                    historyEpoch.incrementAndGet()
                    _uiState.update { state -> state.withHistory(state.history.replacing(r.value)).copy(unreachable = false) }
                }
                is ApiResult.Failure -> if (++failures >= MAX_POLL_FAILURES) return fail(r)
            }
            if (_uiState.value.active == null) return
            delay(pollIntervalMs)
        }
    }

    private fun List<AgentRequestDto>.replacing(updated: AgentRequestDto): List<AgentRequestDto> =
        if (any { it.id == updated.id }) map { if (it.id == updated.id) updated else it } else this + updated
}
