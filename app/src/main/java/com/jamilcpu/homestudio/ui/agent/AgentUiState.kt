package com.jamilcpu.homestudio.ui.agent

import com.jamilcpu.homestudio.data.api.AgentRequestDto

const val STATUS_PENDING = "pending"
const val STATUS_RUNNING = "running"
const val STATUS_DONE = "done"
const val STATUS_FAILED = "failed"

const val PLAY_NOW = "now"
const val PLAY_END = "end"

const val KIND_MORNING = "morning"

val AgentRequestDto.isInFlight: Boolean get() = status == STATUS_PENDING || status == STATUS_RUNNING

/**
 * Everything the Agent tab shows.
 *
 * [history] is oldest first (chat order). [active] is the newest request still pending or running.
 * [notice] is a neutral one-liner about the last Play now / Add to end; [error] is a failure message.
 */
data class AgentUiState(
    val history: List<AgentRequestDto> = emptyList(),
    val input: String = "",
    /** [PLAY_NOW] or [PLAY_END]. */
    val playMode: String = PLAY_NOW,
    val sending: Boolean = false,
    val active: AgentRequestDto? = null,
    val notice: String? = null,
    val error: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !sending
}

/** Returns a copy showing [history] (oldest first) with [AgentUiState.active] derived from it. */
internal fun AgentUiState.withHistory(history: List<AgentRequestDto>): AgentUiState =
    copy(history = history, active = history.lastOrNull { it.isInFlight })
