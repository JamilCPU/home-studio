package com.jamilcpu.homestudio.ui.queue

import com.jamilcpu.homestudio.data.api.HomeStudioApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val QUEUE_POLL_MS = 5_000L

class QueueViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = QUEUE_POLL_MS,
) {
    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    fun start() = Unit
    fun stop() = Unit
    fun refresh() = Unit
    fun onDragStart(id: Long) = Unit
    fun moveItem(id: Long, delta: Int) = Unit
    fun onDragEnd() = Unit
    fun remove(id: Long) = Unit
    fun clear() = Unit
    fun dismissMessages() = Unit
    fun openSearch() = Unit
    fun closeSearch() = Unit
    fun onSearchQuery(text: String) = Unit
    fun submitSearch() = Unit
    fun addResult(videoId: String) = Unit
}
