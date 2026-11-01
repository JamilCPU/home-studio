package com.jamilcpu.homestudio.ui.common

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs one action at a time on [scope]; a [run] while another is running is ignored.
 * [onBusy] is told when an action starts (true) and when it ends (false), so the state can disable buttons.
 */
class ExclusiveRunner(
    private val scope: CoroutineScope,
    private val onBusy: (Boolean) -> Unit,
) {
    private val busy = AtomicBoolean(false)

    /** Returns false (and does nothing) when another action is still running. */
    fun run(block: suspend () -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        onBusy(true)
        scope.launch {
            try {
                block()
            } finally {
                busy.set(false)
                onBusy(false)
            }
        }
        return true
    }
}
