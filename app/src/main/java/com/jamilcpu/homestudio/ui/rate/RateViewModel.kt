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

/** How many recent plays the prefill lookup searches for the video's latest rating. */
const val PREFILL_LOOKUP = 50

/**
 * Drives the Rate sheet for one video. Each save creates a new rating that replaces the shown one
 * (the server does not merge), so the form starts from the latest rating: [initial] when the
 * caller knows it, then whatever the newest rated play of this video in recent history says.
 * Without one it starts empty.
 */
class RateViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    val videoId: String,
    initial: RatingDto? = null,
) {
    private val _uiState = MutableStateFlow(initial?.let { RateUiState().withRating(it) } ?: RateUiState())
    val uiState: StateFlow<RateUiState> = _uiState.asStateFlow()

    init {
        scope.launch { prefill() }
    }

    /** Tapping the selected choice again clears it. */
    fun toggleLiked(value: Boolean) = _uiState.update {
        it.copy(liked = if (it.liked == value) null else value, error = null, edited = true)
    }

    /** Tapping the selected score again clears it. */
    fun toggleSatisfaction(value: Int) {
        if (value !in SATISFACTION_RANGE) return
        _uiState.update { it.copy(satisfaction = if (it.satisfaction == value) null else value, error = null, edited = true) }
    }

    fun onNotes(text: String) = _uiState.update { it.copy(notes = text.take(MAX_NOTES), error = null, edited = true) }

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

    /** Fills the form from the video's latest rating; a failed lookup keeps what is shown. */
    private suspend fun prefill() {
        val result = api.history(unratedOnly = false, limit = PREFILL_LOOKUP) as? ApiResult.Ok ?: return
        // Newest first, and every row carries its video's latest rating (or null while unrated).
        val latest = result.value.entries.firstOrNull { it.video.videoId == videoId && it.rating != null }?.rating
            ?: return
        _uiState.update { if (it.edited || it.saving || it.saved) it else it.withRating(latest) }
    }
}

private fun RateUiState.withRating(rating: RatingDto) = copy(
    liked = rating.liked,
    satisfaction = rating.satisfaction?.takeIf { it in SATISFACTION_RANGE },
    notes = rating.notes.orEmpty().take(MAX_NOTES),
)
