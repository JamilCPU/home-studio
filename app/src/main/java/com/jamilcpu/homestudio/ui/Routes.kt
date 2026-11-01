package com.jamilcpu.homestudio.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/** Navigation route names, in one place. */
object Routes {
    const val NOW = "now"
    const val AGENT = "agent"
    const val QUEUE = "queue"
    const val HISTORY = "history"
    const val TASTE = "taste"
    const val MORNING = "morning"

    const val START = AGENT
}

/** A bottom-bar destination. */
data class Tab(val route: String, val label: String, val icon: ImageVector)

/** Bottom tabs, in display order. */
val BottomTabs: List<Tab> = listOf(
    Tab(Routes.NOW, "Now", Icons.Filled.PlayArrow),
    Tab(Routes.AGENT, "Agent", Icons.Filled.Star),
    Tab(Routes.QUEUE, "Queue", Icons.AutoMirrored.Filled.List),
    Tab(Routes.HISTORY, "History", Icons.Filled.DateRange),
    Tab(Routes.TASTE, "Taste", Icons.Filled.Favorite),
)

/** Title shown in the top bar for [route]. */
fun titleFor(route: String?): String =
    if (route == Routes.MORNING) "Morning" else BottomTabs.firstOrNull { it.route == route }?.label.orEmpty()
