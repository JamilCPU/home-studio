package com.jamilcpu.homestudio.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jamilcpu.homestudio.data.Settings
import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.launch

internal const val WIFI_ONLY_NOTE =
    "Works on home Wi-Fi only. Use your Pi's Tailscale name to connect from anywhere."
internal const val UNREACHABLE_AT_ADDRESS = "Can't reach your Pi at that address"

/** Immutable form state; every change goes through [copy]. */
private data class ConnectState(
    val input: String = "",
    val testing: Boolean = false,
    /** The normalized URL that passed the last test, if any. */
    val verifiedUrl: String? = null,
    val error: String? = null,
)

/** First-run screen: enter the Pi's address, test it, then save. */
@Composable
fun ConnectScreen(api: HomeStudioApi, onSave: (String) -> Unit) {
    var state by remember { mutableStateOf(ConnectState()) }
    val scope = rememberCoroutineScope()
    val normalized = Settings.normalizeServerUrl(state.input)
    val canSave = normalized != null && normalized == state.verifiedUrl && !state.testing

    fun test() {
        val problem = Settings.serverUrlError(state.input)
        if (problem != null || normalized == null) {
            state = state.copy(error = problem ?: Settings.INVALID_URL_MESSAGE, verifiedUrl = null)
            return
        }
        state = state.copy(testing = true, error = null, verifiedUrl = null)
        scope.launch {
            val result = api.statusAt(normalized)
            state = when (result) {
                is ApiResult.Ok -> state.copy(testing = false, verifiedUrl = normalized)
                is ApiResult.Failure -> state.copy(testing = false, error = result.userMessage())
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Connect to your Pi", style = MaterialTheme.typography.headlineMedium)
        Text("Enter the address of your Home Studio server, like http://192.168.x.x:8765")
        OutlinedTextField(
            value = state.input,
            onValueChange = { state = state.copy(input = it, verifiedUrl = null, error = null) },
            label = { Text("Server address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("urlField"),
        )
        if (normalized != null && !isTailscaleHost(normalized)) {
            Text(WIFI_ONLY_NOTE, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("connectError"))
        }
        if (state.verifiedUrl != null) Text("Connected")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = ::test,
                enabled = !state.testing && state.input.isNotBlank(),
                modifier = Modifier.testTag("testButton"),
            ) {
                if (state.testing) {
                    CircularProgressIndicator(Modifier.size(18.dp).testTag("testSpinner"), strokeWidth = 2.dp)
                } else {
                    Text("Test connection")
                }
            }
            Button(
                onClick = { normalized?.let(onSave) },
                enabled = canSave,
                modifier = Modifier.testTag("saveButton"),
            ) { Text("Save") }
        }
    }
}

private fun ApiResult.Failure.userMessage(): String =
    if (kind == ApiResult.Failure.Kind.Unreachable) UNREACHABLE_AT_ADDRESS else message

private fun isTailscaleHost(url: String): Boolean {
    val host = try {
        URI(url).host
    } catch (e: URISyntaxException) {
        null
    }
    return host?.lowercase()?.endsWith(".ts.net") == true
}
