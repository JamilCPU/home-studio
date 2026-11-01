package com.jamilcpu.homestudio.ui.rate

import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.Reply
import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.data.api.RatingDto
import com.jamilcpu.homestudio.settle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateViewModelTest {
    private val server = FakeServer().apply { ok("POST", "/api/ratings") { """{"id":12}""" } }

    private fun TestScope.vm(initial: RatingDto? = null) =
        RateViewModel(server.api(), CoroutineScope(StandardTestDispatcher(testScheduler)), "v1", initial)

    @Test
    fun saveDisabledWhenEmpty() = runTest {
        val vm = vm()
        assertFalse(vm.uiState.value.canSave)
        vm.save()
        settle()
        assertTrue(server.calls("POST", "/api/ratings").isEmpty())

        vm.toggleLiked(true)
        assertTrue(vm.uiState.value.canSave)
        vm.toggleLiked(true)
        assertFalse(vm.uiState.value.canSave)

        vm.toggleSatisfaction(7)
        assertTrue(vm.uiState.value.canSave)
        vm.toggleSatisfaction(7)
        assertFalse(vm.uiState.value.canSave)

        vm.onNotes("   ")
        assertFalse(vm.uiState.value.canSave)
        vm.onNotes("good")
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun ratingSendsOnlySetFields() = runTest {
        val vm = vm()
        vm.onNotes("  loved the memory section ")
        vm.save()
        awaitUntil { vm.uiState.value.saved }
        assertEquals(
            """{"video_id":"v1","notes":"loved the memory section"}""",
            server.calls("POST", "/api/ratings").single().bodyText(),
        )
    }

    @Test
    fun allFieldsSentWhenSet() = runTest {
        val vm = vm()
        vm.toggleLiked(false)
        vm.toggleSatisfaction(3)
        vm.save()
        awaitUntil { vm.uiState.value.saved }
        assertEquals(
            """{"video_id":"v1","liked":false,"satisfaction":3}""",
            server.calls("POST", "/api/ratings").single().bodyText(),
        )
    }

    @Test
    fun likedIsTriState() = runTest {
        val vm = vm()
        vm.toggleLiked(true)
        assertEquals(true, vm.uiState.value.liked)
        vm.toggleLiked(false)
        assertEquals(false, vm.uiState.value.liked)
        vm.toggleLiked(false)
        assertNull(vm.uiState.value.liked)
    }

    @Test
    fun notesCappedAt2000() = runTest {
        val vm = vm()
        vm.onNotes("x".repeat(2_500))
        assertEquals(2_000, vm.uiState.value.notes.length)
    }

    @Test
    fun prefillsTheExistingRating() = runTest {
        val vm = vm(RatingDto(liked = true, satisfaction = 8, notes = "great"))
        val s = vm.uiState.value
        assertEquals(true, s.liked)
        assertEquals(8, s.satisfaction)
        assertEquals("great", s.notes)
    }

    @Test
    fun validationMessageShownInline() = runTest {
        server.reply("POST", "/api/ratings") {
            Reply(422, FakeServer.err("satisfaction: Input should be less than or equal to 10"))
        }
        val vm = vm()
        vm.toggleSatisfaction(9)
        vm.save()
        awaitUntil { vm.uiState.value.error != null }
        assertEquals("satisfaction: Input should be less than or equal to 10", vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun unreachableSetsFlag() = runTest {
        server.down = true
        val vm = vm()
        vm.toggleLiked(true)
        vm.save()
        awaitUntil { vm.uiState.value.unreachable }
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.saving)
    }

    private fun historyWith(vararg entries: String) =
        server.ok("GET", "/api/history") { """{"entries":[${entries.joinToString(",")}]}""" }

    private fun entryJson(playId: Long, videoId: String, rating: String?) =
        """{"play_id":$playId,"video":{"video_id":"$videoId","title":"T","channel":null,"thumbnail_url":null,"duration_s":600,"chapters":[]},"started_at":"2026-11-01T14:03:22+00:00","seconds_listened":600.0,"end_reason":"finished","source":"agent","rating":$rating}"""

    /** As api.md says, every row of a video carries that video's latest rating. */
    private fun historyWithLatestLike() {
        val like = """{"liked":true,"satisfaction":7,"notes":"good one","created_at":"2026-11-01T15:00:00+00:00"}"""
        historyWith(
            entryJson(9, "other", """{"liked":false,"satisfaction":2,"notes":null,"created_at":null}"""),
            entryJson(8, "v1", like),
            entryJson(5, "v1", like),
            entryJson(2, "v1", like),
        )
    }

    @Test
    fun lookupReplacesHistorySuppliedInitial() = runTest {
        historyWithLatestLike()
        val vm = vm(RatingDto(liked = false, satisfaction = 2)) // an older row's view, say
        assertEquals(false, vm.uiState.value.liked) // shown at once
        awaitUntil { vm.uiState.value.liked == true }
        assertEquals(7, vm.uiState.value.satisfaction)
        assertEquals("good one", vm.uiState.value.notes)
    }

    @Test
    fun openingFromNowPlayingPrefillsExistingLike() = runTest {
        historyWithLatestLike()
        val vm = vm() // Now Playing knows no rating
        awaitUntil { vm.uiState.value.liked != null }
        val s = vm.uiState.value
        assertEquals(true, s.liked)
        assertEquals(7, s.satisfaction)
        assertEquals("good one", s.notes)
        val lookup = server.calls("GET", "/api/history").single()
        assertEquals("50", lookup.url.parameters["limit"])
        assertEquals("false", lookup.url.parameters["unrated"])
    }

    @Test
    fun noEarlierRatingStartsEmpty() = runTest {
        historyWith(entryJson(8, "v1", null), entryJson(3, "other", """{"liked":true}"""))
        val vm = vm()
        awaitUntil { server.calls("GET", "/api/history").isNotEmpty() }
        settle()
        assertEquals(RateUiState(), vm.uiState.value)
    }

    @Test
    fun prefillNeverOverwritesEdits() = runTest {
        historyWith(entryJson(5, "v1", """{"liked":true,"satisfaction":7}"""))
        val vm = vm()
        vm.toggleSatisfaction(3) // typed before the lookup answered
        awaitUntil { server.calls("GET", "/api/history").isNotEmpty() }
        settle()
        assertNull(vm.uiState.value.liked)
        assertEquals(3, vm.uiState.value.satisfaction)
    }
}
