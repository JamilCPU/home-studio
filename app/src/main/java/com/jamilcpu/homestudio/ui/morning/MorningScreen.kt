package com.jamilcpu.homestudio.ui.morning

import androidx.compose.runtime.Composable

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

@Composable
fun MorningRoute(vm: MorningViewModel, onTestStarted: () -> Unit) = Unit

@Composable
fun MorningScreen(state: MorningUiState, actions: MorningActions) = Unit
