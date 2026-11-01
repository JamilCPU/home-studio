package com.jamilcpu.homestudio.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Starts [RemoteSessionService] when the app sees the Pi leave idle. Call only while in the foreground. */
object SessionServiceStarter {
    private const val TAG = "SessionServiceStarter"

    /** Starts the service each time [now] turns non-idle; runs until cancelled. */
    suspend fun startWhenPlaybackSeen(context: Context, now: Flow<NowPlayingDto?>) {
        now.map { !it.isIdle() }
            .distinctUntilChanged()
            .filter { it }
            .collect { start(context) }
    }

    /**
     * A plain start (not a foreground start): Media3 promotes the service to the foreground itself
     * once the session reports playback, and a paused Pi must not trip the 5-second
     * startForeground deadline. Starting an already running service is a no-op.
     */
    private fun start(context: Context) {
        try {
            context.startService(Intent(context, RemoteSessionService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not start the media session service", e) // app went to the background
        }
    }
}
