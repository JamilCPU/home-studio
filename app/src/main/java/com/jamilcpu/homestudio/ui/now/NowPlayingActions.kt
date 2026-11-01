package com.jamilcpu.homestudio.ui.now

/** Everything the screen can ask for, so the stateless screen is easy to test. */
data class NowPlayingActions(
    val onDragStart: () -> Unit = {},
    val onDrag: (Float) -> Unit = {},
    val onDragEnd: () -> Unit = {},
    val onToggle: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSkipBack: () -> Unit = {},
    val onSkipForward: () -> Unit = {},
    val onChapter: (Double) -> Unit = {},
    val onQuickRate: (Boolean) -> Unit = {},
    val onRate: (String) -> Unit = {},
    val onOpenYoutube: () -> Unit = {},
    val onAskAgent: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
