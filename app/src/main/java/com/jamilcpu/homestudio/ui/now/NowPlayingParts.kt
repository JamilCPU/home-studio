package com.jamilcpu.homestudio.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jamilcpu.homestudio.data.api.ChapterDto
import com.jamilcpu.homestudio.data.api.QueueItemDto

private val ART_SHAPE = RoundedCornerShape(12.dp)
private const val ART_ASPECT = 16f / 9f

@Composable
fun UnreachableBanner(onRetry: () -> Unit) {
    NoticeCard(MaterialTheme.colorScheme.errorContainer, "bannerUnreachable") {
        Text("Can't reach your Pi", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun NoticeLine(text: String) {
    NoticeCard(MaterialTheme.colorScheme.tertiaryContainer, "notice") {
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun NoticeCard(container: Color, tag: String, content: @Composable RowScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().testTag(tag),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun EmptyQueue(onAskAgent: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp).testTag("emptyQueue"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(NOTICE_QUEUE_EMPTY, style = MaterialTheme.typography.titleLarge)
        Button(onClick = onAskAgent) { Text("Ask the agent") }
    }
}

@Composable
fun TrackHeader(item: QueueItemDto) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Artwork(item.thumbnailUrl)
        Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.channel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        item.reason?.takeIf { it.isNotBlank() }?.let {
            Text("🤖 Picked because: $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Artwork(url: String?) {
    val modifier = Modifier.fillMaxWidth().aspectRatio(ART_ASPECT).clip(ART_SHAPE)
    if (url == null) {
        Box(modifier.testTag("artworkPlaceholder").background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier.testTag("artwork"),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun TimeRow(state: NowPlayingUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(elapsedLabel(state.displayPositionS), style = MaterialTheme.typography.labelMedium)
        Text(remainingLabel(state.displayPositionS, state.durationS), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ChapterRow(chapters: List<ChapterDto>, current: ChapterDto?, onChapter: (Double) -> Unit) {
    if (chapters.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        current?.let {
            Text(it.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("currentChapter"))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chapters) { chapter ->
                AssistChip(
                    onClick = { onChapter(chapter.startS) },
                    label = { Text("${formatClock(chapter.startS)} ${chapter.title}", maxLines = 1) },
                )
            }
        }
    }
}

@Composable
fun TransportRow(state: NowPlayingUiState, actions: NowPlayingActions) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton("⏪15s", "Back 15 seconds", "skipBack", actions.onSkipBack)
        TransportButton("⏮", "Previous", "previous", actions.onPrevious)
        if (state.isPlaying) {
            TransportButton("⏸", "Pause", "playPause", actions.onToggle)
        } else {
            TransportButton("▶️", "Play", "playPause", actions.onToggle)
        }
        TransportButton("⏭", "Next", "next", actions.onNext)
        TransportButton("30s⏩", "Forward 30 seconds", "skipForward", actions.onSkipForward)
    }
}

@Composable
private fun TransportButton(label: String, description: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag).semantics { contentDescription = description }) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun RateRow(state: NowPlayingUiState, videoId: String, actions: NowPlayingActions) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatingButton("👍", "Like", "like", state.quickRating == true) { actions.onQuickRate(true) }
        RatingButton("👎", "Dislike", "dislike", state.quickRating == false) { actions.onQuickRate(false) }
        OutlinedButton(onClick = { actions.onRate(videoId) }, modifier = Modifier.testTag("rate")) {
            Text("✏️ Rate")
        }
    }
}

@Composable
private fun RatingButton(label: String, description: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.testTag(tag).semantics { contentDescription = description }
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
