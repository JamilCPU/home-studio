package com.jamilcpu.homestudio.ui.rate

import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.RatingDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Rate sheet for one video. Each save creates a new rating that replaces the shown one
 * (the server does not merge), so the form starts from [initial] when the caller knows it.
 */
class RateViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    val videoId: String,
    initial: RatingDto? = null,
) {
    private val _uiState = MutableStateFlow(
        RateUiState(
            liked = initial?.liked,
            satisfaction = initial?.satisfaction?.takeIf { it in SATISFACTION_RANGE },
            notes = initial?.notes.orEmpty().take(MAX_NOTES),
        ),
    )
    val uiState: StateFlow<RateUiState> = _uiState.asStateFlow()

    /** Tapping the selected choice again clears it. */
    fun toggleLiked(value: Boolean) = _uiState.update {
        it.copy(liked = if (it.liked == value) null else value, error = null)
    }

    /** Tapping the selected score again clears it. */
    fun toggleSatisfaction(value: Int) {
        if (value !in SATISFACTION_RANGE) return
        _uiState.update { it.copy(satisfaction = if (it.satisfaction == value) null else value, error = null) }
    }

    fun onNotes(text: String) = _uiState.update { it.copy(notes = text.take(MAX_NOTES), error = null) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.update { it.copy(saving = true, error = null, unreachable = false) }
        scope.launch {
            val result = api.rate(
                videoId = videoId,
                liked = s.liked,
                satisfaction = s.satisfaction,
                notes = s.notes.trim().ifEmpty { null },
            )
            _uiState.update {
                when (result) {
                    is ApiResult.Ok -> it.copy(saving = false, saved = true)
                    is ApiResult.Failure -> if (result.kind == ApiResult.Failure.Kind.Unreachable) {
                        it.copy(saving = false, unreachable = true)
                    } else {
                        it.copy(saving = false, error = result.message)
                    }
                }
            }
        }
    }
}
