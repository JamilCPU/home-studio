package com.jamilcpu.homestudio.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun UnreachableBanner(onRetry: () -> Unit) {
    NoticeCard(MaterialTheme.colorScheme.errorContainer, "bannerUnreachable") {
        Text("Can't reach your Pi", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
internal fun NoticeCard(container: Color, tag: String, content: @Composable RowScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().testTag(tag),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

