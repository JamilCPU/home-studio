package com.jamilcpu.homestudio.ui.now

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jamilcpu.homestudio.data.api.ChapterDto

private val TRACK_INSET = 10.dp
private val TICK_HEIGHT = 14.dp
private val BAR_HEIGHT = 40.dp

/** Slider with chapter tick marks drawn over it. The ticks never take touches. */
@Composable
fun SeekBar(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    val duration = state.durationS?.takeIf { it > 0.0 }
    val max = (duration ?: 1.0).toFloat()
    Box(modifier.fillMaxWidth().height(BAR_HEIGHT)) {
        Slider(
            value = state.displayPositionS.toFloat().coerceIn(0f, max),
            onValueChange = {
                actions.onDragStart()
                actions.onDrag(it)
            },
            onValueChangeFinished = actions.onDragEnd,
            valueRange = 0f..max,
            enabled = duration != null,
            modifier = Modifier.fillMaxWidth().testTag("seekBar"),
        )
        if (duration != null) ChapterTicks(state.chapters, duration)
    }
}

@Composable
private fun ChapterTicks(chapters: List<ChapterDto>, durationS: Double) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .padding(horizontal = TRACK_INSET)
            .testTag("chapterTicks"),
    ) {
        val tickH = TICK_HEIGHT.toPx()
        val top = (size.height - tickH) / 2f
        chapters.filter { it.startS > 0.0 && it.startS < durationS }.forEach { chapter ->
            val x = (chapter.startS / durationS).toFloat() * size.width
            drawLine(color, Offset(x, top), Offset(x, top + tickH), strokeWidth = 2.dp.toPx())
        }
    }
}
