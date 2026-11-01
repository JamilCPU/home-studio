package com.jamilcpu.homestudio.ui.morning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jamilcpu.homestudio.data.api.MorningDto
import com.jamilcpu.homestudio.ui.common.UnreachableBanner
import java.time.OffsetDateTime

data class MorningActions(
    val onRetry: () -> Unit = {},
    val onEnabled: (Boolean) -> Unit = {},
    val onWakeTime: (String) -> Unit = {},
    val onToggleDay: (Int) -> Unit = {},
    val onRemoveTopic: (String) -> Unit = {},
    val onTopicInput: (String) -> Unit = {},
    val onAddTopic: () -> Unit = {},
    val onCount: (Int) -> Unit = {},
    val onLead: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
    val onTestNow: () -> Unit = {},
)

/** Stateful entry used by the nav graph; [onTestStarted] runs after "▶ Test it now" is accepted. */
@Composable
fun MorningRoute(vm: MorningViewModel, onTestStarted: () -> Unit) {
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { }
    }
    val state by vm.uiState.collectAsState()
    val started by rememberUpdatedState(onTestStarted)
    val actions = remember(vm) {
        MorningActions(
            onRetry = vm::start,
            onEnabled = vm::setEnabled,
            onWakeTime = vm::setWakeTime,
            onToggleDay = vm::toggleDay,
            onRemoveTopic = vm::removeTopic,
            onTopicInput = vm::onTopicInput,
            onAddTopic = vm::addTopic,
            onCount = vm::setCount,
            onLead = vm::setLead,
            onSave = vm::save,
            onTestNow = { vm.testNow { started() } },
        )
    }
    MorningScreen(state, actions)
}

/** Stateless Morning settings (behind the ⚙ in the top bar). */
@Composable
fun MorningScreen(state: MorningUiState, actions: MorningActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_morning"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.unreachable) UnreachableBanner(actions.onRetry)
        state.draft?.let { draft -> Settings(draft, state, actions) }
        state.saved?.let { saved ->
            Text(
                morningStatusLine(saved, OffsetDateTime.now()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("morningStatus"),
            )
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("morningError")) }
        state.notice?.let { Text(it, modifier = Modifier.testTag("morningNotice")) }
        if (state.draft != null) Buttons(state, actions)
    }
}

@Composable
private fun Settings(draft: MorningDto, state: MorningUiState, actions: MorningActions) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Morning set", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = draft.enabled, onCheckedChange = actions.onEnabled, modifier = Modifier.testTag("morningEnabled"))
    }
    Column {
        TextButton(onClick = { pickingTime = true }, modifier = Modifier.testTag("wakeTime")) {
            Text(format12(draft.wakeTime), style = MaterialTheme.typography.displaySmall)
        }
        Text(
            pickingLabel(draft.wakeTime, draft.buildLeadMin),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("pickingLabel"),
        )
    }
    Stepper("Agent starts picking", draft.buildLeadMin, "min before", LEAD_RANGE, "lead", actions.onLead)
    DayToggles(draft.days, actions.onToggleDay)
    Text("Topics", style = MaterialTheme.typography.titleSmall)
    TopicChips(draft.topics, state.topicInput, actions.onRemoveTopic, actions.onTopicInput, actions.onAddTopic)
    Stepper("Videos", draft.count, "", COUNT_RANGE, "count", actions.onCount)
    if (pickingTime) {
        WakeTimeDialog(
            initial = draft.wakeTime,
            onConfirm = {
                pickingTime = false
                actions.onWakeTime(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun Buttons(state: MorningUiState, actions: MorningActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = actions.onSave, enabled = state.canSave, modifier = Modifier.testTag("morningSave")) {
            Text(if (state.saving) "Saving…" else "Save")
        }
        OutlinedButton(onClick = actions.onTestNow, enabled = !state.testing, modifier = Modifier.testTag("testNow")) {
            Text("▶ Test it now")
        }
    }
}
