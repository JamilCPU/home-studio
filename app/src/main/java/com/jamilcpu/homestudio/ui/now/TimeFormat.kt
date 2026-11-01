package com.jamilcpu.homestudio.ui.now

import kotlin.math.floor

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/** `m:ss` under an hour, `h:mm:ss` from an hour up. Negative and NaN values clamp to 0:00. */
fun formatClock(seconds: Double): String {
    val total = if (seconds.isNaN()) 0 else floor(seconds.coerceAtLeast(0.0)).toLong()
    val h = total / SECONDS_PER_HOUR
    val m = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun elapsedLabel(positionS: Double): String = "${formatClock(positionS)} elapsed"

/** Uses the real minus sign (U+2212). */
fun remainingLabel(positionS: Double, durationS: Double?): String =
    if (durationS == null) "" else "−${formatClock(durationS - positionS)} left"
