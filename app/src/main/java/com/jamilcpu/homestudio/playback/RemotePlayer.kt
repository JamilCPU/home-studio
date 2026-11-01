package com.jamilcpu.homestudio.playback

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MS_PER_S = 1_000.0
private const val US_PER_S = 1_000_000.0

/**
 * A Media3 player that mirrors the Pi's playback and forwards every control to the server.
 * It never plays audio locally.
 *
 * State comes from [repo]'s `now`; each change calls [invalidateState]. [scope] must run on
 * [looper]'s thread (the main thread in the app), because Media3 players are single-threaded.
 */
@OptIn(UnstableApi::class)
class RemotePlayer(
    private val repo: NowPlayingRepository,
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    looper: Looper = Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    private val mirror: Job = scope.launch { repo.now.collect { invalidateState() } }

    override fun getState(): State = stateFor(repo.now.value)

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val playing = repo.now.value?.state == STATE_PLAYING
        // toggle flips the Pi (and starts the queue when idle), so only send it for a real change.
        if (playWhenReady == playing) return Futures.immediateVoidFuture()
        return forward { api.toggle() }
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> forward { api.next() }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> forward { api.previous() }
            else -> if (positionMs == C.TIME_UNSET) {
                Futures.immediateVoidFuture()
            } else {
                forward { api.seek(positionMs.coerceAtLeast(0L) / MS_PER_S) }
            }
        }

    override fun handleStop(): ListenableFuture<*> = forward { api.stop() }

    override fun handleRelease(): ListenableFuture<*> {
        mirror.cancel()
        return Futures.immediateVoidFuture()
    }

    /** The Media3 state for one /api/now answer; nothing known or idle is [Player.STATE_IDLE]. */
    private fun stateFor(now: NowPlayingDto?): State {
        val builder = State.Builder().setAvailableCommands(COMMANDS)
        val item = now?.item
        if (now == null || item == null || now.state == STATE_IDLE) {
            return builder
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                .build()
        }
        val playing = now.state == STATE_PLAYING
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.channel)
            .setArtworkUri(item.thumbnailUrl?.let(Uri::parse))
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(item.videoId)
            .setMediaMetadata(metadata)
            .build()
        val durationS = now.durationS ?: item.durationS?.toDouble()
        val data = MediaItemData.Builder(item.id)
            .setMediaItem(mediaItem)
            .setDurationUs(durationS?.let { (it * US_PER_S).toLong() } ?: C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
        val positionMs = (now.positionS * MS_PER_S).toLong()
        return builder
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaylist(listOf(data))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(PositionSupplier.getExtrapolating(positionMs, if (playing) 1f else 0f))
            .build()
    }

    /** Runs a player call; its answer is published at once. The future completes either way. */
    private fun forward(call: suspend () -> ApiResult<NowPlayingDto>): ListenableFuture<*> {
        val done = SettableFuture.create<Unit>()
        scope.launch {
            try {
                (call() as? ApiResult.Ok)?.let { repo.publish(it.value) }
            } finally {
                done.set(Unit)
            }
        }
        return done
    }

    companion object {
        private const val STATE_PLAYING = "playing"
        private const val STATE_IDLE = "idle"

        /** Play/pause, seek in the item, next/previous and stop, plus what a notification needs to read. */
        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
