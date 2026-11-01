package com.jamilcpu.homestudio.data.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.net.ConnectException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStudioApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, jsonHeaders)

    private fun ok(data: String) = """{"ok":true,"data":$data,"error":null}"""

    private fun err(message: String) = """{"ok":false,"data":null,"error":"$message"}"""

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun HttpRequestData.fullPath(): String = url.encodedPath

    private class Fixture(val engine: MockEngine, val api: HomeStudioApi) {
        val last: HttpRequestData get() = engine.requestHistory.last()
    }

    private fun fixture(
        base: () -> String? = { "http://pi.test:8765" },
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Fixture {
        val engine = MockEngine { request -> handler(request) }
        return Fixture(engine, HomeStudioApi(base, engine))
    }

    private val queueItemJson =
        """{"id":42,"video_id":"abc","title":"How GPUs Work","channel":null,"thumbnail_url":null,""" +
            """"duration_s":1834,"source":"fallback","reason":null,"chapters":[{"title":"Intro","start_s":0.0}]}"""

    private val nowPlayingJson =
        """{"state":"playing","item":$queueItemJson,"position_s":125.4,"duration_s":1834.0,"output_device":"Echo"}"""

    // ---- Required by the brief and rulings ----

    @Test
    fun parsesNowPlayingEnvelope() = runTest {
        val f = fixture { json(ok(nowPlayingJson)) }
        val result = f.api.now()
        val np = (result as ApiResult.Ok).value
        assertEquals(HttpMethod.Get, f.last.method)
        assertEquals("/api/now", f.last.fullPath())
        assertEquals("playing", np.state)
        assertEquals(125.4, np.positionS, 0.0001)
        assertEquals(1834.0, np.durationS!!, 0.0001)
        assertEquals("Echo", np.outputDevice)
        assertEquals(42L, np.item!!.id)
        assertEquals("fallback", np.item!!.source)
        assertNull(np.item!!.channel)
        assertEquals(1834, np.item!!.durationS)
        assertEquals(listOf(ChapterDto("Intro", 0.0)), np.item!!.chapters)
    }

    @Test
    fun parsesIdleNowPlaying() = runTest {
        val f = fixture {
            json(ok("""{"state":"idle","item":null,"position_s":0.0,"duration_s":null,"output_device":"Echo"}"""))
        }
        val np = (f.api.now() as ApiResult.Ok).value
        assertEquals("idle", np.state)
        assertNull(np.item)
        assertNull(np.durationS)
    }

    @Test
    fun seekPostsPositionBody() = runTest {
        val f = fixture { json(ok(nowPlayingJson)) }
        f.api.seek(120.0)
        assertEquals(HttpMethod.Post, f.last.method)
        assertEquals("/api/player/seek", f.last.fullPath())
        assertEquals("""{"position_s":120.0}""", f.last.bodyText())
        assertEquals(ContentType.Application.Json, f.last.body.contentType?.withoutParameters())
    }

    @Test
    fun apiErrorBecomesUnreachableState() = runTest {
        val f = fixture { throw ConnectException("refused") }
        val failure = f.api.now() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Unreachable, failure.kind)
    }

    @Test
    fun unknownHostBecomesUnreachable() = runTest {
        val f = fixture { throw UnknownHostException("nope") }
        val failure = f.api.queue() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Unreachable, failure.kind)
    }

    @Test
    fun timeoutBecomesUnreachable() = runTest {
        val f = fixture { throw HttpRequestTimeoutException("http://pi.test:8765/api/now", 10_000L) }
        val failure = f.api.now() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Unreachable, failure.kind)
    }

    @Test
    fun validationErrorCarriesMessage() = runTest {
        val msg = "satisfaction: Input should be less than or equal to 10"
        val f = fixture { json(err(msg), HttpStatusCode.UnprocessableEntity) }
        val failure = f.api.rate("abc", satisfaction = 11) as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Validation, failure.kind)
        assertEquals(msg, failure.message)
    }

    @Test
    fun missingBaseUrlIsUnreachable() = runTest {
        val f = fixture(base = { null }) { json(ok(nowPlayingJson)) }
        val failure = f.api.now() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Unreachable, failure.kind)
        assertTrue(f.engine.requestHistory.isEmpty())
    }

    @Test
    fun blankBaseUrlIsUnreachable() = runTest {
        val f = fixture(base = { "  " }) { json(ok(nowPlayingJson)) }
        assertTrue(f.api.now() is ApiResult.Failure)
        assertTrue(f.engine.requestHistory.isEmpty())
    }

    // ---- Error mapping ----

    @Test
    fun serverErrorsUseEnvelopeMessage() = runTest {
        val f = fixture { json(err("player unavailable"), HttpStatusCode.ServiceUnavailable) }
        val failure = f.api.toggle() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Server, failure.kind)
        assertEquals("player unavailable", failure.message)
    }

    @Test
    fun conflictIsServerKind() = runTest {
        val f = fixture { json(err("mpv said no"), HttpStatusCode.Conflict) }
        val failure = f.api.next() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Server, failure.kind)
        assertEquals("mpv said no", failure.message)
    }

    @Test
    fun nonJsonBodyIsUnexpectedResponse() = runTest {
        val f = fixture { respond("<html>bad gateway</html>", HttpStatusCode.BadGateway) }
        val failure = f.api.now() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Server, failure.kind)
        assertEquals("unexpected response", failure.message)
    }

    @Test
    fun nonJsonBodyOn200IsUnexpectedResponse() = runTest {
        val f = fixture { respond("hello", HttpStatusCode.OK) }
        val failure = f.api.now() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Server, failure.kind)
        assertEquals("unexpected response", failure.message)
    }

    @Test
    fun okEnvelopeWithoutDataIsUnexpectedResponse() = runTest {
        val f = fixture { json("""{"ok":true,"data":null,"error":null}""") }
        val failure = f.api.now() as ApiResult.Failure
        assertEquals("unexpected response", failure.message)
    }

    @Test
    fun errorEnvelopeWithoutMessageStillFails() = runTest {
        val f = fixture { json("""{"ok":false,"data":null,"error":null}""", HttpStatusCode.InternalServerError) }
        val failure = f.api.now() as ApiResult.Failure
        assertEquals(ApiResult.Failure.Kind.Server, failure.kind)
        assertTrue(failure.message.isNotBlank())
    }

    // ---- Base URL handling ----

    @Test
    fun baseUrlIsReadPerCallAndTrailingSlashTrimmed() = runTest {
        var base: String? = "http://one.test:8765/"
        val f = fixture(base = { base }) { json(ok(nowPlayingJson)) }
        f.api.now()
        assertEquals("one.test", f.last.url.host)
        assertEquals("/api/now", f.last.fullPath())
        base = "https://two.test:9000"
        f.api.now()
        assertEquals("two.test", f.last.url.host)
        assertEquals(9000, f.last.url.port)
        assertEquals("https", f.last.url.protocol.name)
    }

    // ---- Player group ----

    @Test
    fun playerCommandsUseExpectedRoutes() = runTest {
        val f = fixture { json(ok(nowPlayingJson)) }
        f.api.toggle()
        assertEquals(HttpMethod.Post to "/api/player/toggle", f.last.method to f.last.fullPath())
        f.api.next()
        assertEquals(HttpMethod.Post to "/api/player/next", f.last.method to f.last.fullPath())
        f.api.previous()
        assertEquals(HttpMethod.Post to "/api/player/previous", f.last.method to f.last.fullPath())
        f.api.stop()
        assertEquals(HttpMethod.Post to "/api/player/stop", f.last.method to f.last.fullPath())
    }

    @Test
    fun skipPostsDeltaBody() = runTest {
        val f = fixture { json(ok(nowPlayingJson)) }
        f.api.skip(-15.0)
        assertEquals("/api/player/skip", f.last.fullPath())
        assertEquals("""{"delta_s":-15.0}""", f.last.bodyText())
    }

    // ---- Queue group ----

    @Test
    fun queueParsesItemsAndRemaining() = runTest {
        val f = fixture { json(ok("""{"items":[$queueItemJson],"remaining_s":1834}""")) }
        val q = (f.api.queue() as ApiResult.Ok).value
        assertEquals(1, q.items.size)
        assertEquals(1834, q.remainingS)
        assertEquals("/api/queue", f.last.fullPath())
    }

    @Test
    fun enqueueByUrlSendsOnlyUrlAndWhere() = runTest {
        val f = fixture { json(ok(queueItemJson), HttpStatusCode.Created) }
        val result = f.api.enqueue(url = "https://youtu.be/abc", where = QueuePlacement.Next)
        assertEquals(42L, (result as ApiResult.Ok).value.id)
        assertEquals(HttpMethod.Post, f.last.method)
        assertEquals("/api/queue", f.last.fullPath())
        assertEquals("""{"url":"https://youtu.be/abc","where":"next"}""", f.last.bodyText())
    }

    @Test
    fun enqueueByVideoIdAtEnd() = runTest {
        val f = fixture { json(ok(queueItemJson), HttpStatusCode.Created) }
        f.api.enqueue(videoId = "abc", where = QueuePlacement.End)
        assertEquals("""{"video_id":"abc","where":"end"}""", f.last.bodyText())
    }

    @Test
    fun removeAndClearQueue() = runTest {
        val f = fixture { json(ok("""{"removed":1}""")) }
        val removed = (f.api.removeQueueItem(42) as ApiResult.Ok).value
        assertEquals(1, removed.removed)
        assertEquals(HttpMethod.Delete to "/api/queue/42", f.last.method to f.last.fullPath())
        f.api.clearQueue()
        assertEquals(HttpMethod.Delete to "/api/queue", f.last.method to f.last.fullPath())
    }

    @Test
    fun reorderPutsIds() = runTest {
        val f = fixture { json(ok("""{"items":[$queueItemJson]}""")) }
        f.api.reorder(listOf(44, 42, 43))
        assertEquals(HttpMethod.Put to "/api/queue/order", f.last.method to f.last.fullPath())
        assertEquals("""{"ids":[44,42,43]}""", f.last.bodyText())
    }

    @Test
    fun searchSendsEncodedQuery() = runTest {
        val f = fixture {
            json(ok("""{"results":[{"video_id":"abc","title":"T","channel":null,"thumbnail_url":null,"duration_s":null,"chapters":[]}]}"""))
        }
        val r = (f.api.search("gpu memory & more") as ApiResult.Ok).value
        assertEquals(1, r.results.size)
        assertNull(r.results[0].durationS)
        assertEquals("/api/search", f.last.fullPath())
        assertEquals("gpu memory & more", f.last.url.parameters["q"])
    }

    // ---- Ratings and history ----

    @Test
    fun rateOmitsUnsetFields() = runTest {
        val f = fixture { json(ok("""{"id":12}"""), HttpStatusCode.Created) }
        val r = f.api.rate("abc", liked = true, satisfaction = 8)
        assertEquals(12L, (r as ApiResult.Ok).value.id)
        assertEquals(HttpMethod.Post to "/api/ratings", f.last.method to f.last.fullPath())
        assertEquals("""{"video_id":"abc","liked":true,"satisfaction":8}""", f.last.bodyText())
    }

    @Test
    fun historyParsesEntriesAndSendsQuery() = runTest {
        val f = fixture {
            json(
                ok(
                    """{"entries":[{"play_id":17,"video":{"video_id":"abc","title":"T","channel":"C","thumbnail_url":null,"duration_s":1834,"chapters":[]},""" +
                        """"started_at":"2026-11-01T14:03:22+00:00","seconds_listened":null,"end_reason":null,"source":null,""" +
                        """"rating":{"liked":null,"satisfaction":8,"notes":null,"created_at":"2026-11-01T14:20:00+00:00"}}]}""",
                ),
            )
        }
        val h = (f.api.history(unratedOnly = true, limit = 20, offset = 40) as ApiResult.Ok).value
        val e = h.entries.single()
        assertEquals(17L, e.playId)
        assertNull(e.secondsListened)
        assertNull(e.endReason)
        assertNull(e.rating!!.liked)
        assertEquals(8, e.rating!!.satisfaction)
        assertEquals("/api/history", f.last.fullPath())
        assertEquals("true", f.last.url.parameters["unrated"])
        assertEquals("20", f.last.url.parameters["limit"])
        assertEquals("40", f.last.url.parameters["offset"])
    }

    // ---- Taste ----

    @Test
    fun tasteNullDataIsOkNull() = runTest {
        val f = fixture { json(ok("null")) }
        val r = f.api.taste()
        assertTrue(r is ApiResult.Ok)
        assertNull((r as ApiResult.Ok).value)
    }

    @Test
    fun tasteParsesProfile() = runTest {
        val f = fixture {
            json(ok("""{"summary":"S","likes":["a"],"dislikes":["b"],"updated_at":"2026-11-01T09:00:00+00:00"}"""))
        }
        val t = (f.api.taste() as ApiResult.Ok).value!!
        assertEquals(listOf("a"), t.likes)
        assertEquals(listOf("b"), t.dislikes)
    }

    @Test
    fun correctTastePostsText() = runTest {
        val f = fixture { json(ok("""{"id":5}"""), HttpStatusCode.Accepted) }
        val r = f.api.correctTaste("less politics")
        assertEquals(5L, (r as ApiResult.Ok).value.id)
        assertEquals(HttpMethod.Post to "/api/taste/corrections", f.last.method to f.last.fullPath())
        assertEquals("""{"text":"less politics"}""", f.last.bodyText())
    }

    @Test
    fun removeTraitSendsDeleteWithBody() = runTest {
        val f = fixture {
            json(ok("""{"summary":"S","likes":[],"dislikes":["b"],"updated_at":"2026-11-01T15:00:00+00:00"}"""))
        }
        f.api.removeTrait("likes", "hardware teardowns")
        assertEquals(HttpMethod.Delete to "/api/taste/traits", f.last.method to f.last.fullPath())
        assertEquals("""{"kind":"likes","text":"hardware teardowns"}""", f.last.bodyText())
    }

    // ---- Agent ----

    private val agentJson =
        """{"id":9,"prompt":"p","kind":"morning","play":"end","status":"failed","summary":null,"error":"boom",""" +
            """"items":[$queueItemJson],"created_at":"2026-11-01T14:00:00+00:00","finished_at":null}"""

    @Test
    fun submitAgentPostsPromptAndPlay() = runTest {
        val f = fixture { json(ok("""{"id":9}"""), HttpStatusCode.Accepted) }
        val r = f.api.submitAgent("something about GPUs", AgentPlay.Now)
        assertEquals(9L, (r as ApiResult.Ok).value.id)
        assertEquals(HttpMethod.Post to "/api/agent/requests", f.last.method to f.last.fullPath())
        assertEquals("""{"prompt":"something about GPUs","play":"now"}""", f.last.bodyText())
    }

    @Test
    fun agentRequestParsesKindPlayAndError() = runTest {
        val f = fixture { json(ok(agentJson)) }
        val a = (f.api.agentRequest(9) as ApiResult.Ok).value
        assertEquals("/api/agent/requests/9", f.last.fullPath())
        assertEquals("morning", a.kind)
        assertEquals("end", a.play)
        assertEquals("failed", a.status)
        assertEquals("boom", a.error)
        assertNull(a.finishedAt)
        assertEquals(1, a.items.size)
    }

    @Test
    fun agentRequestsSendsLimit() = runTest {
        val f = fixture { json(ok("""{"requests":[$agentJson]}""")) }
        val list = (f.api.agentRequests(10) as ApiResult.Ok).value
        assertEquals(1, list.requests.size)
        assertEquals("/api/agent/requests", f.last.fullPath())
        assertEquals("10", f.last.url.parameters["limit"])
    }

    // ---- Morning ----

    private val morningJson =
        """{"enabled":true,"wake_time":"07:45","days":[0,1,2],"topics":["AI"],"count":3,"build_lead_min":10,""" +
            """"next_build_at":"2026-11-02T07:35:00-06:00","last_outcome":{"date":"2026-11-01","built":true,"fallback":false,""" +
            """"started":true,"request_id":14,"status":"done"}}"""

    @Test
    fun morningParsesBuildLeadAndOutcome() = runTest {
        val f = fixture { json(ok(morningJson)) }
        val m = (f.api.morning() as ApiResult.Ok).value
        assertEquals("/api/morning", f.last.fullPath())
        assertEquals(10, m.buildLeadMin)
        assertEquals("2026-11-02T07:35:00-06:00", m.nextBuildAt)
        assertEquals(14L, m.lastOutcome!!.requestId)
        assertTrue(m.lastOutcome!!.started)
    }

    @Test
    fun morningToleratesNullOutcomeAndMissingFields() = runTest {
        val f = fixture {
            json(ok("""{"enabled":false,"wake_time":"07:45","days":[],"topics":["AI"],"count":3,"next_build_at":null,"last_outcome":null}"""))
        }
        val m = (f.api.morning() as ApiResult.Ok).value
        assertNull(m.nextBuildAt)
        assertNull(m.lastOutcome)
        assertEquals(5, m.buildLeadMin)
    }

    @Test
    fun saveMorningPutsOnlyEditableFields() = runTest {
        val f = fixture { json(ok(morningJson)) }
        val dto = MorningDto(
            enabled = true,
            wakeTime = "06:30",
            days = listOf(0, 1),
            topics = listOf("AI", "hardware"),
            count = 2,
            buildLeadMin = 7,
            nextBuildAt = "2026-11-02T06:23:00-06:00",
            lastOutcome = MorningOutcomeDto(date = "2026-11-01", built = true, fallback = false, started = true),
        )
        f.api.saveMorning(dto)
        assertEquals(HttpMethod.Put to "/api/morning", f.last.method to f.last.fullPath())
        assertEquals(
            """{"enabled":true,"wake_time":"06:30","days":[0,1],"topics":["AI","hardware"],"count":2,"build_lead_min":7}""",
            f.last.bodyText(),
        )
    }

    @Test
    fun testMorningPostsAndReturnsRequestId() = runTest {
        val f = fixture { json(ok("""{"request_id":14}"""), HttpStatusCode.Accepted) }
        val r = f.api.testMorning()
        assertEquals(14L, (r as ApiResult.Ok).value.requestId)
        assertEquals(HttpMethod.Post to "/api/morning/test", f.last.method to f.last.fullPath())
    }

    // ---- Status ----

    @Test
    fun statusParsesSourceAndEvents() = runTest {
        val f = fixture {
            json(
                ok(
                    """{"player":"playing","bluetooth":"connected","cookies":"ok","claude":"ok","source":"broken",""" +
                        """"ytdlp_version":"2026.03.17","quota_used_today":300,"quota_ceiling":9500,"events":[""" +
                        """{"component":"bluetooth","level":"info","message":"m","created_at":"2026-11-01T07:40:00+00:00"}]}""",
                ),
            )
        }
        val s = (f.api.status() as ApiResult.Ok).value
        assertEquals("/api/status", f.last.fullPath())
        assertEquals("broken", s.source)
        assertEquals(300, s.quotaUsedToday)
        assertEquals("info", s.events.single().level)
    }
}
