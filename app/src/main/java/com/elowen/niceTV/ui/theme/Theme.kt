package com.elowen.niceTV.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    background = BlackBackground,
    surface = SurfaceDark,
    onPrimary = Color.Black,
    onBackground = TextWhite,
    onSurface = TextWhite,
)

@Composable
fun NiceTVTheme(
    content: @Composable () -> Unit
) {
    // System bar styling is controlled by the host activity.
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
