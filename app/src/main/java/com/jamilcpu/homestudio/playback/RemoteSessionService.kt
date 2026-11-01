package com.jamilcpu.homestudio.playback

import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.jamilcpu.homestudio.HomeStudioApp
import com.jamilcpu.homestudio.data.NowPlayingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the media session behind the notification, lock screen and Bluetooth controls. Its player
 * ([RemotePlayer]) only mirrors the Pi. Keeps the shared [NowPlayingRepository] polling while it
 * lives, and stops itself once the Pi has been idle for [IDLE_STOP_MS].
 *
 * Without the POST_NOTIFICATIONS permission the session still works for lock-screen and Bluetooth
 * controls; Android just shows no notification.
 */
@OptIn(UnstableApi::class)
class RemoteSessionService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: NowPlayingRepository
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as HomeStudioApp).container
        repo = container.nowPlaying
        repo.acquire()
        val quickRate = QuickRate(container.api) { repo.now.value?.item?.videoId }
        val built = MediaSession.Builder(this, RemotePlayer(repo, container.api, scope))
            .setCallback(Callback(quickRate))
            .setCustomLayout(QuickRate.layout())
            .setSessionActivity(RateIntent.pendingIntent(this))
            .build()
        session = built
        addSession(built)
        scope.launch {
            stopWhenIdle(repo.now, IDLE_STOP_MS) {
                Log.i(TAG, "Pi idle for ${IDLE_STOP_MS / 60_000} min; stopping the media session")
                stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        scope.cancel()
        repo.release()
        super.onDestroy()
    }

    /** Grants the 👍/👎 commands to every controller (the notification's included) and runs them. */
    private inner class Callback(private val quickRate: QuickRate) : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(QuickRate.LIKE)
                .add(QuickRate.DISLIKE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val action = customCommand.customAction
            if (action != QuickRate.LIKE_ACTION && action != QuickRate.DISLIKE_ACTION) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            val result = SettableFuture.create<SessionResult>()
            scope.launch {
                var code = SessionResult.RESULT_ERROR_UNKNOWN
                try {
                    if (quickRate.handle(action)) code = SessionResult.RESULT_SUCCESS
                } finally {
                    result.set(SessionResult(code)) // completes even when the service is going away
                }
            }
            return result
        }
    }

    private companion object {
        const val TAG = "RemoteSessionService"
    }
}
