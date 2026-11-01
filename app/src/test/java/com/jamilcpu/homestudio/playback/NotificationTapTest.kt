package com.jamilcpu.homestudio.playback

import android.content.Intent
import com.jamilcpu.homestudio.HomeStudioApp
import com.jamilcpu.homestudio.MainActivity
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import com.jamilcpu.homestudio.data.api.QueueItemDto
import com.jamilcpu.homestudio.ui.Routes
import com.jamilcpu.homestudio.ui.rate.RateTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationTapTest {
    private val app: HomeStudioApp get() = RuntimeEnvironment.getApplication() as HomeStudioApp

    private val playing = NowPlayingDto(
        state = "playing",
        item = QueueItemDto(id = 4, videoId = "abc123", title = "How GPUs Work", source = "agent"),
    )

    @Test
    fun rateIntentExtraOpensRateSheetOnLaunch() {
        val container = app.container
        container.nowPlaying.publish(playing)
        Robolectric.buildActivity(MainActivity::class.java, RateIntent.forNotification(app)).setup()
        assertEquals(RateTarget("abc123", "How GPUs Work"), container.rateSheet.target.value)
        assertEquals(Routes.NOW, container.tabRequests.requested.value)
    }

    @Test
    fun rateIntentExtraOpensRateSheetOnNewIntent() {
        val container = app.container
        container.nowPlaying.publish(playing)
        val activity = Robolectric.buildActivity(MainActivity::class.java, Intent(app, MainActivity::class.java)).setup()
        assertNull(container.rateSheet.target.value) // a plain launch opens nothing
        activity.newIntent(RateIntent.forNotification(app))
        assertEquals(RateTarget("abc123", "How GPUs Work"), container.rateSheet.target.value)
    }

    @Test
    fun nothingPlayingOpensNoSheet() {
        Robolectric.buildActivity(MainActivity::class.java, RateIntent.forNotification(app)).setup()
        assertNull(app.container.rateSheet.target.value)
    }

    @Test
    fun startsSessionServiceOncePlaybackIsSeen() {
        Robolectric.buildActivity(MainActivity::class.java, Intent(app, MainActivity::class.java)).setup()
        assertNull(shadowOf(app).peekNextStartedService()) // idle so far
        app.container.nowPlaying.publish(playing)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(
            RemoteSessionService::class.java.name,
            shadowOf(app).nextStartedService?.component?.className,
        )
    }
}
