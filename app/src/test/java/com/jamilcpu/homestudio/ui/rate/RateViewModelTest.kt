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
}
