package com.jamilcpu.homestudio.ui.history

import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import com.jamilcpu.homestudio.ui.now.formatClock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

const val HISTORY_PAGE = 50

/** The most unrated plays the filter chip counts exactly; beyond it shows "200+". */
const val UNRATED_COUNT_CAP = 200

data class HistoryUiState(
    /** Newest first, de-duplicated by play id. */
    val entries: List<HistoryEntryDto> = emptyList(),
    val unratedOnly: Boolean = false,
    /** Unrated plays, capped at [UNRATED_COUNT_CAP]; null until known. */
    val unratedCount: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loaded: Boolean = false,
    val unreachable: Boolean = false,
    val error: String? = null,
)

/** One day's plays under its label. */
data class DayGroup(val label: String, val entries: List<HistoryEntryDto>)

/**
 * Groups [entries] (newest first) by their local day in [zone]: "Today", "Yesterday", then a medium
 * date. Entries whose timestamp cannot be parsed join the group before them.
 */
fun groupByDay(entries: List<HistoryEntryDto>, zone: ZoneId, today: LocalDate): List<DayGroup> {
    val groups = mutableListOf<Pair<LocalDate?, MutableList<HistoryEntryDto>>>()
    entries.forEach { entry ->
        val day = localDay(entry.startedAt, zone) ?: groups.lastOrNull()?.first
        val last = groups.lastOrNull()
        if (last != null && last.first == day) last.second += entry else groups += day to mutableListOf(entry)
    }
    return groups.map { (day, items) -> DayGroup(dayLabel(day, today), items.toList()) }
}

private val MEDIUM_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun localDay(iso: String, zone: ZoneId): LocalDate? = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDate()
} catch (_: DateTimeParseException) {
    null
}

private fun dayLabel(day: LocalDate?, today: LocalDate): String = when (day) {
    null -> "Earlier"
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> MEDIUM_DATE.format(day)
}

/** "👍 8", "👎 3", "skipped m:ss" or "Rate?". A notes-only rating shows "📝". */
fun rowLabel(entry: HistoryEntryDto): String {
    val rating = entry.rating
    if (rating != null) {
        val parts = listOfNotNull(
            rating.liked?.let { if (it) "👍" else "👎" },
            rating.satisfaction?.toString(),
        )
        if (parts.isNotEmpty()) return parts.joinToString(" ")
        if (!rating.notes.isNullOrBlank()) return "📝"
    }
    if (entry.endReason == "skipped") return "skipped ${formatClock(entry.secondsListened ?: 0.0)}"
    return "Rate?"
}

/** "Unrated (n)", "Unrated (200+)" at the cap, or "Unrated" while the count is unknown. */
fun unratedChipLabel(count: Int?): String = when {
    count == null -> "Unrated"
    count >= UNRATED_COUNT_CAP -> "Unrated ($UNRATED_COUNT_CAP+)"
    else -> "Unrated ($count)"
}
