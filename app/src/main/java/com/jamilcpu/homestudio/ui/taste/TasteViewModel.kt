package com.jamilcpu.homestudio.ui.taste

import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.TraitKind
import com.jamilcpu.homestudio.ui.common.ExclusiveRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val TASTE_POLL_MS = 3_000L
const val TASTE_TIMEOUT_MS = 60_000L

/**
 * Drives the Taste tab. A correction is refreshed by Claude in the background, so after it (or a
 * trait removal) the profile is refetched every [pollIntervalMs] until `updated_at` moves on from
 * what it was before the change, giving up after [timeoutMs] or when `/api/status` says Claude failed.
 */
class TasteViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = TASTE_POLL_MS,
    private val timeoutMs: Long = TASTE_TIMEOUT_MS,
) {
    private val _uiState = MutableStateFlow(TasteUiState())
    val uiState: StateFlow<TasteUiState> = _uiState.asStateFlow()

    private val exclusive = ExclusiveRunner(scope) { busy -> _uiState.update { it.copy(busy = busy) } }
    private var watchJob: Job? = null

    /** Loads the profile; also the Retry action. Skipped while waiting for an update (the watch refetches). */
    fun start() {
        if (_uiState.value.updating) return
        scope.launch { load() }
    }

    fun onInput(text: String) = _uiState.update { it.copy(input = text, error = null) }

    fun submitCorrection() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return
        exclusive.run {
            val baseline = _uiState.value.taste?.updatedAt
            when (val r = api.correctTaste(text)) {
                is ApiResult.Ok -> {
                    _uiState.update { it.copy(input = "", error = null, unreachable = false) }
                    watchForUpdate(baseline)
                }
                is ApiResult.Failure -> fail(r)
            }
        }
    }

    fun removeTrait(kind: TraitKind, text: String) {
        exclusive.run {
            val baseline = _uiState.value.taste?.updatedAt
            _uiState.update { it.copy(updating = true, note = null, error = null) }
            when (val r = api.removeTrait(kind, text)) {
                is ApiResult.Ok -> {
                    _uiState.update { it.copy(taste = r.value, unreachable = false) }
                    if (r.value.updatedAt != baseline) {
                        watchJob?.cancel()
                        _uiState.update { it.copy(updating = false) }
                    } else {
                        watchForUpdate(baseline)
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(updating = false) }
                    fail(r)
                }
            }
        }
    }

    private fun watchForUpdate(baseline: String?) {
        watchJob?.cancel()
        _uiState.update { it.copy(updating = true, note = null) }
        watchJob = scope.launch {
            var waited = 0L
            while (waited < timeoutMs) {
                delay(pollIntervalMs)
                waited += pollIntervalMs
                val taste = api.taste()
                if (taste is ApiResult.Ok && taste.value?.updatedAt != baseline) {
                    _uiState.update { it.copy(taste = taste.value, updating = false, unreachable = false) }
                    return@launch
                }
                val problem = (api.status() as? ApiResult.Ok)?.value?.claude?.let(::claudeProblem)
                if (problem != null) {
                    _uiState.update { it.copy(updating = false, note = problem) }
                    return@launch
                }
            }
            _uiState.update { it.copy(updating = false, note = TASTE_NEXT_RUN) }
        }
    }

    private suspend fun load() {
        when (val r = api.taste()) {
            is ApiResult.Ok -> _uiState.update { it.copy(taste = r.value, loaded = true, unreachable = false, error = null) }
            is ApiResult.Failure -> fail(r)
        }
    }

    private fun fail(failure: ApiResult.Failure) = _uiState.update {
        if (failure.kind == ApiResult.Failure.Kind.Unreachable) {
            it.copy(unreachable = true)
        } else {
            it.copy(error = failure.message)
        }
    }
}
