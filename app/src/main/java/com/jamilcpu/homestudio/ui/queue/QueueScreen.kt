package com.jamilcpu.homestudio.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jamilcpu.homestudio.ui.common.UnreachableBanner
import com.jamilcpu.homestudio.ui.now.NOTICE_QUEUE_EMPTY

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

/** Stateful entry used by the nav graph: polls while the screen is started. */
@Composable
fun QueueRoute(vm: QueueViewModel) {
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { vm.stop() }
    }
    val state by vm.uiState.collectAsState()
    val actions = remember(vm) {
        QueueActions(
            onRetry = vm::refresh,
            onDragStart = vm::onDragStart,
            onMove = vm::moveItem,
            onDragEnd = vm::onDragEnd,
            onRemove = vm::remove,
            onClear = vm::clear,
            onDismiss = vm::dismissMessages,
            onOpenSearch = vm::openSearch,
            onCloseSearch = vm::closeSearch,
            onSearchQuery = vm::onSearchQuery,
            onSubmitSearch = vm::submitSearch,
            onAddResult = vm::addResult,
        )
    }
    QueueScreen(state, actions)
}

/** Stateless Queue tab: what plays next, with reorder, swipe-to-remove, search/add and clear. */
@Composable
fun QueueScreen(state: QueueUiState, actions: QueueActions) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("screen_queue")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.unreachable) UnreachableBanner(actions.onRetry)
            Messages(state, actions.onDismiss)
            Header(state, actions.onOpenSearch) { confirmClear = true }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("queueList")) {
            items(state.items, key = { it.id }) { item ->
                QueueRow(item, state.items, enabled = !state.busy, actions)
            }
        }
    }
    if (confirmClear) {
        ClearDialog(
            onConfirm = {
                confirmClear = false
                actions.onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
    state.search?.let { SearchDialog(it, actions) }
}

@Composable
private fun Header(state: QueueUiState, onSearch: () -> Unit, onClear: () -> Unit) {
    when {
        state.items.isNotEmpty() -> Text(endsInLabel(state.remainingS), style = MaterialTheme.typography.bodyMedium)
        state.loaded -> Text(NOTICE_QUEUE_EMPTY, style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onSearch, modifier = Modifier.testTag("openSearch")) { Text("🔍 Search / add") }
        TextButton(
            onClick = onClear,
            enabled = state.items.isNotEmpty() && !state.busy,
            modifier = Modifier.testTag("clearQueue"),
        ) { Text("Clear") }
    }
}

@Composable
private fun Messages(state: QueueUiState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice ?: return
    val isError = state.error != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            message,
            Modifier.weight(1f).testTag(if (isError) "queueError" else "queueNotice"),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
private fun ClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("Clear the whole queue?") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirmClear")) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
