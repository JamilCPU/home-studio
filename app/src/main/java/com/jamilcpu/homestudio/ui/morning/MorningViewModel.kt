package com.jamilcpu.homestudio.ui.morning

import com.jamilcpu.homestudio.data.api.ApiResult
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.api.MorningDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_TOPIC = 100
private const val MAX_TOPICS = 20
private val WEEKDAYS = 0..6

/**
 * Drives the Morning settings. Edits change a local [MorningUiState.draft]; Save sends it with PUT
 * and adopts the server's answer (which carries the read-only next_build_at and last_outcome).
 */
class MorningViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow(MorningUiState())
    val uiState: StateFlow<MorningUiState> = _uiState.asStateFlow()

    /** Loads the settings; also the Retry action. Unsaved edits are kept. */
    fun start() {
        scope.launch {
            when (val r = api.morning()) {
                is ApiResult.Ok -> _uiState.update {
                    it.copy(saved = r.value, draft = if (it.dirty) it.draft else r.value, unreachable = false)
                }
                is ApiResult.Failure -> fail(r)
            }
        }
    }

    fun setEnabled(value: Boolean) = edit { it.copy(enabled = value) }

    fun setWakeTime(hhmm: String) {
        val minutes = parseHhMm(hhmm) ?: return
        edit { it.copy(wakeTime = formatHhMm(minutes)) }
    }

    fun toggleDay(day: Int) {
        if (day !in WEEKDAYS) return
        edit { d -> d.copy(days = if (day in d.days) d.days - day else (d.days + day).sorted()) }
    }

    fun removeTopic(topic: String) = edit { it.copy(topics = it.topics - topic) }

    fun onTopicInput(text: String) = _uiState.update { it.copy(topicInput = text.take(MAX_TOPIC)) }

    /** Adds the typed topic; blank and case-insensitive duplicates are ignored. */
    fun addTopic() {
        val topic = _uiState.value.topicInput.trim()
        val draft = _uiState.value.draft ?: return
        _uiState.update { it.copy(topicInput = "") }
        if (topic.isEmpty() || draft.topics.size >= MAX_TOPICS) return
        if (draft.topics.any { it.equals(topic, ignoreCase = true) }) return
        edit { it.copy(topics = it.topics + topic) }
    }

    fun setCount(value: Int) = edit { it.copy(count = value.coerceIn(COUNT_RANGE)) }

    fun setLead(value: Int) = edit { it.copy(buildLeadMin = value.coerceIn(LEAD_RANGE)) }

    fun save() {
        val s = _uiState.value
        val draft = s.draft ?: return
        if (s.saving) return
        if (draft.topics.isEmpty()) {
            _uiState.update { it.copy(error = MORNING_NO_TOPICS, notice = null) }
            return
        }
        _uiState.update { it.copy(saving = true, error = null, notice = null) }
        scope.launch {
            when (val r = api.saveMorning(draft)) {
                is ApiResult.Ok -> _uiState.update {
                    // Keep edits made while the save was in flight.
                    val next = if (it.draft == draft) r.value else it.draft
                    it.copy(saved = r.value, draft = next, saving = false, notice = MORNING_SAVED, unreachable = false)
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(saving = false) }
                    fail(r)
                }
            }
        }
    }

    /** Builds a morning set now; [onStarted] runs once the server accepted it (the shell opens Agent). */
    fun testNow(onStarted: () -> Unit) {
        if (_uiState.value.testing) return
        _uiState.update { it.copy(testing = true, error = null, notice = null) }
        scope.launch {
            val r = api.testMorning()
            _uiState.update { it.copy(testing = false) }
            when (r) {
                is ApiResult.Ok -> onStarted()
                is ApiResult.Failure -> fail(r)
            }
        }
    }

    private fun edit(change: (MorningDto) -> MorningDto) = _uiState.update { s ->
        s.draft?.let { s.copy(draft = change(it), error = null, notice = null) } ?: s
    }

    private fun fail(failure: ApiResult.Failure) = _uiState.update {
        if (failure.kind == ApiResult.Failure.Kind.Unreachable) {
            it.copy(unreachable = true)
        } else {
            it.copy(error = failure.message)
        }
    }
}
