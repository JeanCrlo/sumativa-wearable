package com.packtrack.wearos.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun PackTrackTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}
