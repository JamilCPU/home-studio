package com.jamilcpu.homestudio.ui.history

import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import java.time.LocalDate
import java.time.ZoneId

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

/** Groups [entries] (newest first) by local day: "Today", "Yesterday", then a medium date. */
fun groupByDay(entries: List<HistoryEntryDto>, zone: ZoneId, today: LocalDate): List<DayGroup> = emptyList()

/** "👍 8", "👎 3", "skipped m:ss" or "Rate?". */
fun rowLabel(entry: HistoryEntryDto): String = ""

/** "Unrated (n)". */
fun unratedChipLabel(count: Int?): String = ""
