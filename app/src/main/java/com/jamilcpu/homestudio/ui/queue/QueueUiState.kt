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

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** "Ends in 1h 41m, then stops"; under an hour "Ends in 41m, then stops". */
fun endsInLabel(remainingS: Int): String {
    val minutes = remainingS.coerceAtLeast(0) / SECONDS_PER_MINUTE
    val h = minutes / MINUTES_PER_HOUR
    val m = minutes % MINUTES_PER_HOUR
    val span = if (h > 0) "${h}h ${m}m" else "${m}m"
    return "Ends in $span, then stops"
}

/** The row tag for a queue item's `source`; the fallback set is part of the morning. */
fun sourceLabel(source: String): String = when (source) {
    "agent" -> "Agent"
    "user" -> "You added"
    "morning", "fallback" -> "Morning"
    else -> source
}

private val YOUTUBE_LINK = Regex(
    """^(https?://)?((www|m|music)\.)?(youtube\.com/(watch\?(\S*&)?v=|shorts/)|youtu\.be/)[\w-]{6,}(\S*)$""",
    RegexOption.IGNORE_CASE,
)

/** True for the links the server accepts: watch?v=, youtu.be/ and shorts/, with or without a scheme. */
fun isYoutubeLink(text: String): Boolean = YOUTUBE_LINK.matches(text.trim())
