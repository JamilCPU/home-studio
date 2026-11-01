package com.jamilcpu.homestudio.ui.now

import com.jamilcpu.homestudio.data.api.StatusDto
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticesForTest {
    private fun status(
        bluetooth: String = "connected",
        cookies: String = "ok",
        claude: String = "ok",
        source: String = "ok",
    ) = StatusDto(player = "playing", bluetooth = bluetooth, cookies = cookies, claude = claude, source = source)

    @Test
    fun cookiesExpired() {
        assertEquals(
            listOf("YouTube sign-in expired. Re-upload cookies on the Pi."),
            noticesFor(status(cookies = "expired")),
        )
    }

    @Test
    fun bluetoothDisconnected() {
        assertEquals(
            listOf("Speaker disconnected. Reconnecting…"),
            noticesFor(status(bluetooth = "disconnected")),
        )
    }

    @Test
    fun claudeRateLimited() {
        assertEquals(
            listOf("Agent is rate-limited. Try again later."),
            noticesFor(status(claude = "rate_limited")),
        )
    }

    @Test
    fun claudeNotLoggedIn() {
        assertEquals(listOf("Agent isn't logged in on the Pi."), noticesFor(status(claude = "not_logged_in")))
    }

    @Test
    fun sourceBroken() {
        assertEquals(
            listOf("Playback source broken (YouTube changed?). The Pi updates yt-dlp nightly."),
            noticesFor(status(source = "broken")),
        )
    }

    @Test
    fun allOkUnknownAndMissingStatusGiveNoNotices() {
        assertEquals(emptyList<String>(), noticesFor(status()))
        assertEquals(
            emptyList<String>(),
            noticesFor(status(bluetooth = "unconfigured", cookies = "missing", claude = "unknown")),
        )
        assertEquals(emptyList<String>(), noticesFor(null))
    }

    @Test
    fun severalProblemsKeepFixedOrder() {
        val all = noticesFor(status("disconnected", "expired", "rate_limited", "broken"))
        assertEquals(listOf(NOTICE_COOKIES, NOTICE_BLUETOOTH, NOTICE_RATE_LIMITED, NOTICE_SOURCE_BROKEN), all)
    }
}
