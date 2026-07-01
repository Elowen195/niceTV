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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elowen.niceTV.data.backend.CollectionDetail
import com.elowen.niceTV.data.backend.VideoCollection
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.ui.viewmodel.AuthUiState
import com.elowen.niceTV.ui.viewmodel.CollectionsUiState

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
fun CollectionsScreen(
    authState: AuthUiState,
    state: CollectionsUiState,
    onRefresh: () -> Unit,
    onCreateCollection: (String, String, String) -> Unit,
    onSelectCollection: (VideoCollection) -> Unit,
    onCopyCollection: (VideoCollection) -> Unit,
    onPostClick: (Post) -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val isCompact = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() } < 600.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 12.dp else 16.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp)
    ) {
        Text(
            text = "清单",
            color = Color.White,
            fontSize = if (isCompact) 22.sp else 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "发现公开清单，整理自己的片单，也可以复制别人的好清单。",
            color = Color.Gray,
            fontSize = if (isCompact) 12.sp else 14.sp
        )
        CollectionsPanel(
            authState = authState,
            state = state,
            onRefresh = onRefresh,
            onCreateCollection = onCreateCollection,
            onSelectCollection = onSelectCollection,
            onCopyCollection = onCopyCollection,
            onPostClick = onPostClick
        )
    }
}

@Composable
private fun CollectionsPanel(
    authState: AuthUiState,
    state: CollectionsUiState,
    onRefresh: () -> Unit,
    onCreateCollection: (String, String, String) -> Unit,
    onSelectCollection: (VideoCollection) -> Unit,
    onCopyCollection: (VideoCollection) -> Unit,
    onPostClick: (Post) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateCollectionDialog(
            isLoading = state.isCreating,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, visibility ->
                onCreateCollection(title, description, visibility)
                showCreateDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("共享清单", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("把一组视频整理成可分享列表", color = Color.Gray, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新清单", tint = Color.Cyan)
                }
                Button(
                    enabled = authState.isLoggedIn && !state.isCreating,
                    onClick = { showCreateDialog = true }
                ) {
                    Text("新建")
                }
            }
        }

        if (state.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在加载清单...", color = Color.Gray, fontSize = 13.sp)
            }
        }
        state.error?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        state.message?.let {
            Text(
                text = it,
                color = Color.Cyan,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (authState.isLoggedIn) {
            CollectionListSection(
                title = "我的清单",
                collections = state.myCollections,
                emptyText = "还没有清单",
                onSelectCollection = onSelectCollection
            )
        } else {
            Text("登录后可以创建、复制和编辑自己的清单。", color = Color.Gray, fontSize = 12.sp)
        }

        CollectionListSection(
            title = "公开清单",
            collections = state.publicCollections,
            emptyText = "暂时没有公开清单",
            onSelectCollection = onSelectCollection
        )

        val selected = state.selectedDetail
        if (selected != null || state.isDetailLoading) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            if (state.isDetailLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在打开清单...", color = Color.Gray, fontSize = 13.sp)
                }
            } else if (selected != null) {
                CollectionDetailPanel(
                    detail = selected,
                    currentUserId = authState.session?.userId,
                    canCopy = authState.isLoggedIn && selected.collection.ownerId != authState.session?.userId,
                    isCopying = state.isCreating,
                    onCopy = { onCopyCollection(selected.collection) },
                    onPostClick = onPostClick
                )
            }
        }
    }
}

@Composable
private fun CollectionListSection(
    title: String,
    collections: List<VideoCollection>,
    emptyText: String,
    onSelectCollection: (VideoCollection) -> Unit
) {
    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    if (collections.isEmpty()) {
        Text(emptyText, color = Color.Gray, fontSize = 12.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        collections.take(8).forEach { collection ->
            CollectionSummaryRow(collection = collection, onClick = { onSelectCollection(collection) })
        }
    }
}

@Composable
private fun CollectionSummaryRow(
    collection: VideoCollection,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = visibilityIcon(collection.visibility),
            contentDescription = null,
            tint = Color.Cyan,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collection.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${visibilityLabel(collection.visibility)} · ${collection.itemCount} 条 · ${collection.ownerUsername}",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CollectionDetailPanel(
    detail: CollectionDetail,
    currentUserId: String?,
    canCopy: Boolean,
    isCopying: Boolean,
    onCopy: () -> Unit,
    onPostClick: (Post) -> Unit
) {
    val collection = detail.collection
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    collection.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (collection.description.isNotBlank()) {
                    Text(collection.description, color = Color.Gray, fontSize = 12.sp, maxLines = 3)
                }
                Text(
                    text = if (collection.ownerId == currentUserId) "我的 ${visibilityLabel(collection.visibility)} 清单" else "来自 ${collection.ownerUsername}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            if (canCopy) {
                OutlinedButton(enabled = !isCopying, onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制")
                }
            }
        }

        if (detail.items.isEmpty()) {
            Text("这个清单还没有视频。", color = Color.Gray, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.items.forEach { item ->
                    val video = item.videoRef
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                            .clickable {
                                onPostClick(
                                    Post(
                                        title = video.title,
                                        imageUrl = video.coverUrl.orEmpty(),
                                        link = video.sourceUrl,
                                        maker = video.maker
                                    )
                                )
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderShared,
                            contentDescription = null,
                            tint = Color.Cyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                video.title.ifBlank { video.sourceUrl },
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            video.maker?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("public") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建清单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    singleLine = true,
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("描述") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisibilityChip("public", visibility, "公开") { visibility = it }
                    VisibilityChip("unlisted", visibility, "链接") { visibility = it }
                    VisibilityChip("private", visibility, "私密") { visibility = it }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading && title.isNotBlank(),
                onClick = { onCreate(title, description, visibility) }
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(enabled = !isLoading, onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun VisibilityChip(
    value: String,
    selectedValue: String,
    label: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selectedValue == value,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

private fun visibilityLabel(value: String): String {
    return when (value) {
        "public" -> "公开"
        "unlisted" -> "链接可见"
        "private" -> "私密"
        else -> value
    }
}

private fun visibilityIcon(value: String): ImageVector {
    return when (value) {
        "public" -> Icons.Default.Public
        "unlisted" -> Icons.Default.Link
        "private" -> Icons.Default.Lock
        else -> Icons.Default.FolderShared
    }
}

private fun roleLabel(value: String?): String {
    return when (value) {
        "admin" -> "管理员"
        "moderator" -> "协管"
        else -> "普通用户"
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
                text = if (authState.isLoggedIn) "${roleLabel(authState.session?.role)} · 收藏可同步，评论可发布" else "登录后启用云收藏和评论",
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
            Text("已连接 NiceTV 后端 · ${roleLabel(authState.session?.role)}", color = Color.Gray, fontSize = 13.sp)
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
        authState.error?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        authState.message?.let {
            Text(
                text = it,
                color = Color.Cyan,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
