package com.jamilcpu.homestudio.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.Player
import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.awaitUntil
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.data.api.HomeStudioJson
import com.jamilcpu.homestudio.data.api.NowPlayingDto
import com.jamilcpu.homestudio.settle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemotePlayerTest {
    private val thumb = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"

    private val itemJson =
        """{"id":4,"video_id":"abc123","title":"How GPUs Work","channel":"Some Channel","thumbnail_url":"$thumb","duration_s":1834,"source":"agent","reason":null,"chapters":[]}"""

    private fun nowJson(state: String, pos: Double = 451.7) =
        """{"state":"$state","item":$itemJson,"position_s":$pos,"duration_s":1834.0,"output_device":"Echo"}"""

    private val idleJson = """{"state":"idle","item":null,"position_s":0.0,"duration_s":null,"output_device":null}"""

    @Volatile
    private var serverState = "playing"

    private val server = FakeServer().apply {
        ok("GET", "/api/now") { nowJson(serverState) }
        ok("POST", "/api/player/toggle") {
            serverState = if (serverState == "playing") "paused" else "playing"
            nowJson(serverState)
        }
        ok("POST", "/api/player/seek") { nowJson(serverState, 300.0) }
        ok("POST", "/api/player/next") { nowJson(serverState, 0.0) }
        ok("POST", "/api/player/previous") { nowJson(serverState, 0.0) }
        ok("POST", "/api/player/stop") { idleJson }
        ok("POST", "/api/ratings") { """{"id":12}""" }
    }

    private fun dto(json: String): NowPlayingDto = HomeStudioJson.decodeFromString(NowPlayingDto.serializer(), json)

    private class Rig(val scope: CoroutineScope, val repo: NowPlayingRepository, val player: RemotePlayer)

    private fun TestScope.rig(): Rig {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val api = server.api()
        val repo = NowPlayingRepository(api, scope)
        val player = RemotePlayer(repo, api, scope)
        sync()
        return Rig(scope, repo, player)
    }

    private fun Rig.close() {
        player.release()
        scope.cancel()
    }

    /** Runs queued coroutines, then the main looper (where the player's future listeners land). */
    private fun TestScope.sync() {
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun TestScope.awaitPost(path: String) = awaitUntil {
        shadowOf(Looper.getMainLooper()).idle()
        server.calls("POST", path).isNotEmpty()
    }

    @Test
    fun playerStateMirrorsNowPlaying() = runTest {
        val rig = rig()
        val player = rig.player
        assertEquals(Player.STATE_IDLE, player.playbackState) // nothing known yet

        rig.repo.publish(dto(nowJson("playing")))
        sync()
        assertEquals(Player.STATE_READY, player.playbackState)
        assertTrue(player.playWhenReady)
        assertEquals("abc123", player.currentMediaItem?.mediaId)
        assertEquals("How GPUs Work", player.mediaMetadata.title.toString())
        assertEquals("Some Channel", player.mediaMetadata.artist.toString())
        assertEquals(Uri.parse(thumb), player.mediaMetadata.artworkUri)
        assertEquals(1_834_000L, player.duration)
        assertEquals(451_700L, player.currentPosition)

        rig.repo.publish(dto(nowJson("paused", pos = 500.0)))
        sync()
        assertEquals(Player.STATE_READY, player.playbackState)
        assertFalse(player.playWhenReady)
        assertEquals(500_000L, player.currentPosition)

        rig.repo.publish(dto(idleJson))
        sync()
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertEquals(0, player.mediaItemCount)

        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_STOP,
        ).forEach { assertTrue("command $it", player.isCommandAvailable(it)) }
        rig.close()
    }

    @Test
    fun seekForwardsToApi() = runTest {
        val rig = rig()
        val player = rig.player
        rig.repo.publish(dto(nowJson("playing")))
        sync()

        player.seekTo(300_000L)
        awaitPost("/api/player/seek")
        assertEquals("""{"position_s":300.0}""", server.calls("POST", "/api/player/seek").single().bodyText())
        awaitUntil {
            shadowOf(Looper.getMainLooper()).idle()
            player.currentPosition == 300_000L
        }

        player.seekToNext()
        awaitPost("/api/player/next")
        player.seekToPrevious()
        awaitPost("/api/player/previous")

        player.pause()
        awaitPost("/api/player/toggle")
        awaitUntil {
            shadowOf(Looper.getMainLooper()).idle()
            !player.playWhenReady && rig.repo.now.value?.state == "paused"
        }
        player.pause() // already paused: nothing more is sent
        settle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, server.calls("POST", "/api/player/toggle").size)

        player.play()
        awaitUntil {
            shadowOf(Looper.getMainLooper()).idle()
            server.calls("POST", "/api/player/toggle").size == 2
        }

        player.stop()
        awaitPost("/api/player/stop")
        awaitUntil {
            shadowOf(Looper.getMainLooper()).idle()
            player.playbackState == Player.STATE_IDLE
        }
        assertEquals(1, server.calls("POST", "/api/player/seek").size)
        assertEquals(1, server.calls("POST", "/api/player/next").size)
        assertEquals(1, server.calls("POST", "/api/player/previous").size)
        rig.close()
    }

    @Test
    fun likeCommandRatesCurrentVideo() = runTest {
        val rig = rig()
        rig.repo.publish(dto(nowJson("playing")))
        val quickRate = QuickRate(server.api()) { rig.repo.now.value?.item?.videoId }

        assertTrue(quickRate.handle(QuickRate.LIKE_ACTION))
        assertEquals(
            """{"video_id":"abc123","liked":true}""",
            server.calls("POST", "/api/ratings").single().bodyText(),
        )
        assertTrue(quickRate.handle(QuickRate.DISLIKE_ACTION))
        assertEquals(
            """{"video_id":"abc123","liked":false}""",
            server.calls("POST", "/api/ratings").last().bodyText(),
        )

        assertFalse(quickRate.handle("home_studio.unknown"))
        assertFalse(QuickRate(server.api()) { null }.handle(QuickRate.LIKE_ACTION)) // nothing playing
        assertEquals(2, server.calls("POST", "/api/ratings").size)
        assertEquals("home_studio.like", QuickRate.LIKE_ACTION)
        assertEquals("home_studio.dislike", QuickRate.DISLIKE_ACTION)
        rig.close()
    }

    @Test
    fun pauseThenPlayEndsPlaying() = runTest {
        val rig = rig()
        val player = rig.player
        rig.repo.publish(dto(nowJson("playing")))
        sync()

        player.pause()
        player.play() // before the toggle answered: checked against the state the pause left
        awaitUntil {
            shadowOf(Looper.getMainLooper()).idle()
            server.calls("POST", "/api/player/toggle").size == 2
        }
        settle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("playing", serverState)
        assertEquals("playing", rig.repo.now.value?.state)
        assertTrue(player.playWhenReady)

        player.play() // already playing: nothing is sent
        settle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, server.calls("POST", "/api/player/toggle").size)
        rig.close()
    }

    @Test
    fun unreachablePiShowsAsNotPlaying() = runTest {
        val rig = rig()
        val player = rig.player
        rig.repo.publish(dto(nowJson("playing")))
        sync()
        assertTrue(player.playWhenReady)

        server.down = true
        rig.repo.refresh()
        awaitUntil {
            shadowOf(Looper.getMainLooper()).idle()
            !rig.repo.reachable.value
        }
        sync()
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(player.playWhenReady)
        assertEquals("How GPUs Work", player.mediaMetadata.title.toString()) // still says what it was

        server.down = false
        rig.repo.refresh()
        awaitUntil {
            shadowOf(Looper.getMainLooper()).idle()
            player.playbackState == Player.STATE_READY
        }
        assertTrue(player.playWhenReady)
        rig.close()
    }

    @Test
    fun onlyTrustedControllersMayRate() {
        val own = "com.jamilcpu.homestudio"
        assertTrue(QuickRate.mayRate(isNotificationController = true, controllerPackage = "android.media", ownPackage = own))
        assertTrue(QuickRate.mayRate(isNotificationController = false, controllerPackage = own, ownPackage = own))
        assertTrue(QuickRate.mayRate(isNotificationController = false, controllerPackage = "com.android.systemui", ownPackage = own))
        assertFalse(QuickRate.mayRate(isNotificationController = false, controllerPackage = "com.example.other", ownPackage = own))
    }
}
