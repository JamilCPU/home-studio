package com.jamilcpu.homestudio.ui.queue

import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.QueuePlacement
import com.jamilcpu.homestudio.ui.common.ExclusiveRunner
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val QUEUE_POLL_MS = 5_000L
const val NOTICE_ADDED = "Added to the end of the queue"

/**
 * Drives the Queue tab. Polls every [pollIntervalMs] between [start] and [stop] (the screen ties
 * them to the lifecycle). While a row is dragged the order is local; one reorder is sent on drop.
 */
class QueueViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = QUEUE_POLL_MS,
) {
    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private val exclusive = ExclusiveRunner(scope) { busy -> _uiState.update { it.copy(busy = busy) } }

    /** Bumped by every local change, so a poll that started earlier cannot overwrite it. */
    private val epoch = AtomicInteger(0)

    /** The order when the current drag began; a drop that ends there sends nothing. */
    private var dragStartIds: List<Long> = emptyList()

    fun start() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                load()
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh() {
        scope.launch { load() }
    }

    fun dismissMessages() = _uiState.update { it.copy(error = null, notice = null) }

    // ---- Reorder ----

    fun onDragStart(id: Long) {
        val s = _uiState.value
        if (s.busy || s.dragging || s.items.none { it.id == id }) return
        epoch.incrementAndGet()
        dragStartIds = s.items.map { it.id }
        _uiState.update { it.copy(dragging = true) }
    }

    /** Moves the dragged row [delta] places (negative is up); local only until [onDragEnd]. */
    fun moveItem(id: Long, delta: Int) {
        _uiState.update { s ->
            val from = s.items.indexOfFirst { it.id == id }
            val to = from + delta
            if (!s.dragging || from < 0 || to !in s.items.indices) return@update s
            val reordered = s.items.toMutableList().apply { add(to, removeAt(from)) }
            s.copy(items = reordered.toList())
        }
    }

    /** Sends the full new order once; a drop where it started sends nothing. */
    fun onDragEnd() {
        val s = _uiState.value
        if (!s.dragging) return
        val ids = s.items.map { it.id }
        _uiState.update { it.copy(dragging = false) }
        if (ids == dragStartIds) return
        epoch.incrementAndGet()
        val started = exclusive.run {
            when (val r = api.reorder(ids)) {
                is ApiResult.Ok -> {
                    epoch.incrementAndGet()
                    _uiState.update { it.copy(items = r.value.items, unreachable = false) }
                }
                is ApiResult.Failure -> {
                    fail(r)
                    load()
                }
            }
        }
        if (!started) refresh() // something else was running: show the server's order again
    }

    // ---- Remove and clear ----

    /** Removes the row at once and tells the server; a failure restores the server's list. */
    fun remove(id: Long) {
        epoch.incrementAndGet()
        _uiState.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
        scope.launch {
            (api.removeQueueItem(id) as? ApiResult.Failure)?.let { fail(it) }
            load()
        }
    }

    fun clear() {
        exclusive.run {
            (api.clearQueue() as? ApiResult.Failure)?.let { fail(it) }
            load()
        }
    }

    // ---- Search / add ----

    fun openSearch() = _uiState.update { it.copy(search = SearchUiState(), notice = null, error = null) }

    fun closeSearch() = _uiState.update { it.copy(search = null) }

    fun onSearchQuery(text: String) = updateSearch { it.copy(query = text, error = null) }

    /** A pasted YouTube link is added straight away; anything else is searched. */
    fun submitSearch() {
        val search = _uiState.value.search ?: return
        val q = search.query.trim()
        if (q.isEmpty() || search.working) return
        updateSearch { it.copy(working = true, error = null) }
        scope.launch {
            if (isYoutubeLink(q)) {
                enqueue { api.enqueue(url = q, where = QueuePlacement.End) }
            } else {
                when (val r = api.search(q)) {
                    is ApiResult.Ok -> updateSearch { it.copy(working = false, results = r.value.results) }
                    is ApiResult.Failure -> updateSearch { it.copy(working = false, error = r.message) }
                }
            }
        }
    }

    fun addResult(videoId: String) {
        val search = _uiState.value.search ?: return
        if (search.working) return
        updateSearch { it.copy(working = true, error = null) }
        scope.launch { enqueue { api.enqueue(videoId = videoId, where = QueuePlacement.End) } }
    }

    private suspend fun enqueue(call: suspend () -> ApiResult<*>) {
        when (val r = call()) {
            is ApiResult.Ok -> {
                _uiState.update { it.copy(search = null, notice = NOTICE_ADDED) }
                load()
            }
            is ApiResult.Failure -> updateSearch { it.copy(working = false, error = r.message) }
        }
    }

    private fun updateSearch(change: (SearchUiState) -> SearchUiState) =
        _uiState.update { s -> s.search?.let { s.copy(search = change(it)) } ?: s }

    // ---- Plumbing ----

    private suspend fun load() {
        val epochAtStart = epoch.get()
        when (val r = api.queue()) {
            is ApiResult.Ok -> _uiState.update { s ->
                if (s.dragging || epoch.get() != epochAtStart) {
                    s.copy(unreachable = false, loaded = true)
                } else {
                    s.copy(items = r.value.items, remainingS = r.value.remainingS, loaded = true, unreachable = false)
                }
            }
            is ApiResult.Failure -> fail(r)
        }
    }

    private fun fail(failure: ApiResult.Failure) = _uiState.update {
        if (failure.kind == ApiResult.Failure.Kind.Unreachable) {
            it.copy(unreachable = true)
        } else {
            it.copy(error = failure.message)
        }
    }
}
