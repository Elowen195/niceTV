package com.elowen.niceTV.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.elowen.niceTV.ui.theme.GlowCoral
import com.elowen.niceTV.ui.theme.GlowCyan
import com.elowen.niceTV.ui.theme.GradientEnd
import com.elowen.niceTV.ui.theme.GradientMid
import com.elowen.niceTV.ui.theme.GradientStart

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val base = Brush.linearGradient(
                    colors = listOf(GradientStart, GradientMid, GradientEnd),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                val glowTopRight = Brush.radialGradient(
                    colors = listOf(GlowCyan, Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.18f),
                    radius = size.minDimension * 0.9f
                )
                val glowBottomLeft = Brush.radialGradient(
                    colors = listOf(GlowCoral, Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.88f),
                    radius = size.minDimension * 1.0f
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(glowTopRight)
                    drawRect(glowBottomLeft)
                }
            }
    ) {
        content()
    }
}
