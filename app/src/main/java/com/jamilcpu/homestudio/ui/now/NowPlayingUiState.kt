package com.jamilcpu.homestudio.ui.now

import com.jamilcpu.homestudio.data.api.ChapterDto
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import com.jamilcpu.homestudio.data.api.QueueItemDto
import com.jamilcpu.homestudio.data.api.StatusDto

const val NOTICE_COOKIES = "YouTube sign-in expired. Re-upload cookies on the Pi."
const val NOTICE_BLUETOOTH = "Speaker disconnected. Reconnecting…"
const val NOTICE_RATE_LIMITED = "Agent is rate-limited. Try again later."
const val NOTICE_NOT_LOGGED_IN = "Agent isn't logged in on the Pi."
const val NOTICE_SOURCE_BROKEN = "Playback source broken (YouTube changed?). The Pi updates yt-dlp nightly."
const val NOTICE_QUEUE_EMPTY = "Queue empty"

data class NowPlayingUiState(
    val item: QueueItemDto? = null,
    /** "playing", "paused" or "idle". */
    val state: String = "idle",
    val positionS: Double = 0.0,
    val durationS: Double? = null,
    val outputDevice: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val notices: List<String> = emptyList(),
    val unreachable: Boolean = false,
    /** Non-null only while the user's finger is on the seek bar. */
    val dragPositionS: Float? = null,
    /** Idle with nothing to play. */
    val queueEmpty: Boolean = false,
    /** Last rating tapped for the current video (true = up, false = down). */
    val quickRating: Boolean? = null,
    /** Server or network error from the last action, if any. */
    val actionError: String? = null,
) {
    val isPlaying: Boolean get() = state == "playing"

    /** What the seek bar shows: the drag position while dragging, else the polled position. */
    val displayPositionS: Double get() = dragPositionS?.toDouble() ?: positionS

    val currentChapter: ChapterDto?
        get() = chapters.lastOrNull { it.startS <= displayPositionS }
}

/** Builds the status notices in a fixed order; unknown values produce no notice. */
fun noticesFor(status: StatusDto?): List<String> {
    if (status == null) return emptyList()
    return buildList {
        if (status.cookies == "expired") add(NOTICE_COOKIES)
        if (status.bluetooth == "disconnected") add(NOTICE_BLUETOOTH)
        when (status.claude) {
            "rate_limited" -> add(NOTICE_RATE_LIMITED)
            "not_logged_in" -> add(NOTICE_NOT_LOGGED_IN)
        }
        if (status.source == "broken") add(NOTICE_SOURCE_BROKEN)
    }
}

internal fun NowPlayingDto?.isQueueEmpty(): Boolean = this != null && state == "idle" && item == null
