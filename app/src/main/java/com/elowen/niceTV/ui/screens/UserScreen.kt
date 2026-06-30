package com.elowen.niceTV.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elowen.niceTV.ui.viewmodel.AuthUiState

@Composable
fun UserScreen(
    authState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSyncFavorites: () -> Unit
) {
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
        UserHeader(authState, isCompact)
        Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 24.dp))
        AccountPanel(
            authState = authState,
            onLogin = onLogin,
            onRegister = onRegister,
            onLogout = onLogout,
            onSyncFavorites = onSyncFavorites
        )
        Spacer(modifier = Modifier.height(if (isCompact) 20.dp else 32.dp))

        Text(
            text = "设置",
            color = Color.White,
            fontSize = if (isCompact) 18.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = if (isCompact) 10.dp else 16.dp)
        )

        SettingsItem(
            icon = Icons.Default.VpnKey,
            title = "代理设置",
            subtitle = "管理代理节点和订阅",
            onClick = { showProxySettings = true }
        )
    }
}
@Composable
private fun UserHeader(authState: AuthUiState, isCompact: Boolean) {
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
                    .background(if (authState.isLoggedIn) Color.Cyan.copy(alpha = 0.35f) else Color.Gray),
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
                text = authState.session?.username ?: "本地模式",
                color = Color.White,
                fontSize = if (isCompact) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (authState.isLoggedIn) "收藏可同步，评论可发布" else "登录后启用云收藏和评论",
                color = Color.Gray,
                fontSize = if (isCompact) 12.sp else 14.sp
            )
        }
    }
}

@Composable
private fun AccountPanel(
    authState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSyncFavorites: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("账号", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (authState.isLoggedIn) {
            Text("已连接 NiceTV 后端", color = Color.Gray, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !authState.isLoading,
                    onClick = onSyncFavorites
                ) {
                    Text("同步收藏")
                }
                TextButton(
                    enabled = !authState.isLoading,
                    onClick = onLogout
                ) {
                    Text("退出登录")
                }
            }
        } else {
            LoginRegisterForm(
                isLoading = authState.isLoading,
                onLogin = onLogin,
                onRegister = onRegister
            )
        }
        if (authState.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在处理...", color = Color.Gray, fontSize = 13.sp)
            }
        }
        authState.error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
        authState.message?.let { Text(it, color = Color.Cyan, fontSize = 13.sp) }
    }
}

@Composable
private fun LoginRegisterForm(
    isLoading: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = login,
        onValueChange = { login = it },
        enabled = !isLoading,
        singleLine = true,
        label = { Text(if (registerMode) "用户名" else "用户名或邮箱") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        enabled = !isLoading,
        singleLine = true,
        label = { Text("密码") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = !isLoading,
            onClick = {
                if (registerMode) onRegister(login, password) else onLogin(login, password)
            }
        ) {
            Text(if (registerMode) "注册并登录" else "登录")
        }
        TextButton(
            enabled = !isLoading,
            onClick = { registerMode = !registerMode }
        ) {
            Text(if (registerMode) "已有账号" else "注册账号")
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    Text(
        text = "未登录也可以继续浏览、播放、下载和使用本地收藏。",
        color = Color.Gray,
        fontSize = 12.sp
    )
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
