package com.jamilcpu.homestudio

import com.jamilcpu.homestudio.data.api.HomeStudioApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.net.ConnectException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A scripted reply: HTTP status plus the raw response text. */
data class Reply(val status: Int, val text: String)

/**
 * Scripted Home Studio server for tests. Routes are "METHOD /path" (no query); unknown routes
 * answer 404. Set [down] to make every call fail like an unreachable Pi.
 */
class FakeServer {
    @Volatile
    var down = false

    private val routes = ConcurrentHashMap<String, (HttpRequestData) -> Reply>()

    /** Every request, safe to read while polling adds more (MockEngine's own history is not). */
    private val received = CopyOnWriteArrayList<HttpRequestData>()

    val engine = MockEngine { request ->
        received += request
        if (down) throw ConnectException("refused")
        val handler = routes["${request.method.value} ${request.url.encodedPath}"]
        val reply = handler?.invoke(request) ?: Reply(404, err("not found"))
        respond(reply.text, HttpStatusCode.fromValue(reply.status), JSON_HEADERS)
    }

    fun api(): HomeStudioApi = HomeStudioApi({ "http://pi.test:8765" }, engine)

    /** Answers [method] [path] with `{"ok":true,"data":<data()>}`. */
    fun ok(method: String, path: String, data: (HttpRequestData) -> String) {
        routes["$method $path"] = { Reply(200, ok(data(it))) }
    }

    fun reply(method: String, path: String, handler: (HttpRequestData) -> Reply) {
        routes["$method $path"] = handler
    }

    fun calls(method: String, path: String): List<HttpRequestData> =
        received.filter { it.method.value == method && it.url.encodedPath == path }

    companion object {
        private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

        fun ok(data: String) = """{"ok":true,"data":$data,"error":null}"""

        fun err(message: String) = """{"ok":false,"data":null,"error":"$message"}"""
    }
}

fun HttpRequestData.bodyText(): String = (body as TextContent).text

/** A QueueItemOut JSON object. */
fun queueItemJson(id: Long, title: String = "V$id", source: String = "agent", durationS: Int = 600) =
    """{"id":$id,"video_id":"vid$id","title":"$title","channel":"C","thumbnail_url":null,"duration_s":$durationS,"source":"$source","reason":null,"chapters":[]}"""
