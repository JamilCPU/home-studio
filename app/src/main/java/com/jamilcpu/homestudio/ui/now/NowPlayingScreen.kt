package com.jamilcpu.homestudio.ui.now

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.ui.common.UnreachableBanner

/** Stateful entry used by the nav graph: owns the view model and the polling lifecycle. */
@Composable
fun NowPlayingRoute(
    repo: NowPlayingRepository,
    api: HomeStudioApi,
    onRate: (String) -> Unit,
    onAskAgent: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val vm = remember(repo, api) { NowPlayingViewModel(repo, api, scope) }
    val context = LocalContext.current
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { vm.stop() }
    }
    val state by vm.uiState.collectAsState()
    val actions = remember(vm, onRate, onAskAgent) {
        NowPlayingActions(
            onDragStart = vm::onDragStart,
            onDrag = vm::onDrag,
            onDragEnd = vm::onDragEnd,
            onToggle = vm::toggle,
            onNext = vm::next,
            onPrevious = vm::previous,
            onSkipBack = vm::skipBack,
            onSkipForward = vm::skipForward,
            onChapter = vm::chapterTapped,
            onQuickRate = vm::quickRate,
            onRate = onRate,
            onOpenYoutube = {
                vm.openInYoutubeUri()?.let { uri ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        // No app can open the link; nothing useful to show.
                    }
                }
            },
            onAskAgent = onAskAgent,
            onRetry = vm::retry,
        )
    }
    NowPlayingScreen(state, actions)
}

/** Stateless Now Playing remote. */
@Composable
fun NowPlayingScreen(state: NowPlayingUiState, actions: NowPlayingActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_now"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.unreachable) UnreachableBanner(actions.onRetry)
        state.notices.forEach { NoticeLine(it) }
        state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("actionError")) }
        val item = state.item
        if (item == null) {
            if (state.queueEmpty) EmptyQueue(actions.onAskAgent)
        } else {
            PlayingContent(state, item.videoId, actions)
        }
    }
}

@Composable
private fun PlayingContent(state: NowPlayingUiState, videoId: String, actions: NowPlayingActions) {
    val item = checkNotNull(state.item)
    TrackHeader(item)
    SeekBar(state, actions)
    TimeRow(state)
    ChapterRow(state.chapters, state.currentChapter, actions.onChapter)
    TransportRow(state, actions)
    state.outputDevice?.let { Text("🔊 $it", style = MaterialTheme.typography.bodyMedium) }
    RateRow(state, videoId, actions)
    TextButton(onClick = actions.onOpenYoutube, modifier = Modifier.testTag("openYoutube")) {
        Text("↗ Open in YouTube at ${formatClock(state.displayPositionS)}")
    }
}
