package com.jamilcpu.homestudio.ui.queue

import androidx.compose.runtime.Composable

data class QueueActions(
    val onRetry: () -> Unit = {},
    val onDragStart: (Long) -> Unit = {},
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onDragEnd: () -> Unit = {},
    val onRemove: (Long) -> Unit = {},
    val onClear: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onOpenSearch: () -> Unit = {},
    val onCloseSearch: () -> Unit = {},
    val onSearchQuery: (String) -> Unit = {},
    val onSubmitSearch: () -> Unit = {},
    val onAddResult: (String) -> Unit = {},
)

@Composable
fun QueueRoute(vm: QueueViewModel) = Unit

@Composable
fun QueueScreen(state: QueueUiState, actions: QueueActions) = Unit
