package com.jamilcpu.homestudio.ui.history

import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import com.jamilcpu.homestudio.data.api.QueuePlacement
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val NOTICE_PLAYING_AGAIN = "Playing again"

/**
 * Drives the History tab: pages of [pageSize] plays, newest first, optionally unrated only.
 * [refresh] starts over from the first page; [loadMore] appends the next one. [onPlayed] gets the
 * player state after [playAgain], so Now Playing updates without a poll.
 */
class HistoryViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pageSize: Int = HISTORY_PAGE,
    private val onPlayed: (NowPlayingDto) -> Unit = {},
) {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** Bumped by every refresh, so a page for an older list (or filter) is dropped. */
    private val generation = AtomicInteger(0)
    private var pageJob: Job? = null

    /** Called when the screen starts: shows the latest plays. */
    fun start() = refresh()

    fun refresh() {
        val gen = generation.incrementAndGet()
        pageJob?.cancel()
        _uiState.update { it.copy(loading = true, loadingMore = false, endReached = false, error = null) }
        pageJob = scope.launch { loadPage(gen, offset = 0) }
        scope.launch { loadUnratedCount(gen) }
    }

    /** Appends the next page; ignored while a page is loading or once the end is reached. */
    fun loadMore() {
        val s = _uiState.value
        if (s.loading || s.loadingMore || s.endReached || !s.loaded) return
        val gen = generation.get()
        _uiState.update { it.copy(loadingMore = true) }
        pageJob = scope.launch { loadPage(gen, offset = s.nextOffset) }
    }

    fun setUnratedOnly(value: Boolean) {
        if (_uiState.value.unratedOnly == value) return
        _uiState.update { it.copy(unratedOnly = value, entries = emptyList(), nextOffset = 0, loaded = false) }
        refresh()
    }

    fun dismissMessages() = _uiState.update { it.copy(error = null, notice = null) }

    /** Plays [entry]'s video now: queues it next, then jumps to that queue item. */
    fun playAgain(entry: HistoryEntryDto) {
        if (_uiState.value.playing) return
        _uiState.update { it.copy(playing = true, error = null, notice = null) }
        scope.launch {
            val played = when (val queued = api.enqueue(videoId = entry.video.videoId, where = QueuePlacement.Next)) {
                is ApiResult.Ok -> api.play(queued.value.id)
                is ApiResult.Failure -> queued
            }
            when (played) {
                is ApiResult.Ok -> {
                    onPlayed(played.value)
                    _uiState.update { it.copy(playing = false, notice = NOTICE_PLAYING_AGAIN) }
                }
                is ApiResult.Failure -> _uiState.update {
                    if (played.kind == ApiResult.Failure.Kind.Unreachable) {
                        it.copy(playing = false, unreachable = true)
                    } else {
                        it.copy(playing = false, error = played.message)
                    }
                }
            }
        }
    }

    private suspend fun loadPage(gen: Int, offset: Int) {
        val unrated = _uiState.value.unratedOnly
        val result = api.history(unratedOnly = unrated, limit = pageSize, offset = offset)
        if (generation.get() != gen) return
        _uiState.update { s ->
            when (result) {
                is ApiResult.Ok -> {
                    val page = result.value.entries
                    val base = if (offset == 0) emptyList() else s.entries
                    s.copy(
                        entries = base.mergedWith(page),
                        // The raw count: duplicates dropped from a shifted page still advance it.
                        nextOffset = offset + page.size,
                        loading = false,
                        loadingMore = false,
                        loaded = true,
                        endReached = page.size < pageSize,
                        unreachable = false,
                        error = null,
                    )
                }
                is ApiResult.Failure -> {
                    val stopped = s.copy(loading = false, loadingMore = false)
                    if (result.kind == ApiResult.Failure.Kind.Unreachable) {
                        stopped.copy(unreachable = true)
                    } else {
                        stopped.copy(error = result.message)
                    }
                }
            }
        }
    }

    private suspend fun loadUnratedCount(gen: Int) {
        val r = api.history(unratedOnly = true, limit = UNRATED_COUNT_CAP, offset = 0)
        if (generation.get() != gen || r !is ApiResult.Ok) return
        _uiState.update { it.copy(unratedCount = r.value.entries.size) }
    }

    /** New plays shift offsets, so a later page may repeat rows already shown. */
    private fun List<HistoryEntryDto>.mergedWith(page: List<HistoryEntryDto>): List<HistoryEntryDto> {
        val seen = mapTo(HashSet()) { it.playId }
        return this + page.filter { seen.add(it.playId) }
    }
}
