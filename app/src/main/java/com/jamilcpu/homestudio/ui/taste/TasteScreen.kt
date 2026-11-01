package com.jamilcpu.homestudio.ui.taste

import androidx.compose.runtime.Composable
import com.jamilcpu.homestudio.data.api.TraitKind

data class TasteActions(
    val onRetry: () -> Unit = {},
    val onInput: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onRemove: (TraitKind, String) -> Unit = { _, _ -> },
)

@Composable
fun TasteRoute(vm: TasteViewModel) = Unit

@Composable
fun TasteScreen(state: TasteUiState, actions: TasteActions) = Unit
