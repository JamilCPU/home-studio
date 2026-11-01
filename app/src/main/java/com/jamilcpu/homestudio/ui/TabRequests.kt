package com.jamilcpu.homestudio.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lets code outside the shell (a notification tap) ask it to switch tab. Lives in the AppContainer. */
class TabRequests {
    private val _requested = MutableStateFlow<String?>(null)

    /** The route the shell should show next, until it calls [consume]. */
    val requested: StateFlow<String?> = _requested.asStateFlow()

    fun request(route: String) {
        _requested.value = route
    }

    fun consume() {
        _requested.value = null
    }
}
