package com.elowen.niceTV.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.ui.theme.GlassBorder

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun VideoCard(
    post: Post,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val windowInfo = LocalWindowInfo.current
    val isCompact = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() } < 600.dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (isCompact) 3.dp else 4.dp)
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(if (isCompact) 12.dp else 18.dp),
        border = BorderStroke(1.dp, GlassBorder),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompact) 4.dp else 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(320f / 216f) // 保持原始图片比例 320:216
        ) {
            // 1. 背景图片
            GlideImage(
                model = post.imageUrl,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 2. 黑色渐变遮罩 (让文字看得清)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 260f
                        )
                    )
            )
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (isCompact) 6.dp else 10.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(if (isCompact) 8.dp else 14.dp),
                contentPadding = PaddingValues(horizontal = if (isCompact) 8.dp else 10.dp, vertical = if (isCompact) 5.dp else 8.dp)
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (isCompact) 12.sp else 14.sp
                )
            }
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.48f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = Color.Cyan,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}
