package com.jamilcpu.homestudio.ui.history

import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the History tab: pages of [pageSize] plays, newest first, optionally unrated only.
 * [refresh] starts over from the first page; [loadMore] appends the next one.
 */
class HistoryViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pageSize: Int = HISTORY_PAGE,
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
