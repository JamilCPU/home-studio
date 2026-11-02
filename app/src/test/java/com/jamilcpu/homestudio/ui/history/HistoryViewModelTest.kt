package com.jamilcpu.homestudio.ui.history

import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.Reply
import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.data.api.HistoryEntryDto
import com.jamilcpu.homestudio.data.api.RatingDto
import com.jamilcpu.homestudio.data.api.VideoDto
import com.jamilcpu.homestudio.queueItemJson
import com.jamilcpu.homestudio.settle
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private fun entry(
        playId: Long,
        startedAt: String = "2026-11-01T14:03:22.512345+00:00",
        endReason: String? = "finished",
        listened: Double? = 600.0,
        rating: RatingDto? = null,
    ) = HistoryEntryDto(
        playId = playId,
        video = VideoDto(videoId = "v$playId", title = "Video $playId"),
        startedAt = startedAt,
        secondsListened = listened,
        endReason = endReason,
        source = "agent",
        rating = rating,
    )

    private fun entryJson(playId: Long) =
        """{"play_id":$playId,"video":{"video_id":"v$playId","title":"Video $playId","channel":null,"thumbnail_url":null,"duration_s":600,"chapters":[]},"started_at":"2026-11-01T14:03:22+00:00","seconds_listened":600.0,"end_reason":"finished","source":"agent","rating":null}"""

    /** 120 plays in total; 7 unrated. */
    private val server = FakeServer().apply {
        ok("GET", "/api/history") { req ->
            val unrated = req.url.parameters["unrated"] == "true"
            val limit = req.url.parameters["limit"]!!.toInt()
            val offset = req.url.parameters["offset"]!!.toInt()
            val total = if (unrated) 7 else 120
            val ids = (offset until minOf(total, offset + limit)).map { it + 1L }
            """{"entries":[${ids.joinToString(",") { entryJson(it) }}]}"""
        }
    }

    private fun TestScope.vm() = HistoryViewModel(server.api(), CoroutineScope(StandardTestDispatcher(testScheduler)))

    private fun pages(unrated: Boolean) = server.calls("GET", "/api/history").filter {
        it.url.parameters["unrated"] == unrated.toString() && it.url.parameters["limit"] == "50"
    }

    @Test
    fun historyGroupsTodayYesterday() {
        val zone = ZoneId.of("America/Chicago") // UTC-5 on 2026-11-01 (CDT ends that day at 2 AM)
        val today = LocalDate.of(2026, 11, 1)
        val groups = groupByDay(
            listOf(
                entry(4, "2026-11-01T20:00:00+00:00"), // today 2 PM local
                entry(3, "2026-11-01T04:30:00.5+00:00"), // yesterday 11:30 PM local, though "today" in UTC
                entry(2, "2026-10-31T15:00:00+00:00"), // yesterday
                entry(1, "2026-10-28T15:00:00+00:00"),
            ),
            zone,
            today,
        )
        val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LocalDate.of(2026, 10, 28))
        assertEquals(listOf("Today", "Yesterday", medium), groups.map { it.label })
        assertEquals(listOf(listOf(4L), listOf(3L, 2L), listOf(1L)), groups.map { g -> g.entries.map { it.playId } })
    }

    @Test
    fun rowLabels() {
        assertEquals("👍 8", rowLabel(entry(1, rating = RatingDto(liked = true, satisfaction = 8))))
        assertEquals("👎 3", rowLabel(entry(1, rating = RatingDto(liked = false, satisfaction = 3))))
        assertEquals("👍", rowLabel(entry(1, rating = RatingDto(liked = true))))
        assertEquals("skipped 1:15", rowLabel(entry(1, endReason = "skipped", listened = 75.4)))
        assertEquals("Rate?", rowLabel(entry(1, endReason = "finished")))
    }

    @Test
    fun loadsFiftyThenMoreNearTheEnd() = runTest {
        val vm = vm()
        vm.start()
        awaitUntil { vm.uiState.value.entries.size == 50 }
        vm.loadMore()
        awaitUntil { vm.uiState.value.entries.size == 100 }
        vm.loadMore()
        awaitUntil { vm.uiState.value.endReached }
        assertEquals(120, vm.uiState.value.entries.size)
        assertEquals((1L..120L).toList(), vm.uiState.value.entries.map { it.playId })
        assertEquals(listOf("0", "50", "100"), pages(false).map { it.url.parameters["offset"] })
        vm.loadMore()
        settle()
        assertEquals(3, pages(false).size) // nothing more once the end is reached
    }

    @Test
    fun unratedFilterQueriesUnratedAndCounts() = runTest {
        val vm = vm()
        vm.start()
        awaitUntil { vm.uiState.value.unratedCount == 7 }
        assertEquals("Unrated (7)", unratedChipLabel(7))
        vm.setUnratedOnly(true)
        awaitUntil { vm.uiState.value.unratedOnly && vm.uiState.value.entries.size == 7 }
        assertTrue(pages(true).isNotEmpty())
        assertTrue(vm.uiState.value.endReached)
    }

    @Test
    fun unratedCountCaps() {
        assertEquals("Unrated", unratedChipLabel(null))
        assertEquals("Unrated (200+)", unratedChipLabel(200))
    }

    @Test
    fun fullPageOfDuplicatesStillAdvances() = runTest {
        // New plays shifted every offset: the second page repeats the first one exactly.
        server.ok("GET", "/api/history") { req ->
            val unrated = req.url.parameters["unrated"] == "true"
            val offset = req.url.parameters["offset"]!!.toInt()
            val ids = when {
                unrated -> emptyList()
                offset == 0 || offset == 50 -> (1L..50L).toList()
                offset == 100 -> (51L..60L).toList()
                else -> emptyList()
            }
            """{"entries":[${ids.joinToString(",") { entryJson(it) }}]}"""
        }
        val vm = vm()
        vm.start()
        awaitUntil { vm.uiState.value.entries.size == 50 }
        vm.loadMore()
        awaitUntil { !vm.uiState.value.loadingMore && pages(false).size == 2 }
        assertEquals(50, vm.uiState.value.entries.size)
        assertFalse(vm.uiState.value.endReached)
        vm.loadMore()
        awaitUntil { vm.uiState.value.endReached }
        assertEquals(listOf("0", "50", "100"), pages(false).map { it.url.parameters["offset"] })
        assertEquals((1L..60L).toList(), vm.uiState.value.entries.map { it.playId })
    }

    @Test
    fun unreachableSetsFlag() = runTest {
        server.down = true
        val vm = vm()
        vm.start()
        awaitUntil { vm.uiState.value.unreachable }
        assertFalse(vm.uiState.value.loading)
        server.down = false
        vm.refresh()
        awaitUntil { !vm.uiState.value.unreachable && vm.uiState.value.entries.isNotEmpty() }
    }

    @Test
    fun playAgainQueuesNextThenPlaysIt() = runTest {
        server.ok("POST", "/api/queue") { queueItemJson(9, source = "user") }
        server.ok("POST", "/api/player/play") {
            """{"state":"playing","item":${queueItemJson(9)},"position_s":0.0,"duration_s":600.0,"output_device":"Echo"}"""
        }
        val published = mutableListOf<Long?>()
        val vm = HistoryViewModel(
            server.api(), CoroutineScope(StandardTestDispatcher(testScheduler)),
            onPlayed = { published += it.item?.id },
        )
        vm.playAgain(entry(1))
        awaitUntil { vm.uiState.value.notice == NOTICE_PLAYING_AGAIN }
        assertEquals("""{"video_id":"v1","where":"next"}""", server.calls("POST", "/api/queue").single().bodyText())
        assertEquals("""{"queue_item_id":9}""", server.calls("POST", "/api/player/play").single().bodyText())
        assertEquals(listOf<Long?>(9), published)
    }

    @Test
    fun playAgainEnqueueFailureShowsErrorAndDoesNotPlay() = runTest {
        server.reply("POST", "/api/queue") { Reply(404, FakeServer.err("video not found")) }
        val vm = vm()
        vm.playAgain(entry(1))
        awaitUntil { vm.uiState.value.error != null }
        assertEquals("video not found", vm.uiState.value.error)
        assertTrue(server.calls("POST", "/api/player/play").isEmpty())
    }
}
