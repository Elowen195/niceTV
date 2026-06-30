package com.elowen.niceTV.ui.components

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.elowen.niceTV.ui.theme.GlassBlack
import com.elowen.niceTV.ui.theme.GlassBorder
import com.elowen.niceTV.ui.theme.GlassHighlight

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    border: BorderStroke = BorderStroke(1.dp, GlassBorder),
    background: Brush = Brush.linearGradient(listOf(GlassHighlight, GlassBlack)),
    content: @Composable BoxScope.() -> Unit
) {
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                28f,
                28f,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .clip(shape)
            .border(border, shape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(blurModifier)
                .background(background)
                .zIndex(0f)
        )
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .zIndex(1f)
        ) {
            content()
        }
    }
}
