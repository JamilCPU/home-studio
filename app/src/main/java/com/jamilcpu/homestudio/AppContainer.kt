package com.jamilcpu.homestudio

import android.content.Context
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.Settings
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.ui.TabRequests
import com.jamilcpu.homestudio.ui.rate.RateSheetController
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * Manual dependency holder, created once in [HomeStudioApp.onCreate]. Tests pass their own
 * [engine] and a fixed [serverUrlOverride] instead of the saved setting.
 */
class AppContainer(
    context: Context,
    engine: HttpClientEngine = OkHttp.create(),
    serverUrlOverride: StateFlow<String?>? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: Settings = Settings(context)

    /** Latest saved server URL, kept hot so [api] can read it synchronously on each call. */
    val serverUrl: StateFlow<String?> = serverUrlOverride
        // One small blocking read at startup so the first API call never sees a stale null.
        ?: settings.serverUrl.stateIn(
            scope,
            SharingStarted.Eagerly,
            runBlocking { settings.serverUrl.first() },
        )

    val api: HomeStudioApi = HomeStudioApi(baseUrl = { serverUrl.value }, engine = engine)

    /** Shared by the Now Playing screen and, later, the notification service. */
    val nowPlaying: NowPlayingRepository = NowPlayingRepository(api, scope)

    /** The shell's Rate sheet; the notification tap opens it through here too. */
    val rateSheet: RateSheetController = RateSheetController()

    /** Tab switches asked for from outside the shell (the notification tap). */
    val tabRequests: TabRequests = TabRequests()
}
