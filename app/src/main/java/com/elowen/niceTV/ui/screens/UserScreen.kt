package com.elowen.niceTV.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
fun UserScreen() {
    val windowInfo = LocalWindowInfo.current
    val isCompact = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() } < 600.dp
    var showProxySettings by remember { mutableStateOf(false) }

    BackHandler(enabled = showProxySettings) {
        showProxySettings = false
    }

    if (showProxySettings) {
        ProxySettingsScreen(onBack = { showProxySettings = false })
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 12.dp else 16.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // User Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(if (isCompact) 12.dp else 16.dp))
                .padding(if (isCompact) 16.dp else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 56.dp else 80.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(if (isCompact) 32.dp else 48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
                Text(
                    text = "本地模式",
                    color = Color.White,
                    fontSize = if (isCompact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "收藏、下载和代理配置仅保存在此设备",
                    color = Color.Gray,
                    fontSize = if (isCompact) 12.sp else 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isCompact) 20.dp else 32.dp))

        // Settings Section
        Text(
            text = "设置",
            color = Color.White,
            fontSize = if (isCompact) 18.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = if (isCompact) 10.dp else 16.dp)
        )

        // Proxy Settings Entry
        SettingsItem(
            icon = Icons.Default.VpnKey,
            title = "代理设置",
            subtitle = "管理代理节点和订阅",
            onClick = { showProxySettings = true }
        )

    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.Cyan,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
        }
    }
}
