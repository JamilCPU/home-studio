package com.jamilcpu.homestudio.ui.rate

import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.RatingDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Drives the Rate sheet for one video. */
class RateViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    val videoId: String,
    initial: RatingDto? = null,
) {
    private val _uiState = MutableStateFlow(RateUiState())
    val uiState: StateFlow<RateUiState> = _uiState.asStateFlow()

    fun toggleLiked(value: Boolean) = Unit

    fun toggleSatisfaction(value: Int) = Unit

    fun onNotes(text: String) = Unit

    fun save() = Unit
}
