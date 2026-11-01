package com.jamilcpu.homestudio.ui.taste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jamilcpu.homestudio.data.api.TraitKind
import com.jamilcpu.homestudio.ui.common.UnreachableBanner

private const val MAX_CORRECTION = 1000

data class TasteActions(
    val onRetry: () -> Unit = {},
    val onInput: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onRemove: (TraitKind, String) -> Unit = { _, _ -> },
)

/** Stateful entry used by the nav graph: reloads the profile each time the screen starts. */
@Composable
fun TasteRoute(vm: TasteViewModel) {
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { }
    }
    val state by vm.uiState.collectAsState()
    val actions = remember(vm) {
        TasteActions(onRetry = vm::start, onInput = vm::onInput, onSend = vm::submitCorrection, onRemove = vm::removeTrait)
    }
    TasteScreen(state, actions)
}

/** Stateless Taste tab: the agent's summary, likes and dislikes, and a box to correct it. */
@Composable
fun TasteScreen(state: TasteUiState, actions: TasteActions) {
    Column(Modifier.fillMaxSize().testTag("screen_taste")) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.unreachable) UnreachableBanner(actions.onRetry)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("tasteError")) }
            SummaryBubble(state)
            val taste = state.taste
            if (taste != null) {
                TraitList("Likes", TraitKind.Likes, taste.likes, enabled = !state.busy, actions.onRemove)
                TraitList("Dislikes", TraitKind.Dislikes, taste.dislikes, enabled = !state.busy, actions.onRemove)
            }
        }
        CorrectionInput(state, actions)
    }
}

@Composable
private fun SummaryBubble(state: TasteUiState) {
    if (!state.loaded && state.taste == null) return
    Card(
        Modifier.fillMaxWidth().testTag("tasteSummary"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.taste?.summary?.ifBlank { null } ?: TASTE_EMPTY)
            val status = if (state.updating) TASTE_UPDATING else state.note
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("tasteStatus"),
                )
            }
        }
    }
}

@Composable
private fun TraitList(
    title: String,
    kind: TraitKind,
    traits: List<String>,
    enabled: Boolean,
    onRemove: (TraitKind, String) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (traits.isEmpty()) {
            Text("None yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        traits.forEach { trait ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trait, Modifier.weight(1f))
                IconButton(
                    onClick = { onRemove(kind, trait) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Remove $trait" },
                ) { Text("✕") }
            }
        }
    }
}

@Composable
private fun CorrectionInput(state: TasteUiState, actions: TasteActions) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.input,
            onValueChange = { actions.onInput(it.take(MAX_CORRECTION)) },
            modifier = Modifier.weight(1f).testTag("tasteInput"),
            placeholder = { Text("Correct it: \"I actually like…\"") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { actions.onSend() }),
        )
        IconButton(onClick = actions.onSend, enabled = state.canSend, modifier = Modifier.testTag("tasteSend")) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send correction")
        }
    }
}
