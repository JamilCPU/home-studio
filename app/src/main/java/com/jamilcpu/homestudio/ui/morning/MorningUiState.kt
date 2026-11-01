package com.jamilcpu.homestudio.ui.morning

import com.jamilcpu.homestudio.data.api.MorningDto
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

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

private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_HOUR = 60
private const val NOON = 12

/** Minutes after midnight for "HH:mm", or null if it is not a valid 24-hour time. */
fun parseHhMm(hhmm: String): Int? {
    val parts = hhmm.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * MINUTES_PER_HOUR + m
}

/** "HH:mm" for minutes after midnight (wrapping round the day). */
fun formatHhMm(minutes: Int): String {
    val m = Math.floorMod(minutes, MINUTES_PER_DAY)
    return String.format(Locale.ROOT, "%02d:%02d", m / MINUTES_PER_HOUR, m % MINUTES_PER_HOUR)
}

/** 12-hour display for minutes after midnight: 465 → "7:45 AM". */
fun format12(minutes: Int): String {
    val m = Math.floorMod(minutes, MINUTES_PER_DAY)
    val h24 = m / MINUTES_PER_HOUR
    val h12 = if (h24 % NOON == 0) NOON else h24 % NOON
    val suffix = if (h24 < NOON) "AM" else "PM"
    return String.format(Locale.ROOT, "%d:%02d %s", h12, m % MINUTES_PER_HOUR, suffix)
}

/** "07:45" → "7:45 AM"; an unparseable value is shown as is. */
fun format12(hhmm: String): String = parseHhMm(hhmm)?.let(::format12) ?: hhmm

/** "Central Time · agent starts picking at 7:40 AM" for wake "07:45" and lead 5 (ruling A7). */
fun pickingLabel(wakeTime: String, leadMin: Int): String {
    val wake = parseHhMm(wakeTime) ?: return "Central Time"
    return "Central Time · agent starts picking at ${format12(wake - leadMin)}"
}

/**
 * "Next set builds 7:40 AM · Last: built ✓ started ✓" (ruling A7). The time is the station's own
 * (next_build_at carries its offset); a build more than a day ahead also names the weekday.
 */
fun morningStatusLine(dto: MorningDto, now: OffsetDateTime): String {
    val next = dto.nextBuildAt?.let { parseOffset(it) }
    val head = if (next == null) {
        "No set scheduled"
    } else {
        val stationToday = now.withOffsetSameInstant(next.offset).toLocalDate()
        val time = format12(next.hour * MINUTES_PER_HOUR + next.minute)
        val day = next.toLocalDate()
        val prefix = if (day == stationToday || day == stationToday.plusDays(1)) {
            ""
        } else {
            next.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " "
        }
        "Next set builds $prefix$time"
    }
    val last = dto.lastOutcome ?: return head
    val parts = buildList {
        add("built ${mark(last.built)}")
        if (last.fallback) add("fallback ✓")
        add("started ${mark(last.started)}")
    }
    return "$head · Last: ${parts.joinToString(" ")}"
}

private fun mark(ok: Boolean) = if (ok) "✓" else "✗"

private fun parseOffset(iso: String): OffsetDateTime? = try {
    OffsetDateTime.parse(iso)
} catch (_: DateTimeParseException) {
    null
}
