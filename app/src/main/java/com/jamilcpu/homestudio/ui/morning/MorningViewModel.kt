package com.jamilcpu.homestudio.ui.morning

import com.jamilcpu.homestudio.data.api.HomeStudioApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MorningViewModel(
    private val api: HomeStudioApi,
    private val scope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow(MorningUiState())
    val uiState: StateFlow<MorningUiState> = _uiState.asStateFlow()

    fun start() = Unit
    fun setEnabled(value: Boolean) = Unit
    fun setWakeTime(hhmm: String) = Unit
    fun toggleDay(day: Int) = Unit
    fun removeTopic(topic: String) = Unit
    fun onTopicInput(text: String) = Unit
    fun addTopic() = Unit
    fun setCount(value: Int) = Unit
    fun setLead(value: Int) = Unit
    fun save() = Unit
    fun testNow(onStarted: () -> Unit) = Unit
}
