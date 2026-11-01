package com.jamilcpu.homestudio.ui.queue

import com.jamilcpu.homestudio.data.api.QueueItemDto
import com.jamilcpu.homestudio.data.api.VideoDto

/** The "🔍 Search / add" dialog. */
data class SearchUiState(
    val query: String = "",
    val results: List<VideoDto> = emptyList(),
    val working: Boolean = false,
    val error: String? = null,
) {
    val isLink: Boolean get() = isYoutubeLink(query)
}

data class QueueUiState(
    val items: List<QueueItemDto> = emptyList(),
    val remainingS: Int = 0,
    val loaded: Boolean = false,
    val unreachable: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    /** A clear or reorder is running. */
    val busy: Boolean = false,
    /** A finger is dragging a row: polling must not overwrite the order. */
    val dragging: Boolean = false,
    /** Non-null while the search dialog is open. */
    val search: SearchUiState? = null,
)

/** "Ends in 1h 41m, then stops". */
fun endsInLabel(remainingS: Int): String = ""

/** The row tag for a queue item's `source`. */
fun sourceLabel(source: String): String = ""

/** True for the links the server accepts: watch?v=, youtu.be/ and shorts/, with or without a scheme. */
fun isYoutubeLink(text: String): Boolean = false
