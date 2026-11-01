package com.jamilcpu.homestudio.ui.now

import android.net.Uri
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SKIP_BACK_S = -15.0
private const val SKIP_FORWARD_S = 30.0

/** Plain class (no Android ViewModel base): its state is derived from [repo], so it is cheap to rebuild. */
class NowPlayingViewModel(
    private val repo: NowPlayingRepository,
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
) {
    private val drag = MutableStateFlow<Float?>(null)

    /** Position shown from the moment the finger lifts until the seek answers, so the bar never snaps back. */
    private val seekHold = MutableStateFlow<Float?>(null)
    private val local = MutableStateFlow(LocalState())

    private data class LocalState(
        val ratedVideoId: String? = null,
        val rating: Boolean? = null,
        val error: String? = null,
    )

    val uiState: StateFlow<NowPlayingUiState> =
        combine(
            combine(repo.now, repo.status, repo.reachable) { now, status, reachable -> Triple(now, status, reachable) },
            combine(drag, seekHold, local) { d, h, l -> Triple(d, h, l) },
        ) { (now, status, reachable), (d, hold, l) ->
            val item = now?.item
            NowPlayingUiState(
                item = item,
                state = now?.state ?: "idle",
                positionS = hold?.toDouble() ?: now?.positionS ?: 0.0,
                durationS = now?.durationS ?: item?.durationS?.toDouble(),
                outputDevice = now?.outputDevice,
                chapters = item?.chapters.orEmpty(),
                notices = noticesFor(status),
                unreachable = !reachable,
                dragPositionS = d,
                queueEmpty = now.isQueueEmpty(),
                quickRating = l.rating.takeIf { item != null && l.ratedVideoId == item.videoId },
                actionError = l.error,
            )
        }.stateIn(scope, SharingStarted.Eagerly, NowPlayingUiState())

    fun start() = repo.acquire()

    fun stop() = repo.release()

    fun retry() = repo.refresh()

    fun onDragStart() {
        if (drag.value == null) drag.value = uiState.value.positionS.toFloat()
    }

    fun onDrag(s: Float) {
        if (drag.value != null) drag.value = s
    }

    /** Sends exactly one seek for the released position. */
    fun onDragEnd() {
        val target = drag.value ?: return
        drag.value = null
        seekHold.value = target
        scope.launch {
            try {
                player { api.seek(target.toDouble()) }
            } finally {
                seekHold.value = null
            }
        }
    }

    fun toggle() = launchPlayer { api.toggle() }

    fun next() = launchPlayer { api.next() }

    fun previous() = launchPlayer { api.previous() }

    fun skipBack() = launchPlayer { api.skip(SKIP_BACK_S) }

    fun skipForward() = launchPlayer { api.skip(SKIP_FORWARD_S) }

    fun chapterTapped(startS: Double) = launchPlayer { api.seek(startS) }

    fun quickRate(liked: Boolean) {
        val videoId = uiState.value.item?.videoId ?: return
        scope.launch {
            when (val result = api.rate(videoId, liked = liked)) {
                is ApiResult.Ok -> local.update { LocalState(ratedVideoId = videoId, rating = liked) }
                is ApiResult.Failure -> local.update { it.copy(error = result.message) }
            }
        }
    }

    /** Link to the current video at the current second, or null when nothing is playing. */
    fun openInYoutubeUri(): Uri? {
        val state = uiState.value
        val id = state.item?.videoId ?: return null
        val t = floor(state.displayPositionS).toLong()
        return Uri.parse("https://www.youtube.com/watch?v=$id&t=${t}s")
    }

    private fun launchPlayer(call: suspend () -> ApiResult<NowPlayingDto>) {
        scope.launch { player(call) }
    }

    private suspend fun player(call: suspend () -> ApiResult<NowPlayingDto>) {
        when (val result = call()) {
            is ApiResult.Ok -> {
                repo.publish(result.value)
                local.update { it.copy(error = null) }
            }
            is ApiResult.Failure -> local.update { it.copy(error = result.message) }
        }
    }
}
