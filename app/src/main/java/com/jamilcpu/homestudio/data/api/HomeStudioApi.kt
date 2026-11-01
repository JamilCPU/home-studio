package com.jamilcpu.homestudio.data.api

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

sealed interface ApiResult<out T> {
    data class Ok<out T>(val value: T) : ApiResult<T>

    data class Failure(val message: String, val kind: Kind) : ApiResult<Nothing> {
        enum class Kind {
            /** Connection, DNS or timeout problem, or no server address configured. */
            Unreachable,

            /** The server answered with an error (or something that is not an envelope). */
            Server,

            /** HTTP 422: the request body or query failed validation. */
            Validation,
        }
    }
}

/** Where a queued video goes. */
enum class QueuePlacement(val wire: String) { Next("next"), End("end") }

/** Which side of the taste profile a trait belongs to. */
enum class TraitKind(val wire: String) { Likes("likes"), Dislikes("dislikes") }

/** How the agent's picks are played. */
enum class AgentPlay(val wire: String) { Now("now"), End("end") }

/**
 * Client for the Home Studio server (see the server repo's docs/api.md).
 *
 * [baseUrl] is read on every call so a changed setting takes effect immediately.
 */
class HomeStudioApi(
    private val baseUrl: () -> String?,
    engine: HttpClientEngine,
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(HomeStudioJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    // ---- Player ----

    suspend fun now(): ApiResult<NowPlayingDto> = call(HttpMethod.Get, "/api/now")

    suspend fun toggle(): ApiResult<NowPlayingDto> = call(HttpMethod.Post, "/api/player/toggle")

    suspend fun next(): ApiResult<NowPlayingDto> = call(HttpMethod.Post, "/api/player/next")

    suspend fun previous(): ApiResult<NowPlayingDto> = call(HttpMethod.Post, "/api/player/previous")

    suspend fun stop(): ApiResult<NowPlayingDto> = call(HttpMethod.Post, "/api/player/stop")

    suspend fun seek(positionS: Double): ApiResult<NowPlayingDto> =
        call(HttpMethod.Post, "/api/player/seek", body = encode(SeekBody(positionS)))

    suspend fun skip(deltaS: Double): ApiResult<NowPlayingDto> =
        call(HttpMethod.Post, "/api/player/skip", body = encode(SkipBody(deltaS)))

    // ---- Queue ----

    suspend fun queue(): ApiResult<QueueDto> = call(HttpMethod.Get, "/api/queue")

    suspend fun enqueue(
        videoId: String? = null,
        url: String? = null,
        where: QueuePlacement,
    ): ApiResult<QueueItemDto> =
        call(HttpMethod.Post, "/api/queue", body = encode(EnqueueBody(videoId, url, where.wire)))

    suspend fun removeQueueItem(id: Long): ApiResult<RemovedDto> =
        call(HttpMethod.Delete, "/api/queue/$id")

    suspend fun clearQueue(): ApiResult<RemovedDto> = call(HttpMethod.Delete, "/api/queue")

    suspend fun reorder(ids: List<Long>): ApiResult<QueueItemsDto> =
        call(HttpMethod.Put, "/api/queue/order", body = encode(ReorderBody(ids)))

    suspend fun search(q: String): ApiResult<SearchResultsDto> =
        call(HttpMethod.Get, "/api/search", query = listOf("q" to q))

    // ---- Ratings and history ----

    suspend fun rate(
        videoId: String,
        liked: Boolean? = null,
        satisfaction: Int? = null,
        notes: String? = null,
    ): ApiResult<IdDto> =
        call(HttpMethod.Post, "/api/ratings", body = encode(RateBody(videoId, liked, satisfaction, notes)))

    suspend fun history(
        unratedOnly: Boolean = false,
        limit: Int = 50,
        offset: Int = 0,
    ): ApiResult<HistoryDto> = call(
        HttpMethod.Get,
        "/api/history",
        query = listOf("unrated" to unratedOnly, "limit" to limit, "offset" to offset),
    )

    // ---- Taste ----

    /** Ok(null) means the server has no taste profile yet. */
    suspend fun taste(): ApiResult<TasteDto?> = call(HttpMethod.Get, "/api/taste")

    suspend fun correctTaste(text: String): ApiResult<IdDto> =
        call(HttpMethod.Post, "/api/taste/corrections", body = encode(CorrectionBody(text)))

    suspend fun removeTrait(kind: TraitKind, text: String): ApiResult<TasteDto> =
        call(HttpMethod.Delete, "/api/taste/traits", body = encode(TraitBody(kind.wire, text)))

    // ---- Agent ----

    suspend fun submitAgent(prompt: String, play: AgentPlay): ApiResult<IdDto> =
        call(HttpMethod.Post, "/api/agent/requests", body = encode(AgentBody(prompt, play.wire)))

    suspend fun agentRequest(id: Long): ApiResult<AgentRequestDto> =
        call(HttpMethod.Get, "/api/agent/requests/$id")

    suspend fun agentRequests(limit: Int = 20): ApiResult<AgentRequestsDto> =
        call(HttpMethod.Get, "/api/agent/requests", query = listOf("limit" to limit))

    // ---- Morning ----

    suspend fun morning(): ApiResult<MorningDto> = call(HttpMethod.Get, "/api/morning")

    /** Sends only the editable fields; `next_build_at` and `last_outcome` are read-only. */
    suspend fun saveMorning(dto: MorningDto): ApiResult<MorningDto> = call(
        HttpMethod.Put,
        "/api/morning",
        body = encode(
            MorningUpdateBody(
                enabled = dto.enabled,
                wakeTime = dto.wakeTime,
                days = dto.days,
                topics = dto.topics,
                count = dto.count,
                buildLeadMin = dto.buildLeadMin,
            ),
        ),
    )

    suspend fun testMorning(): ApiResult<MorningTestDto> = call(HttpMethod.Post, "/api/morning/test")

    // ---- System ----

    suspend fun status(): ApiResult<StatusDto> = call(HttpMethod.Get, "/api/status")

    /** Like [status] but against an explicit [url], for testing an address before it is saved. */
    suspend fun statusAt(url: String): ApiResult<StatusDto> =
        call(HttpMethod.Get, "/api/status", baseOverride = url)

    // ---- Plumbing ----

    private inline fun <reified B> encode(body: B): String = HomeStudioJson.encodeToString(body)

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        query: List<Pair<String, Any>> = emptyList(),
        body: String? = null,
        baseOverride: String? = null,
    ): ApiResult<T> {
        val base = (baseOverride ?: baseUrl())?.trim()?.trimEnd('/')
        if (base.isNullOrEmpty()) {
            return ApiResult.Failure(MESSAGE_NO_SERVER, ApiResult.Failure.Kind.Unreachable)
        }
        val (status, text) = try {
            val response = client.request {
                configure(method, base + path, query, body)
            }
            response.status.value to response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Request $method $path failed", e)
            return ApiResult.Failure(MESSAGE_UNREACHABLE, ApiResult.Failure.Kind.Unreachable)
        }
        return parse(status, text)
    }

    private inline fun <reified T> parse(status: Int, text: String): ApiResult<T> {
        val envelope = try {
            HomeStudioJson.decodeFromString<Envelope<T>>(text)
        } catch (e: SerializationException) {
            return unexpected()
        } catch (e: IllegalArgumentException) {
            return unexpected()
        }
        if (status !in 200..299 || !envelope.ok) {
            val message = envelope.error?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $status)"
            val kind = if (status == HTTP_UNPROCESSABLE) {
                ApiResult.Failure.Kind.Validation
            } else {
                ApiResult.Failure.Kind.Server
            }
            return ApiResult.Failure(message, kind)
        }
        val data = envelope.data
        if (data == null && null !is T) return unexpected()
        @Suppress("UNCHECKED_CAST")
        return ApiResult.Ok(data as T)
    }

    private fun unexpected() = ApiResult.Failure(MESSAGE_UNEXPECTED, ApiResult.Failure.Kind.Server)

    private companion object {
        const val TAG = "HomeStudioApi"
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val HTTP_UNPROCESSABLE = 422
        const val MESSAGE_NO_SERVER = "No server address set. Enter your Pi's address first."
        const val MESSAGE_UNREACHABLE = "Can't reach your Pi"
        const val MESSAGE_UNEXPECTED = "unexpected response"
    }
}

private fun HttpRequestBuilder.configure(
    method: HttpMethod,
    fullUrl: String,
    query: List<Pair<String, Any>>,
    body: String?,
) {
    this.method = method
    url(fullUrl)
    query.forEach { (k, v) -> parameter(k, v) }
    if (body != null) {
        contentType(ContentType.Application.Json)
        setBody(TextContent(body, ContentType.Application.Json))
    }
}
