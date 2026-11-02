package com.jamilcpu.homestudio.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jamilcpu.homestudio.data.api.QueueItemDto
import com.jamilcpu.homestudio.ui.now.formatClock

/** One queue row: ▶ plays it now; swipe either way to remove; long-press the handle and drag to reorder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueRow(item: QueueItemDto, items: List<QueueItemDto>, enabled: Boolean, actions: QueueActions) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val remove = value != SwipeToDismissBoxValue.Settled
            if (remove) actions.onRemove(item.id)
            remove
        },
    )
    val drag = remember { RowDrag() }
    SwipeToDismissBox(
        state = dismiss,
        backgroundContent = { RemoveBackground() },
        modifier = Modifier
            .zIndex(if (drag.active) 1f else 0f)
            .graphicsLayer { translationY = drag.offset }
            .onSizeChanged { drag.heightPx = it.height }
            .testTag("queueRow_${item.id}"),
    ) {
        ListItem(
            leadingContent = {
                IconButton(
                    onClick = { actions.onPlay(item.id) },
                    enabled = enabled,
                    modifier = Modifier.testTag("playQueue_${item.id}"),
                ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play now") }
            },
            overlineContent = { Text(sourceLabel(item.source)) },
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = details(item)?.let { text -> { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingContent = { DragHandle(item.id, items, enabled, drag, actions) },
        )
    }
}

/** Per-row drag state: how far the finger has moved since the row last swapped places. */
private class RowDrag {
    var active by mutableStateOf(false)
    var offset by mutableFloatStateOf(0f)
    var heightPx by mutableIntStateOf(0)

    fun reset() {
        active = false
        offset = 0f
    }
}

@Composable
private fun DragHandle(id: Long, items: List<QueueItemDto>, enabled: Boolean, drag: RowDrag, actions: QueueActions) {
    val current by rememberUpdatedState(items)
    val onMove by rememberUpdatedState(actions.onMove)
    val onEnd by rememberUpdatedState(actions.onDragEnd)
    Icon(
        Icons.Filled.Menu,
        contentDescription = "Drag to reorder",
        modifier = Modifier
            .testTag("dragHandle_$id")
            .pointerInput(id, enabled) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        drag.active = true
                        actions.onDragStart(id)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.offset += amount.y
                        val h = drag.heightPx.toFloat()
                        val index = current.indexOfFirst { it.id == id }
                        if (h <= 0f || index < 0) return@detectDragGesturesAfterLongPress
                        if (drag.offset > h / 2 && index < current.lastIndex) {
                            onMove(id, +1)
                            drag.offset -= h
                        } else if (drag.offset < -h / 2 && index > 0) {
                            onMove(id, -1)
                            drag.offset += h
                        }
                    },
                    onDragEnd = {
                        drag.reset()
                        onEnd()
                    },
                    onDragCancel = {
                        drag.reset()
                        onEnd()
                    },
                )
            },
    )
}

@Composable
private fun RemoveBackground() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

private fun details(item: QueueItemDto): String? =
    listOfNotNull(item.channel, item.durationS?.let { formatClock(it.toDouble()) })
        .joinToString(" · ")
        .ifEmpty { null }
