package com.jamilcpu.homestudio.ui.history

import androidx.compose.runtime.Composable
import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import com.jamilcpu.homestudio.ui.rate.RateSheetController

data class HistoryActions(
    val onRetry: () -> Unit = {},
    val onUnratedOnly: (Boolean) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpen: (HistoryEntryDto) -> Unit = {},
)

@Composable
fun HistoryRoute(vm: HistoryViewModel, rateSheet: RateSheetController) = Unit

@Composable
fun HistoryScreen(state: HistoryUiState, actions: HistoryActions) = Unit
