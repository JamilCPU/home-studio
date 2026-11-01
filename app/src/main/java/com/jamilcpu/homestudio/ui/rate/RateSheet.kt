package com.jamilcpu.homestudio.ui.rate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.ui.common.UnreachableBanner

data class RateActions(
    val onLiked: (Boolean) -> Unit = {},
    val onSatisfaction: (Int) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * Hosted once at the shell level: shows the Rate sheet whenever [controller] has a target.
 * [onRated] runs after a successful save, once the sheet has closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateSheetHost(controller: RateSheetController, api: HomeStudioApi, onRated: () -> Unit) {
    val target by controller.target.collectAsState()
    val current = target ?: return
    val scope = rememberCoroutineScope()
    val vm = remember(current) { RateViewModel(api, scope, current.videoId, current.initial) }
    val state by vm.uiState.collectAsState()
    LaunchedEffect(vm, state.saved) {
        if (state.saved) {
            controller.close()
            onRated()
        }
    }
    val actions = remember(vm) {
        RateActions(
            onLiked = vm::toggleLiked,
            onSatisfaction = vm::toggleSatisfaction,
            onNotes = vm::onNotes,
            onSave = vm::save,
        )
    }
    ModalBottomSheet(
        onDismissRequest = controller::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("rateSheet"),
    ) {
        RateSheetContent(current.title, state, actions)
    }
}

/** Stateless sheet body: like/dislike, a 1–10 score, notes and Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RateSheetContent(title: String?, state: RateUiState, actions: RateActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title ?: "Rate", style = MaterialTheme.typography.titleMedium)
        if (state.unreachable) UnreachableBanner(actions.onSave)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LikedChip("👍 Like", value = true, state.liked, actions.onLiked)
            LikedChip("👎 Dislike", value = false, state.liked, actions.onLiked)
        }
        Text("Satisfaction", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SATISFACTION_RANGE.forEach { n ->
                FilterChip(
                    selected = state.satisfaction == n,
                    onClick = { actions.onSatisfaction(n) },
                    label = { Text("$n") },
                    modifier = Modifier.testTag("sat_$n"),
                )
            }
        }
        OutlinedTextField(
            value = state.notes,
            onValueChange = actions.onNotes,
            modifier = Modifier.fillMaxWidth().testTag("rateNotes"),
            label = { Text("Notes") },
            supportingText = { Text("${state.notes.length}/$MAX_NOTES") },
            minLines = 2,
            maxLines = 6,
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("rateError"))
        }
        Button(
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().testTag("rateSave"),
        ) {
            Text(if (state.saving) "Saving…" else "Save")
        }
    }
}

@Composable
private fun LikedChip(label: String, value: Boolean, liked: Boolean?, onLiked: (Boolean) -> Unit) {
    FilterChip(
        selected = liked == value,
        onClick = { onLiked(value) },
        label = { Text(label) },
        modifier = Modifier.testTag("liked_$value"),
    )
}
