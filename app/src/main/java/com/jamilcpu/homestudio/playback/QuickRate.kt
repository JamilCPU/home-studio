package com.jamilcpu.homestudio.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi

/**
 * The notification's 👍/👎 buttons: rates whatever is playing now ([currentVideoId]) with
 * `liked` only. Kept free of session types so it can be tested on its own.
 */
class QuickRate(
    private val api: HomeStudioApi,
    private val currentVideoId: () -> String?,
) {
    /** Handles a custom session action; true when the rating was stored. */
    suspend fun handle(action: String): Boolean {
        val liked = when (action) {
            LIKE_ACTION -> true
            DISLIKE_ACTION -> false
            else -> return false
        }
        val videoId = currentVideoId() ?: return false
        return api.rate(videoId, liked = liked) is ApiResult.Ok
    }

    companion object {
        const val LIKE_ACTION = "home_studio.like"
        const val DISLIKE_ACTION = "home_studio.dislike"

        /** System UI forwards the notification's buttons on Android 13+ through its own controller. */
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /** Only the media notification, this app and System UI may rate. */
        fun mayRate(isNotificationController: Boolean, controllerPackage: String, ownPackage: String): Boolean =
            isNotificationController || controllerPackage == ownPackage || controllerPackage == SYSTEM_UI_PACKAGE

        val LIKE: SessionCommand = SessionCommand(LIKE_ACTION, Bundle.EMPTY)
        val DISLIKE: SessionCommand = SessionCommand(DISLIKE_ACTION, Bundle.EMPTY)

        /** The two buttons shown in the media notification, beside play/pause, next and previous. */
        @OptIn(UnstableApi::class)
        fun layout(): List<CommandButton> = listOf(
            CommandButton.Builder(CommandButton.ICON_THUMB_UP_UNFILLED)
                .setDisplayName("Like")
                .setSessionCommand(LIKE)
                .build(),
            CommandButton.Builder(CommandButton.ICON_THUMB_DOWN_UNFILLED)
                .setDisplayName("Dislike")
                .setSessionCommand(DISLIKE)
                .build(),
        )
    }
}
