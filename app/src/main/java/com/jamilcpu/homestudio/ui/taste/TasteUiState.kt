package com.jamilcpu.homestudio.ui.taste

import com.jamilcpu.homestudio.data.api.TasteDto
import com.jamilcpu.homestudio.ui.now.NOTICE_NOT_LOGGED_IN
import com.jamilcpu.homestudio.ui.now.NOTICE_RATE_LIMITED

const val TASTE_UPDATING = "Updating…"
const val TASTE_NEXT_RUN = "Will update after the agent's next run"
const val TASTE_EMPTY = "No taste profile yet. Rate a few videos and the agent will learn."
const val NOTICE_CLAUDE_MISSING = "Agent isn't installed on the Pi."

data class TasteUiState(
    /** Null when the server has no profile yet. */
    val taste: TasteDto? = null,
    val loaded: Boolean = false,
    val input: String = "",
    /** Waiting for a refetch to return a newer `updated_at`. */
    val updating: Boolean = false,
    /** Why the profile did not update (timeout, or the agent's status). */
    val note: String? = null,
    val busy: Boolean = false,
    val unreachable: Boolean = false,
    val error: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !busy
}

/** The notice for a `/api/status` `claude` value that means a refresh failed; null for "ok"/"unknown". */
fun claudeProblem(claude: String?): String? = when (claude) {
    null, "ok", "unknown" -> null
    "rate_limited" -> NOTICE_RATE_LIMITED
    "not_logged_in" -> NOTICE_NOT_LOGGED_IN
    "missing" -> NOTICE_CLAUDE_MISSING
    else -> "Agent unavailable ($claude)."
}
