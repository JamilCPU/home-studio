package com.jamilcpu.homestudio.ui.morning

import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.Reply
import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.data.api.MorningDto
import com.jamilcpu.homestudio.data.api.MorningOutcomeDto
import com.jamilcpu.homestudio.settle
import java.time.OffsetDateTime
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
class MorningViewModelTest {
    private val settings =
        """{"enabled":true,"wake_time":"07:45","days":[0,1,2,3,4],"topics":["AI"],"count":3,"build_lead_min":5,"next_build_at":"2026-11-02T07:40:00-06:00","last_outcome":null}"""

    private val server = FakeServer().apply {
        ok("GET", "/api/morning") { settings }
        ok("PUT", "/api/morning") { settings }
        ok("POST", "/api/morning/test") { """{"request_id":14}""" }
    }

    private fun TestScope.started(): MorningViewModel =
        MorningViewModel(server.api(), CoroutineScope(StandardTestDispatcher(testScheduler))).also {
            it.start()
            awaitUntil { it.uiState.value.draft != null }
        }

    @Test
    fun morningTimeLabelShowsMinus5() {
        assertEquals("Central Time · agent starts picking at 7:40 AM", pickingLabel("07:45", 5))
        assertTrue(pickingLabel("07:45", 5).endsWith("agent starts picking at 7:40 AM"))
    }

    @Test
    fun twelveHourFormatting() {
        assertEquals("7:45 AM", format12("07:45"))
        assertEquals("12:00 PM", format12("12:00"))
        assertEquals("12:05 AM", format12("00:05"))
        assertEquals("1:05 PM", format12("13:05"))
        assertEquals("Central Time · agent starts picking at 11:58 PM", pickingLabel("00:03", 5))
        assertEquals("Central Time · agent starts picking at 6:45 AM", pickingLabel("07:45", 60))
    }

    @Test
    fun morningRejectsEmptyTopics() = runTest {
        val vm = started()
        vm.removeTopic("AI")
        assertTrue(vm.uiState.value.draft!!.topics.isEmpty())
        vm.save()
        settle()
        assertTrue(server.calls("PUT", "/api/morning").isEmpty())
        assertEquals(MORNING_NO_TOPICS, vm.uiState.value.error)
    }

    @Test
    fun saveSendsEditedFields() = runTest {
        val vm = started()
        assertFalse(vm.uiState.value.canSave)
        vm.setWakeTime("06:30")
        vm.toggleDay(4)
        vm.toggleDay(6)
        vm.onTopicInput(" hardware ")
        vm.addTopic()
        vm.setCount(2)
        vm.setLead(10)
        assertTrue(vm.uiState.value.canSave)
        vm.save()
        awaitUntil { server.calls("PUT", "/api/morning").isNotEmpty() && !vm.uiState.value.saving }
        assertEquals(
            """{"enabled":true,"wake_time":"06:30","days":[0,1,2,3,6],"topics":["AI","hardware"],"count":2,"build_lead_min":10}""",
            server.calls("PUT", "/api/morning").single().bodyText(),
        )
        assertEquals(MORNING_SAVED, vm.uiState.value.notice)
    }

    @Test
    fun saveShowsServerValidationInline() = runTest {
        server.reply("PUT", "/api/morning") { Reply(422, FakeServer.err("count: Input should be less than or equal to 6")) }
        val vm = started()
        vm.setEnabled(false)
        vm.save()
        awaitUntil { vm.uiState.value.error != null }
        assertEquals("count: Input should be less than or equal to 6", vm.uiState.value.error)
        assertTrue(vm.uiState.value.dirty)
    }

    @Test
    fun steppersClamp() = runTest {
        val vm = started()
        vm.setCount(7)
        assertEquals(6, vm.uiState.value.draft!!.count)
        vm.setCount(0)
        assertEquals(1, vm.uiState.value.draft!!.count)
        vm.setLead(61)
        assertEquals(60, vm.uiState.value.draft!!.buildLeadMin)
        vm.setLead(0)
        assertEquals(1, vm.uiState.value.draft!!.buildLeadMin)
    }

    @Test
    fun duplicateOrBlankTopicIgnored() = runTest {
        val vm = started()
        vm.onTopicInput("ai")
        vm.addTopic()
        vm.onTopicInput("   ")
        vm.addTopic()
        assertEquals(listOf("AI"), vm.uiState.value.draft!!.topics)
    }

    @Test
    fun testNowCallsApiThenNavigates() = runTest {
        val vm = started()
        var navigated = 0
        vm.testNow { navigated++ }
        awaitUntil { navigated == 1 }
        assertEquals(1, server.calls("POST", "/api/morning/test").size)
    }

    @Test
    fun statusLineFromNextBuildAndOutcome() {
        val now = OffsetDateTime.parse("2026-11-01T20:00:00-06:00")
        val dto = MorningDto(
            enabled = true,
            wakeTime = "07:45",
            days = listOf(0),
            topics = listOf("AI"),
            count = 3,
            nextBuildAt = "2026-11-02T07:40:00-06:00",
            lastOutcome = MorningOutcomeDto(date = "2026-11-01", built = true, started = true, requestId = 14, status = "done"),
        )
        assertEquals("Next set builds 7:40 AM · Last: built ✓ started ✓", morningStatusLine(dto, now))
        assertEquals(
            "Next set builds 7:40 AM · Last: built ✗ fallback ✓ started ✗",
            morningStatusLine(dto.copy(lastOutcome = MorningOutcomeDto(date = "2026-11-01", fallback = true)), now),
        )
        assertEquals("Next set builds 7:40 AM", morningStatusLine(dto.copy(lastOutcome = null), now))
        assertEquals("No set scheduled", morningStatusLine(dto.copy(nextBuildAt = null, lastOutcome = null), now))
    }

    @Test
    fun unreachableSetsFlag() = runTest {
        server.down = true
        val vm = MorningViewModel(server.api(), CoroutineScope(StandardTestDispatcher(testScheduler)))
        vm.start()
        awaitUntil { vm.uiState.value.unreachable }
        server.down = false
        vm.start()
        awaitUntil { vm.uiState.value.draft != null && !vm.uiState.value.unreachable }
    }
}
