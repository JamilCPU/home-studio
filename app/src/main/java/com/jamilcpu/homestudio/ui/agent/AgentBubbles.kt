package com.jamilcpu.homestudio.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jamilcpu.homestudio.data.api.AgentRequestDto
import kotlin.math.roundToInt

const val MORNING_LABEL = "🌅 Morning set"
const val WORKING_TEXT = "Working… searching, reading transcripts"

/** One exchange: the prompt on the right, the agent's reply on the left. */
@Composable
fun RequestEntry(request: AgentRequestDto, actions: AgentActions, busy: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Bubble(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "prompt_${request.id}") {
                Text(if (request.kind == KIND_MORNING) MORNING_LABEL else request.prompt)
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { Reply(request, actions, busy) }
    }
}

@Composable
private fun Reply(request: AgentRequestDto, actions: AgentActions, busy: Boolean) {
    when (request.status) {
        STATUS_FAILED -> Bubble(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "failed_${request.id}") {
            Text(request.error ?: "Something went wrong.")
            TextButton(onClick = { actions.onRetry(request.id) }, modifier = Modifier.testTag("retry_${request.id}")) {
                Text("Try again")
            }
        }
        STATUS_DONE -> DoneReply(request, actions, busy)
        else -> Bubble(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "working_${request.id}") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(WORKING_TEXT)
            }
        }
    }
}

@Composable
private fun DoneReply(request: AgentRequestDto, actions: AgentActions, busy: Boolean) {
    Bubble(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "done_${request.id}") {
        if (request.items.isEmpty()) {
            Text(request.summary ?: "Nothing new to queue.")
            return@Bubble
        }
        val n = request.items.size
        val minutes = (request.items.sumOf { it.durationS ?: 0 } / 60.0).roundToInt()
        Text("Queued $n ${if (n == 1) "video" else "videos"} ($minutes min):", style = MaterialTheme.typography.titleSmall)
        request.items.forEach { Text(it.title, Modifier.padding(start = 8.dp)) }
        request.summary?.takeIf { it.isNotBlank() }?.let { Text("Why: $it", style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.onPlayNow(request.id) }, enabled = !busy, modifier = Modifier.testTag("playNow_${request.id}")) {
                Text("Play now")
            }
            OutlinedButton(onClick = { actions.onAddToEnd(request.id) }, enabled = !busy, modifier = Modifier.testTag("addToEnd_${request.id}")) {
                Text("Add to end")
            }
        }
    }
}

@Composable
private fun Bubble(container: Color, content: Color, tag: String, body: @Composable () -> Unit) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag(tag),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { body() }
    }
}
