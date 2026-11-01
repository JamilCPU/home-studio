package com.jamilcpu.homestudio.ui.taste

import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.TraitKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val TASTE_POLL_MS = 3_000L
const val TASTE_TIMEOUT_MS = 60_000L

class TasteViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = TASTE_POLL_MS,
    private val timeoutMs: Long = TASTE_TIMEOUT_MS,
) {
    private val _uiState = MutableStateFlow(TasteUiState())
    val uiState: StateFlow<TasteUiState> = _uiState.asStateFlow()

    fun start() = Unit
    fun onInput(text: String) = Unit
    fun submitCorrection() = Unit
    fun removeTrait(kind: TraitKind, text: String) = Unit
}
