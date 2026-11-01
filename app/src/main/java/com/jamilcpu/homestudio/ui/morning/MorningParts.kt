package com.jamilcpu.homestudio.ui.morning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Monday first, matching the API's 0 = Monday … 6 = Sunday. */
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private const val MINUTES_PER_HOUR = 60
private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "label  [−] value [+]", clamped to [range]; tags are "<tag>_minus", "<tag>_value" and "<tag>_plus". */
@Composable
internal fun Stepper(label: String, value: Int, unit: String, range: IntRange, tag: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            modifier = Modifier.testTag("${tag}_minus").semantics { contentDescription = "Less" },
        ) { Text("−") }
        Text(
            if (unit.isEmpty()) "$value" else "$value $unit",
            Modifier.padding(horizontal = 12.dp).testTag("${tag}_value"),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.testTag("${tag}_plus").semantics { contentDescription = "More" },
        ) { Text("+") }
    }
}

@Composable
internal fun DayToggles(days: List<Int>, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DAY_LETTERS.forEachIndexed { day, letter ->
            FilterChip(
                selected = day in days,
                onClick = { onToggle(day) },
                label = { Text(letter) },
                modifier = Modifier.testTag("day_$day").semantics { contentDescription = DAY_NAMES[day] },
            )
        }
    }
}

/** Removable topic chips plus "+ add", which opens an inline field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopicChips(
    topics: List<String>,
    input: String,
    onRemove: (String) -> Unit,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        topics.forEach { topic ->
            InputChip(
                selected = false,
                onClick = { onRemove(topic) },
                label = { Text(topic) },
                trailingIcon = { Text("✕") },
                modifier = Modifier.testTag("topic_$topic").semantics { contentDescription = "Remove $topic" },
            )
        }
        AssistChip(onClick = { adding = true }, label = { Text("+ add") }, modifier = Modifier.testTag("addTopic"))
    }
    if (adding) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f).testTag("topicInput"),
                placeholder = { Text("New topic") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )
            TextButton(onClick = onAdd, enabled = input.isNotBlank(), modifier = Modifier.testTag("topicAdd")) { Text("Add") }
            TextButton(onClick = { adding = false }) { Text("Done") }
        }
    }
}

/** Material 3 time picker in a dialog; returns "HH:mm" (24-hour) on OK. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WakeTimeDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val minutes = parseHhMm(initial) ?: 0
    val picker = rememberTimePickerState(initialHour = minutes / MINUTES_PER_HOUR, initialMinute = minutes % MINUTES_PER_HOUR, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wake time") },
        text = { TimePicker(state = picker) },
        confirmButton = {
            TextButton(onClick = { onConfirm(formatHhMm(picker.hour * MINUTES_PER_HOUR + picker.minute)) }, modifier = Modifier.testTag("timeOk")) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
