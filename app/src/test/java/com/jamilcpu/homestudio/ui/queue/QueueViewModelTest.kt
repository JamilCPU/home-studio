package com.jamilcpu.homestudio.ui.queue

import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.queueItemJson
import com.jamilcpu.homestudio.settle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    @Volatile
    private var queueIds: List<Long> = listOf(1, 2, 3)

    private val server = FakeServer().apply {
        ok("GET", "/api/queue") {
            """{"items":[${queueIds.joinToString(",") { queueItemJson(it) }}],"remaining_s":${queueIds.size * 600}}"""
        }
        ok("PUT", "/api/queue/order") { req ->
            val ids = Regex("\\d+").findAll(req.bodyText()).map { it.value.toLong() }.toList()
            queueIds = ids
            """{"items":[${ids.joinToString(",") { queueItemJson(it) }}]}"""
        }
        ok("DELETE", "/api/queue") { """{"removed":3}""".also { queueIds = emptyList() } }
        ok("POST", "/api/queue") { queueItemJson(9, source = "user") }
        ok("GET", "/api/search") {
            """{"results":[{"video_id":"abc","title":"GPU memory explained","channel":"C","thumbnail_url":null,"duration_s":900,"chapters":[]}]}"""
        }
    }

    private fun TestScope.vm() = QueueViewModel(server.api(), CoroutineScope(StandardTestDispatcher(testScheduler)))

    private fun TestScope.started(): QueueViewModel = vm().also {
        it.start()
        awaitUntil { it.uiState.value.loaded }
    }

    @Test
    fun reorderSendsFullIdList() = runTest {
        val vm = started()
        vm.onDragStart(1)
        vm.moveItem(1, +1)
        vm.moveItem(1, +1)
        assertEquals(listOf(2L, 3L, 1L), vm.uiState.value.items.map { it.id })
        assertTrue(server.calls("PUT", "/api/queue/order").isEmpty()) // nothing sent mid-drag
        vm.onDragEnd()
        awaitUntil { !vm.uiState.value.busy && server.calls("PUT", "/api/queue/order").isNotEmpty() }
        settle()
        assertEquals("""{"ids":[2,3,1]}""", server.calls("PUT", "/api/queue/order").single().bodyText())
        vm.stop()
    }

    @Test
    fun dropWithoutMoveSendsNothing() = runTest {
        val vm = started()
        vm.onDragStart(2)
        vm.onDragEnd()
        settle()
        assertTrue(server.calls("PUT", "/api/queue/order").isEmpty())
        vm.stop()
    }

    @Test
    fun pollDoesNotOverwriteOrderWhileDragging() = runTest {
        val vm = started()
        vm.onDragStart(3)
        vm.moveItem(3, -1)
        advanceTimeBy(QUEUE_POLL_MS + 1)
        settle()
        assertTrue(server.calls("GET", "/api/queue").size >= 2)
        assertEquals(listOf(1L, 3L, 2L), vm.uiState.value.items.map { it.id })
        vm.stop()
    }

    @Test
    fun pollsEveryFiveSecondsUntilStopped() = runTest {
        val vm = started()
        assertEquals(1, server.calls("GET", "/api/queue").size)
        advanceTimeBy(QUEUE_POLL_MS + 1)
        awaitUntil { server.calls("GET", "/api/queue").size == 2 }
        vm.stop()
        advanceTimeBy(QUEUE_POLL_MS * 3)
        settle()
        assertEquals(2, server.calls("GET", "/api/queue").size)
    }

    @Test
    fun remainingTimeFormatted() {
        assertEquals("Ends in 1h 41m, then stops", endsInLabel(6060))
        assertEquals("Ends in 41m, then stops", endsInLabel(2460))
        assertEquals("Ends in 2h 0m, then stops", endsInLabel(7200))
    }

    @Test
    fun sourceTags() {
        assertEquals("Agent", sourceLabel("agent"))
        assertEquals("You added", sourceLabel("user"))
        assertEquals("Morning", sourceLabel("morning"))
        assertEquals("Morning", sourceLabel("fallback"))
    }

    @Test
    fun recognisesYoutubeLinks() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30",
            "music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "youtu.be/dQw4w9WgXcQ?si=x",
            "https://youtube.com/shorts/dQw4w9WgXcQ",
        ).forEach { assertTrue(it, isYoutubeLink(it)) }
        listOf("gpu memory", "https://vimeo.com/123", "youtube", "https://youtube.com/").forEach {
            assertFalse(it, isYoutubeLink(it))
        }
    }

    @Test
    fun removeDeletesTheItem() = runTest {
        server.ok("DELETE", "/api/queue/2") { """{"removed":1}""".also { queueIds = listOf(1, 3) } }
        val vm = started()
        vm.remove(2)
        assertEquals(listOf(1L, 3L), vm.uiState.value.items.map { it.id }) // gone right away
        awaitUntil { server.calls("DELETE", "/api/queue/2").isNotEmpty() }
        vm.stop()
    }

    @Test
    fun clearDeletesEverything() = runTest {
        val vm = started()
        vm.clear()
        awaitUntil { server.calls("DELETE", "/api/queue").isNotEmpty() && vm.uiState.value.items.isEmpty() }
        vm.stop()
    }

    @Test
    fun searchThenTapAddsVideoIdAtEnd() = runTest {
        val vm = started()
        vm.openSearch()
        vm.onSearchQuery("gpu memory")
        vm.submitSearch()
        awaitUntil { vm.uiState.value.search?.results?.isNotEmpty() == true }
        assertEquals("gpu memory", server.calls("GET", "/api/search").single().url.parameters["q"])
        vm.addResult("abc")
        awaitUntil { vm.uiState.value.search == null }
        assertEquals("""{"video_id":"abc","where":"end"}""", server.calls("POST", "/api/queue").single().bodyText())
        vm.stop()
    }

    @Test
    fun pastedLinkEnqueuesUrl() = runTest {
        val vm = started()
        vm.openSearch()
        vm.onSearchQuery(" https://youtu.be/dQw4w9WgXcQ ")
        vm.submitSearch()
        awaitUntil { vm.uiState.value.search == null }
        assertTrue(server.calls("GET", "/api/search").isEmpty())
        assertEquals(
            """{"url":"https://youtu.be/dQw4w9WgXcQ","where":"end"}""",
            server.calls("POST", "/api/queue").single().bodyText(),
        )
        vm.stop()
    }

    @Test
    fun unreachableSetsFlagAndRetryClearsIt() = runTest {
        server.down = true
        val vm = vm()
        vm.start()
        awaitUntil { vm.uiState.value.unreachable }
        server.down = false
        vm.refresh()
        awaitUntil { !vm.uiState.value.unreachable && vm.uiState.value.loaded }
        assertNull(vm.uiState.value.error)
        vm.stop()
    }
}
