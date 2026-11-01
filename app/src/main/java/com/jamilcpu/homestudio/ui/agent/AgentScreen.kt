package com.jamilcpu.homestudio.ui.agent

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jamilcpu.homestudio.ui.common.UnreachableBanner

private val CHIPS = listOf(
    "Podcast on…" to "Podcast on ",
    "Classical playlist" to "Classical playlist",
    "More like this" to "More like this",
)

/** Everything the Agent screen can ask for, so the stateless screen is easy to test. */
data class AgentActions(
    val onInput: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onPlayMode: (String) -> Unit = {},
    val onChip: (String) -> Unit = {},
    val onPlayNow: (Long) -> Unit = {},
    val onAddToEnd: (Long) -> Unit = {},
    val onRetry: (Long) -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onMic: () -> Unit = {},
    val onReconnect: () -> Unit = {},
)

/** Stateful entry used by the nav graph: binds polling to the lifecycle and wires dictation. */
@Composable
fun AgentRoute(vm: AgentViewModel) {
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { vm.stop() }
    }
    val context = LocalContext.current
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val micAvailable = remember {
        context.packageManager.queryIntentActivities(speechIntent, 0).isNotEmpty()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.let(vm::appendInput)
        }
    }
    val state by vm.uiState.collectAsState()
    val actions = remember(vm, launcher, speechIntent) {
        AgentActions(
            onInput = vm::onInput,
            onSend = vm::send,
            onPlayMode = vm::setPlayMode,
            onChip = vm::chip,
            onPlayNow = vm::playNow,
            onAddToEnd = vm::addToEnd,
            onRetry = vm::retry,
            onDismiss = vm::dismissMessages,
            onReconnect = vm::start,
            onMic = {
                try {
                    launcher.launch(speechIntent)
                } catch (_: ActivityNotFoundException) {
                    // No recognizer after all; the keyboard's own mic still works.
                }
            },
        )
    }
    AgentScreen(state, actions, micAvailable)
}

/** Stateless Agent tab: chat history with the input pinned at the bottom. */
@Composable
fun AgentScreen(state: AgentUiState, actions: AgentActions, micAvailable: Boolean) {
    val listState = rememberLazyListState()
    val last = state.history.lastOrNull()
    LaunchedEffect(state.history.size, last?.status) {
        if (state.history.isNotEmpty()) listState.scrollToItem(state.history.lastIndex)
    }
    Column(Modifier.fillMaxSize().testTag("screen_agent")) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("agentHistory"),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.history, key = { it.id }) { RequestEntry(it, actions, state.busy) }
        }
        if (state.unreachable) {
            Box(Modifier.padding(horizontal = 16.dp)) { UnreachableBanner(actions.onReconnect) }
        }
        Messages(state, actions)
        InputBar(state, actions, micAvailable)
    }
}

@Composable
private fun Messages(state: AgentUiState, actions: AgentActions) {
    val message = state.error ?: state.notice ?: return
    val isError = state.error != null
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            message,
            Modifier.weight(1f).testTag(if (isError) "agentError" else "agentNotice"),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = actions.onDismiss) { Text("Dismiss") }
    }
}

@Composable
private fun InputBar(state: AgentUiState, actions: AgentActions, micAvailable: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHIPS.forEach { (label, prefill) ->
                AssistChip(onClick = { actions.onChip(prefill) }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.playMode == PLAY_NOW,
                onClick = { actions.onPlayMode(PLAY_NOW) },
                label = { Text("Play now") },
                modifier = Modifier.testTag("mode_now"),
            )
            FilterChip(
                selected = state.playMode == PLAY_END,
                onClick = { actions.onPlayMode(PLAY_END) },
                label = { Text("Add to end") },
                modifier = Modifier.testTag("mode_end"),
            )
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions.onInput,
                modifier = Modifier.weight(1f).testTag("agentInput"),
                placeholder = { Text("Ask for something to play…") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { actions.onSend() }),
            )
            if (micAvailable) {
                IconButton(onClick = actions.onMic, modifier = Modifier.testTag("micButton")) {
                    Text("🎤", modifier = Modifier.semantics { contentDescription = "Dictate request" })
                }
            }
            IconButton(onClick = actions.onSend, enabled = state.canSend, modifier = Modifier.testTag("sendButton")) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
