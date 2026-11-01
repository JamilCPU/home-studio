package com.jamilcpu.homestudio.data.api

import io.ktor.client.engine.mock.MockEngine
import java.net.ConnectException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeStudioApiLogTest {
    @Test
    fun transportFailureIsLoggedBeforeBecomingUnreachable() = runTest {
        ShadowLog.clear()
        val engine = MockEngine { throw ConnectException("refused") }
        val api = HomeStudioApi({ "http://pi.test:8765" }, engine)

        val result = api.status()

        assertEquals(ApiResult.Failure.Kind.Unreachable, (result as ApiResult.Failure).kind)
        val warning = ShadowLog.getLogs().single { it.tag == "HomeStudioApi" }
        assertEquals(android.util.Log.WARN, warning.type)
        assertTrue(warning.throwable is ConnectException)
    }
}
