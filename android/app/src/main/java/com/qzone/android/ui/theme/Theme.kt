package com.qzone.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val QQZoneColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFF2A541),
    secondary = androidx.compose.ui.graphics.Color(0xFF48A9A6),
    tertiary = androidx.compose.ui.graphics.Color(0xFF8D6A9F),
    background = androidx.compose.ui.graphics.Color(0xFF0A1C2B),
    surface = androidx.compose.ui.graphics.Color(0xFF102A43),
)

@Composable
fun QQZoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QQZoneColorScheme,
        content = content,
    )
}
