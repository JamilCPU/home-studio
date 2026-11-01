package com.jamilcpu.homestudio.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_FILE_NAME = "home_studio_settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_FILE_NAME)

/** Persistent app settings. Currently just the Home Studio server address. */
class Settings(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    /** The saved server URL (no trailing slash), or null before first-run setup. */
    val serverUrl: Flow<String?> = store.data.map { it[SERVER_URL_KEY] }

    /**
     * Saves [url] after trimming it and dropping a trailing slash.
     *
     * @throws IllegalArgumentException when it is not an http:// or https:// URL with a host.
     */
    suspend fun setServerUrl(url: String) {
        serverUrlError(url)?.let { throw IllegalArgumentException(it) }
        val normalized = checkNotNull(normalizeServerUrl(url))
        store.edit { it[SERVER_URL_KEY] = normalized }
    }

    companion object {
        const val INVALID_URL_MESSAGE =
            "Enter the full address, starting with http:// or https://"
        const val PATH_NOT_ALLOWED_MESSAGE =
            "Enter just the address, like http://192.168.x.x:8765"

        private val SERVER_URL_KEY = stringPreferencesKey("server_url")

        /** Returns the cleaned URL, or null when it is not a usable http(s) address. */
        fun normalizeServerUrl(raw: String): String? =
            if (serverUrlError(raw) == null) raw.trim().trimEnd('/') else null

        /** A user-facing reason [raw] cannot be used as the server address, or null when it can. */
        fun serverUrlError(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            val lower = trimmed.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return INVALID_URL_MESSAGE
            val uri = try {
                URI(trimmed)
            } catch (e: URISyntaxException) {
                return INVALID_URL_MESSAGE
            }
            if (uri.host.isNullOrBlank()) return INVALID_URL_MESSAGE
            val hasPath = !uri.rawPath.isNullOrEmpty()
            if (hasPath || uri.rawQuery != null || uri.rawFragment != null) return PATH_NOT_ALLOWED_MESSAGE
            return null
        }
    }
}
