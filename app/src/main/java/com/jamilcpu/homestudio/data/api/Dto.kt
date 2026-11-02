package com.jamilcpu.homestudio.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON configuration: the server may add fields the app does not know yet. */
val HomeStudioJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

@Serializable
data class Envelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: String? = null,
)

// ---- Shared shapes ----

@Serializable
data class ChapterDto(
    val title: String,
    @SerialName("start_s") val startS: Double,
)

@Serializable
data class VideoDto(
    @SerialName("video_id") val videoId: String,
    val title: String,
    val channel: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration_s") val durationS: Int? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class QueueItemDto(
    val id: Long,
    @SerialName("video_id") val videoId: String,
    val title: String,
    val channel: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration_s") val durationS: Int? = null,
    /** "agent", "user", "morning" or "fallback". */
    val source: String,
    val reason: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class NowPlayingDto(
    /** "playing", "paused" or "idle". */
    val state: String,
    val item: QueueItemDto? = null,
    @SerialName("position_s") val positionS: Double = 0.0,
    @SerialName("duration_s") val durationS: Double? = null,
    @SerialName("output_device") val outputDevice: String? = null,
)

@Serializable
data class QueueDto(
    val items: List<QueueItemDto>,
    @SerialName("remaining_s") val remainingS: Int = 0,
)

@Serializable
data class QueueItemsDto(val items: List<QueueItemDto>)

@Serializable
data class RemovedDto(val removed: Int)

@Serializable
data class SearchResultsDto(val results: List<VideoDto>)

@Serializable
data class RatingDto(
    val liked: Boolean? = null,
    val satisfaction: Int? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class HistoryEntryDto(
    @SerialName("play_id") val playId: Long,
    val video: VideoDto,
    @SerialName("started_at") val startedAt: String,
    @SerialName("seconds_listened") val secondsListened: Double? = null,
    /** "finished", "skipped", "stopped", "error" or null while still playing. */
    @SerialName("end_reason") val endReason: String? = null,
    val source: String? = null,
    val rating: RatingDto? = null,
)

@Serializable
data class HistoryDto(val entries: List<HistoryEntryDto>)

@Serializable
data class TasteDto(
    val summary: String = "",
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class IdDto(val id: Long)

@Serializable
data class AgentRequestDto(
    val id: Long,
    val prompt: String,
    /** "user" or "morning". */
    val kind: String = "user",
    /** "now" or "end". */
    val play: String = "end",
    /** "pending", "running", "done" or "failed". */
    val status: String,
    val summary: String? = null,
    val error: String? = null,
    val items: List<QueueItemDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
)

@Serializable
data class AgentRequestsDto(val requests: List<AgentRequestDto>)

@Serializable
data class MorningOutcomeDto(
    val date: String,
    val built: Boolean = false,
    val fallback: Boolean = false,
    val started: Boolean = false,
    @SerialName("request_id") val requestId: Long? = null,
    val status: String? = null,
)

@Serializable
data class MorningDto(
    val enabled: Boolean,
    @SerialName("wake_time") val wakeTime: String,
    /** Weekdays, 0 = Monday ... 6 = Sunday. */
    val days: List<Int>,
    val topics: List<String>,
    val count: Int,
    @SerialName("build_lead_min") val buildLeadMin: Int = 5,
    /** Read-only. */
    @SerialName("next_build_at") val nextBuildAt: String? = null,
    /** Read-only. */
    @SerialName("last_outcome") val lastOutcome: MorningOutcomeDto? = null,
)

@Serializable
data class MorningTestDto(@SerialName("request_id") val requestId: Long)

@Serializable
data class StatusEventDto(
    val component: String,
    /** "info", "warn" or "error". */
    val level: String,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class StatusDto(
    val player: String,
    val bluetooth: String,
    val cookies: String,
    val claude: String,
    /** "ok" or "broken". */
    val source: String = "ok",
    @SerialName("ytdlp_version") val ytdlpVersion: String? = null,
    @SerialName("quota_used_today") val quotaUsedToday: Int = 0,
    @SerialName("quota_ceiling") val quotaCeiling: Int = 0,
    val events: List<StatusEventDto> = emptyList(),
)

// ---- Request bodies ----

@Serializable
internal data class SeekBody(@SerialName("position_s") val positionS: Double)

@Serializable
internal data class SkipBody(@SerialName("delta_s") val deltaS: Double)

@Serializable
internal data class PlayBody(@SerialName("queue_item_id") val queueItemId: Long)

@Serializable
internal data class EnqueueBody(
    @SerialName("video_id") val videoId: String? = null,
    val url: String? = null,
    val where: String,
)

@Serializable
internal data class ReorderBody(val ids: List<Long>)

@Serializable
internal data class RateBody(
    @SerialName("video_id") val videoId: String,
    val liked: Boolean? = null,
    val satisfaction: Int? = null,
    val notes: String? = null,
)

@Serializable
internal data class CorrectionBody(val text: String)

@Serializable
internal data class TraitBody(val kind: String, val text: String)

@Serializable
internal data class AgentBody(val prompt: String, val play: String)

@Serializable
internal data class MorningUpdateBody(
    val enabled: Boolean,
    @SerialName("wake_time") val wakeTime: String,
    val days: List<Int>,
    val topics: List<String>,
    val count: Int,
    @SerialName("build_lead_min") val buildLeadMin: Int,
)
