package com.jamilcpu.homestudio.ui.morning

import com.jamilcpu.homestudio.data.api.MorningDto
import java.time.OffsetDateTime

val COUNT_RANGE = 1..6
val LEAD_RANGE = 1..60
const val MORNING_NO_TOPICS = "Add at least one topic"
const val MORNING_SAVED = "Saved"

data class MorningUiState(
    /** What the server has. */
    val saved: MorningDto? = null,
    /** What the screen shows and Save sends. */
    val draft: MorningDto? = null,
    val topicInput: String = "",
    val saving: Boolean = false,
    val testing: Boolean = false,
    /** The server's message (a 422 is shown as is) or a local validation message. */
    val error: String? = null,
    val notice: String? = null,
    val unreachable: Boolean = false,
) {
    val dirty: Boolean get() = draft != null && draft != saved

    val canSave: Boolean get() = dirty && !saving
}

/** "07:45" → "7:45 AM". */
fun format12(hhmm: String): String = ""

/** "Central Time · agent starts picking at 7:40 AM" for wake "07:45" and lead 5. */
fun pickingLabel(wakeTime: String, leadMin: Int): String = ""

/** "Next set builds 7:40 AM · Last: built ✓ started ✓" (ruling A7). */
fun morningStatusLine(dto: MorningDto, now: OffsetDateTime): String = ""
