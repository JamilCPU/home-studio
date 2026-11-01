package com.jamilcpu.homestudio.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.settings() = Settings(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "home_studio_settings.preferences_pb")
        },
    )

    private suspend fun Settings.setExpectingRejection(url: String) {
        try {
            setServerUrl(url)
            throw AssertionError("expected rejection of '$url'")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun defaultsToNull() = runTest {
        assertNull(settings().serverUrl.first())
    }

    @Test
    fun acceptsLanUrl() = runTest {
        val s = settings()
        s.setServerUrl("http://192.0.2.50:8765")
        assertEquals("http://192.0.2.50:8765", s.serverUrl.first())
    }

    @Test
    fun acceptsHttpsTailscaleName() = runTest {
        val s = settings()
        s.setServerUrl("https://pi.example.ts.net:8765")
        assertEquals("https://pi.example.ts.net:8765", s.serverUrl.first())
    }

    @Test
    fun trimsWhitespaceAndTrailingSlash() = runTest {
        val s = settings()
        s.setServerUrl("  http://pi.local:8765/  ")
        assertEquals("http://pi.local:8765", s.serverUrl.first())
    }

    @Test
    fun rejectsNonHttpScheme() = runTest {
        val s = settings()
        s.setExpectingRejection("ftp://pi.local")
        s.setExpectingRejection("pi.local:8765")
        s.setExpectingRejection("")
        assertNull(s.serverUrl.first())
    }

    @Test
    fun rejectsMissingHost() = runTest {
        val s = settings()
        s.setExpectingRejection("http://")
        assertNull(s.serverUrl.first())
    }

    @Test
    fun normalizeReturnsCleanUrlOrNull() {
        assertEquals("http://a.b:1", Settings.normalizeServerUrl("http://a.b:1/"))
        assertNull(Settings.normalizeServerUrl("gopher://a.b"))
    }

    @Test
    fun rejectsPathQueryAndFragment() = runTest {
        val s = settings()
        s.setExpectingRejection("http://pi:8765/api")
        s.setExpectingRejection("http://pi:8765/?x=1")
        s.setExpectingRejection("http://pi:8765/#frag")
        assertNull(s.serverUrl.first())
        assertNull(Settings.normalizeServerUrl("http://pi:8765/api"))
        assertEquals(Settings.PATH_NOT_ALLOWED_MESSAGE, Settings.serverUrlError("http://pi:8765/api"))
        assertEquals(Settings.PATH_NOT_ALLOWED_MESSAGE, Settings.serverUrlError("http://pi:8765/?x=1"))
        assertEquals(Settings.INVALID_URL_MESSAGE, Settings.serverUrlError("pi:8765"))
        assertNull(Settings.serverUrlError("http://pi:8765"))
    }

    @Test
    fun acceptsBareTrailingSlash() = runTest {
        val s = settings()
        s.setServerUrl("http://pi:8765/")
        assertEquals("http://pi:8765", s.serverUrl.first())
        assertEquals("Enter just the address, like http://192.168.x.x:8765", Settings.PATH_NOT_ALLOWED_MESSAGE)
    }
}
