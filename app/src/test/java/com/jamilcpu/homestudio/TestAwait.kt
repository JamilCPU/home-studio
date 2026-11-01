package com.jamilcpu.homestudio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Ktor's mock engine answers on a real thread, so virtual-time tests must wait for real I/O to
 * finish and then let the test dispatcher resume the continuation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    while (true) {
        runCurrent()
        if (condition()) return
        check(System.nanoTime() < deadline) { "condition not met within ${timeoutMs}ms" }
        Thread.sleep(2)
    }
}

/** Gives any in-flight request time to land; for asserting that nothing more happens. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settle() {
    repeat(25) {
        runCurrent()
        Thread.sleep(4)
    }
}
