package com.jamilcpu.homestudio.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Asks for POST_NOTIFICATIONS once, on first launch (Android 13+), with no rationale screen. If it
 * is denied the media session still serves the lock screen and Bluetooth; there is just no
 * notification. Construct it while the activity initialises (it registers a result launcher).
 */
class NotificationPermission(private val activity: ComponentActivity) {
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i(TAG, "Notification permission granted=$granted")
    }

    fun requestOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        launcher.launch(permission)
    }

    private companion object {
        const val TAG = "NotificationPermission"
        const val PREFS = "home_studio_prompts"
        const val KEY_ASKED = "asked_post_notifications"
    }
}
