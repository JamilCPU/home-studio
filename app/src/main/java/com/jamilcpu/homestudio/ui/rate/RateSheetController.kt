package com.jamilcpu.homestudio.ui.rate

import com.jamilcpu.homestudio.data.api.RatingDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The video the Rate sheet is open for. [initial] prefills the sheet (a new rating replaces the old one). */
data class RateTarget(
    val videoId: String,
    val title: String? = null,
    val initial: RatingDto? = null,
)

/**
 * Shell-level holder for the Rate sheet, so Now Playing, a History row and (Task 7) the
 * notification can all open the same sheet. It lives in the AppContainer.
 */
class RateSheetController {
    private val _target = MutableStateFlow<RateTarget?>(null)
    val target: StateFlow<RateTarget?> = _target.asStateFlow()

    fun open(videoId: String, title: String? = null, initial: RatingDto? = null) {
        _target.value = RateTarget(videoId, title, initial)
    }

    fun close() {
        _target.value = null
    }
}
