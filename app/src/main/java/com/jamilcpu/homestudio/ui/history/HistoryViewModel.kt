package com.jamilcpu.homestudio.ui.history

import com.jamilcpu.homestudio.data.api.HomeStudioApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pageSize: Int = HISTORY_PAGE,
) {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun start() = Unit
    fun refresh() = Unit
    fun loadMore() = Unit
    fun setUnratedOnly(value: Boolean) = Unit
}
