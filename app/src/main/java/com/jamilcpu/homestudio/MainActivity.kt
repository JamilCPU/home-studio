package com.jamilcpu.homestudio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jamilcpu.homestudio.playback.NotificationPermission
import com.jamilcpu.homestudio.playback.RateIntent
import com.jamilcpu.homestudio.playback.SessionServiceStarter
import com.jamilcpu.homestudio.ui.HomeStudioRoot
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as HomeStudioApp).container

    private val notificationPermission = NotificationPermission(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeStudioTheme {
                HomeStudioRoot(container)
            }
        }
        if (savedInstanceState == null) {
            openRateIfRequested(intent)
            notificationPermission.requestOnce()
        }
        // Only while visible: Android forbids starting services from the background. Holding the
        // repository here keeps it polling on every tab, not just Now Playing.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val repo = container.nowPlaying
                repo.acquire()
                try {
                    SessionServiceStarter.startWhenPlaybackSeen(this@MainActivity, repo.now)
                } finally {
                    repo.release()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRateIfRequested(intent)
    }

    private fun openRateIfRequested(intent: Intent?) {
        RateIntent.handle(intent, container.nowPlaying.now.value, container.rateSheet, container.tabRequests)
    }
}
