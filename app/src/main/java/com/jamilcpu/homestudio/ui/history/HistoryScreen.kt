package com.jamilcpu.homestudio.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import com.jamilcpu.homestudio.ui.common.UnreachableBanner
import com.jamilcpu.homestudio.ui.rate.RateSheetController
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Rows from the end at which the next page is requested. */
private const val LOAD_MORE_THRESHOLD = 5

data class HistoryActions(
    val onRetry: () -> Unit = {},
    val onUnratedOnly: (Boolean) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpen: (HistoryEntryDto) -> Unit = {},
)

/** Stateful entry used by the nav graph: refreshes on start; a row opens the shell's Rate sheet. */
@Composable
fun HistoryRoute(vm: HistoryViewModel, rateSheet: RateSheetController) {
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { }
    }
    val state by vm.uiState.collectAsState()
    val actions = remember(vm, rateSheet) {
        HistoryActions(
            onRetry = vm::refresh,
            onUnratedOnly = vm::setUnratedOnly,
            onLoadMore = vm::loadMore,
            onOpen = { entry -> rateSheet.open(entry.video.videoId, entry.video.title, entry.rating) },
        )
    }
    HistoryScreen(state, actions)
}

/** Stateless History tab: plays grouped by local day, each showing its rating or "Rate?". */
@Composable
fun HistoryScreen(state: HistoryUiState, actions: HistoryActions) {
    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(state.entries) { groupByDay(state.entries, zone, LocalDate.now(zone)) }
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, actions.onLoadMore)
    Column(Modifier.fillMaxSize().testTag("screen_history")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.unreachable) UnreachableBanner(actions.onRetry)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("historyError")) }
            Filters(state, actions.onUnratedOnly)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("historyList"), state = listState) {
            groups.forEach { group ->
                item(key = "day_${group.entries.first().playId}") { DayHeader(group.label) }
                items(group.entries, key = { it.playId }) { entry -> HistoryRow(entry) { actions.onOpen(entry) } }
            }
            if (state.loadingMore || (state.loading && state.entries.isEmpty())) {
                item(key = "loading") { Loading() }
            }
            if (state.loaded && state.entries.isEmpty()) {
                item(key = "empty") { Text("Nothing played yet", Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun LoadMoreWhenNearEnd(listState: LazyListState, onLoadMore: () -> Unit) {
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }
}

@Composable
private fun Filters(state: HistoryUiState, onUnratedOnly: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !state.unratedOnly,
            onClick = { onUnratedOnly(false) },
            label = { Text("All") },
            modifier = Modifier.testTag("filterAll"),
        )
        FilterChip(
            selected = state.unratedOnly,
            onClick = { onUnratedOnly(true) },
            label = { Text(unratedChipLabel(state.unratedCount)) },
            modifier = Modifier.testTag("filterUnrated"),
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun HistoryRow(entry: HistoryEntryDto, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).testTag("historyRow_${entry.playId}"),
        headlineContent = { Text(entry.video.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = entry.video.channel?.let { channel -> { Text(channel, maxLines = 1) } },
        trailingContent = { Text(rowLabel(entry), style = MaterialTheme.typography.labelLarge) },
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
