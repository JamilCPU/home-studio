package com.jamilcpu.homestudio.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jamilcpu.homestudio.MainActivity
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import com.jamilcpu.homestudio.ui.Routes
import com.jamilcpu.homestudio.ui.TabRequests
import com.jamilcpu.homestudio.ui.rate.RateSheetController

/** The notification tap: opens the app on Now Playing with the Rate sheet for the current video. */
object RateIntent {
    const val EXTRA_OPEN_RATE = "com.jamilcpu.homestudio.extra.OPEN_RATE"

    private const val REQUEST_CODE = 7

    fun forNotification(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_RATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        forNotification(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Acts on [intent] if it came from the notification: switches to Now Playing and opens the
     * Rate sheet for what is playing. The sheet itself looks up the latest rating to prefill.
     * Returns true when the intent was a Rate request.
     */
    fun handle(intent: Intent?, now: NowPlayingDto?, rateSheet: RateSheetController, tabs: TabRequests): Boolean {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RATE, false) != true) return false
        intent.removeExtra(EXTRA_OPEN_RATE) // a recreated activity must not open it again
        tabs.request(Routes.NOW)
        val item = now?.item ?: return true
        rateSheet.open(item.videoId, item.title)
        return true
    }
}
