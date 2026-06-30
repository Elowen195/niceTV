package com.elowen.niceTV.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.elowen.niceTV.ui.theme.NeonPurple

@Composable
fun SectionHeader(
    title: String,
    showMore: Boolean = true,
    actionText: String = "更多 >",
    onMoreClick: () -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val isCompact = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() } < 600.dp
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isCompact) 8.dp else 16.dp, vertical = if (isCompact) 6.dp else 10.dp),
        shape = RoundedCornerShape(if (isCompact) 12.dp else 18.dp),
        contentPadding = PaddingValues(horizontal = if (isCompact) 10.dp else 12.dp, vertical = if (isCompact) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (showMore) {
                Text(
                    text = actionText,
                    color = NeonPurple,
                    fontSize = if (isCompact) 12.sp else 14.sp,
                    modifier = Modifier
                        .clickable { onMoreClick() }
                        .padding(if (isCompact) 6.dp else 8.dp)
                )
            }
        }
    }
}
