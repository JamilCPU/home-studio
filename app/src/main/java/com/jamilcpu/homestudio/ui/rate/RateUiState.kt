package com.jamilcpu.homestudio.ui.rate

const val MAX_NOTES = 2000
val SATISFACTION_RANGE = 1..10

/** The Rate sheet's form. [liked] is tri-state: null (unset), true (like), false (dislike). */
data class RateUiState(
    val liked: Boolean? = null,
    val satisfaction: Int? = null,
    val notes: String = "",
    val saving: Boolean = false,
    /** The server's message (a 422 is shown as is). */
    val error: String? = null,
    val unreachable: Boolean = false,
    /** The rating was stored; the host closes the sheet. */
    val saved: Boolean = false,
) {
    val hasInput: Boolean get() = liked != null || satisfaction != null || notes.isNotBlank()

    val canSave: Boolean get() = hasInput && !saving && !saved
}
