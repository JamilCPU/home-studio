package com.jamilcpu.homestudio.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamilcpu.homestudio.data.api.VideoDto
import com.jamilcpu.homestudio.ui.now.formatClock

/** Type to search YouTube and tap a result, or paste a YouTube link to add it directly. Adds go to the end. */
@Composable
internal fun SearchDialog(state: SearchUiState, actions: QueueActions) {
    AlertDialog(
        onDismissRequest = actions.onCloseSearch,
        title = { Text("Search / add") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onSearchQuery,
                    modifier = Modifier.fillMaxWidth().testTag("searchField"),
                    placeholder = { Text("Search, or paste a YouTube link") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = if (state.isLink) ImeAction.Done else ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.onSubmitSearch() }, onDone = { actions.onSubmitSearch() }),
                )
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("searchError"))
                }
                if (state.results.isNotEmpty()) Results(state.results, enabled = !state.working, actions.onAddResult)
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.onSubmitSearch,
                enabled = state.query.isNotBlank() && !state.working,
                modifier = Modifier.testTag("searchSubmit"),
            ) { Text(if (state.isLink) "Add link" else "Search") }
        },
        dismissButton = { TextButton(onClick = actions.onCloseSearch) { Text("Close") } },
    )
}

@Composable
private fun Results(results: List<VideoDto>, enabled: Boolean, onAdd: (String) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 360.dp).testTag("searchResults")) {
        items(results, key = { it.videoId }) { video ->
            ListItem(
                modifier = Modifier.clickable(enabled = enabled) { onAdd(video.videoId) }.testTag("result_${video.videoId}"),
                headlineContent = { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val details = listOfNotNull(video.channel, video.durationS?.let { formatClock(it.toDouble()) })
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), maxLines = 1)
                },
            )
        }
    }
}
