package com.elowen.niceTV.ui.screens

import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elowen.niceTV.core.BoxManager
import com.elowen.niceTV.core.platform.proxy.ProxyRuntimeConfig
import com.elowen.niceTV.core.platform.proxy.ProxyHttpClientFactory
import com.elowen.niceTV.data.models.Node
import com.elowen.niceTV.data.db.SubscriptionEntity
import com.elowen.niceTV.data.models.Protocol
import com.elowen.niceTV.data.parser.SubscriptionParser
import com.elowen.niceTV.data.repository.NodeRepository
import com.elowen.niceTV.data.repository.SubscriptionRepository
import com.elowen.niceTV.presentation.main.ProxyController
import com.elowen.niceTV.ui.components.rememberNotificationPermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
fun ProxySettingsScreen(onBack: () -> Unit) {
    val logTag = "ProxySettingsScreen"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nodeRepo = remember { NodeRepository(context) }
    val subRepo = remember { SubscriptionRepository(context) }
    val proxyController = remember(context) { ProxyController(context.applicationContext) }
    val runWithNotificationPermission = rememberNotificationPermissionGate(
        deniedMessage = "未授予通知权限，代理状态通知可能不可见"
    )

    var nodes by remember { mutableStateOf<List<Node>>(emptyList()) }
    var activeNodeId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddSubDialog by remember { mutableStateOf(false) }
    var showAddNodeDialog by remember { mutableStateOf(false) }
    var nodePendingDelete by remember { mutableStateOf<Node?>(null) }
    var isConnected by remember { mutableStateOf(BoxManager.isRunning) }
    var autoStartEnabled by remember { mutableStateOf(ProxyRuntimeConfig.isAutoStartEnabled(context)) }
    var isProxyActionInProgress by remember { mutableStateOf(false) }
    var isTestingProxy by remember { mutableStateOf(false) }
    var proxyTestMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val allNodes = nodeRepo.getAllNodes()
            val active = nodeRepo.getActiveNode()
            withContext(Dispatchers.Main) {
                nodes = allNodes
                activeNodeId = active?.id
                isConnected = BoxManager.isRunning && active != null
                autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                isLoading = false
            }
        }
    }

    fun refreshNodes() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                val allNodes = nodeRepo.getAllNodes()
                val active = nodeRepo.getActiveNode()
                withContext(Dispatchers.Main) {
                    nodes = allNodes
                    activeNodeId = active?.id
                    isConnected = BoxManager.isRunning && active != null
                    autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                    isLoading = false
                }
            }
        }
    }

    if (showAddSubDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddSubDialog = false },
            onConfirm = { name, url ->
                showAddSubDialog = false
                scope.launch {
                    isLoading = true
                    val addResult = withContext(Dispatchers.IO) {
                        runCatching {
                            val entity = SubscriptionEntity(
                                name = name,
                                url = url,
                                useProxyForUpdate = false,
                                autoUpdate = false,
                                updateInterval = 24,
                                lastUpdateTime = 0,
                                speedTestUrl = "",
                                speedTestTimeout = 5,
                                nodeCount = 0,
                                createdTime = System.currentTimeMillis()
                            )
                            val subId = subRepo.addSubscription(entity)
                            subRepo.updateSubscriptionContent(subId, false)
                                .onFailure { updateError ->
                                    runCatching { subRepo.deleteSubscription(subId) }
                                        .onFailure { cleanupError ->
                                            Log.e(logTag, "Failed to clean up empty subscription", cleanupError)
                                        }
                                    throw updateError
                                }
                                .getOrThrow()
                        }.onFailure { e ->
                            Log.e(logTag, "Failed to add subscription", e)
                        }
                    }
                    addResult
                        .onSuccess { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            refreshNodes()
                        }
                        .onFailure { e ->
                            isLoading = false
                            Toast.makeText(
                                context,
                                proxyImportErrorText(e, "订阅添加失败"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
        )
    }

    if (showAddNodeDialog) {
        AddNodeDialog(
            onDismiss = { showAddNodeDialog = false },
            onConfirm = { links ->
                showAddNodeDialog = false
                scope.launch {
                    isLoading = true
                    try {
                        val addedCount = withContext(Dispatchers.IO) {
                            val parsedNodes = SubscriptionParser.parseSubscription(links)
                            parsedNodes.forEach { node -> nodeRepo.addNode(node) }
                            parsedNodes.size
                        }
                        if (addedCount > 0) {
                            Toast.makeText(context, "已添加 $addedCount 个节点", Toast.LENGTH_SHORT).show()
                            refreshNodes()
                        } else {
                            isLoading = false
                            Toast.makeText(context, "未解析到有效节点链接", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        isLoading = false
                        Toast.makeText(context, "添加节点失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    nodePendingDelete?.let { node ->
        val deletingActiveNode = node.id == activeNodeId
        AlertDialog(
            onDismissRequest = { nodePendingDelete = null },
            title = { Text("删除节点", color = Color.White) },
            text = {
                Text(
                    if (deletingActiveNode) {
                        "确定删除“${node.name}”吗？当前代理会同时关闭。"
                    } else {
                        "确定删除“${node.name}”吗？"
                    },
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = deleteConfirm@{
                        if (isProxyActionInProgress) return@deleteConfirm
                        nodePendingDelete = null
                        isProxyActionInProgress = true
                        scope.launch {
                            try {
                                isLoading = true
                                val proxyWasRunning = BoxManager.isRunning
                                if (deletingActiveNode && proxyWasRunning) {
                                    val stopAccepted = proxyController.stop()
                                    if (stopAccepted) {
                                        delay(800)
                                    }
                                    if (!stopAccepted || BoxManager.isRunning) {
                                        isConnected = BoxManager.isRunning
                                        autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                        isLoading = false
                                        Toast.makeText(context, "代理关闭失败，未删除当前节点", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                }

                                withContext(Dispatchers.IO) {
                                    nodeRepo.deleteNode(node.id)
                                }
                                if (deletingActiveNode) {
                                    activeNodeId = null
                                    isConnected = false
                                    autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                    val message = if (proxyWasRunning) {
                                        "已删除当前节点并关闭代理"
                                    } else {
                                        "已删除当前节点"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                                refreshNodes()
                            } finally {
                                isProxyActionInProgress = false
                            }
                        }
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { nodePendingDelete = null }) {
                    Text("取消", color = Color.Gray)
                }
            },
            containerColor = Color.DarkGray
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Text(
                "代理设置",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Connection status indicator
            Text(
                text = if (isConnected) "已连接" else "未连接",
                color = if (isConnected) Color.Green else Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Action buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showAddSubDialog = true },
                    enabled = !isProxyActionInProgress && !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Cyan)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加订阅", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { showAddNodeDialog = true },
                    enabled = !isProxyActionInProgress && !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Cyan)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加节点", fontSize = 13.sp)
                }
            }
            val proxyIsRunning = isConnected || BoxManager.isRunning
            Button(
                onClick = proxyButton@{
                    if (isProxyActionInProgress) return@proxyButton
                    if (proxyIsRunning) {
                        isProxyActionInProgress = true
                        scope.launch {
                            try {
                                if (proxyController.stop()) {
                                    delay(800)
                                    isConnected = proxyController.status().isConnected
                                    autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                    if (!isConnected && !BoxManager.isRunning) {
                                        Toast.makeText(context, "已关闭代理", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "代理关闭失败", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    isConnected = BoxManager.isRunning
                                    autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                    Toast.makeText(context, "代理关闭失败", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isProxyActionInProgress = false
                            }
                        }
                    } else if (activeNodeId == null) {
                        Toast.makeText(context, "请先选择一个代理节点", Toast.LENGTH_SHORT).show()
                    } else {
                        isProxyActionInProgress = true
                        runWithNotificationPermission {
                            scope.launch {
                                try {
                                    val accepted = proxyController.start()
                                    if (accepted) {
                                        Toast.makeText(context, "代理服务正在启动", Toast.LENGTH_SHORT).show()
                                        delay(800)
                                        isConnected = proxyController.status().isConnected
                                        autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                        if (!isConnected) {
                                            Toast.makeText(context, "代理服务启动失败", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        isConnected = BoxManager.isRunning
                                        autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                        Toast.makeText(context, "代理服务启动失败", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    isProxyActionInProgress = false
                                }
                            }
                        }
                    }
                },
                enabled = !isProxyActionInProgress && !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (proxyIsRunning) Color.Red.copy(alpha = 0.7f) else Color.Cyan
                )
            ) {
                Text(
                    if (isProxyActionInProgress) "处理中..." else if (proxyIsRunning) "关闭代理" else "启动代理",
                    fontSize = 13.sp,
                    color = if (proxyIsRunning) Color.White else Color.Black
                )
            }
            Text(
                text = when {
                    autoStartEnabled && activeNodeId != null -> "已开启自动恢复：下次打开 App 会自动启动代理"
                    activeNodeId == null -> "选择节点并启动后，下次打开 App 可自动恢复代理"
                    else -> "自动恢复未开启：手动启动代理后会记住状态"
                },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = testClick@{
                    if (isTestingProxy || isProxyActionInProgress) return@testClick
                    if (!proxyIsRunning) {
                        proxyTestMessage = "请先启动代理"
                        Toast.makeText(context, "请先启动代理", Toast.LENGTH_SHORT).show()
                        return@testClick
                    }
                    isTestingProxy = true
                    proxyTestMessage = "正在测试代理..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val client = ProxyHttpClientFactory.createSocksClient(
                                    connectTimeoutSeconds = 5,
                                    readTimeoutSeconds = 5,
                                    writeTimeoutSeconds = 5
                                )
                                val request = Request.Builder()
                                    .url("https://www.gstatic.com/generate_204")
                                    .build()
                                client.newCall(request).execute().use { response ->
                                    response.code in 200..204
                                }
                            }
                        }
                        isTestingProxy = false
                        result
                            .onSuccess { ok ->
                                proxyTestMessage = if (ok) "代理可用" else "代理已启动，但目标站点返回异常"
                                Toast.makeText(context, proxyTestMessage, Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                proxyTestMessage = "代理不可用，请切换节点或检查网络"
                                Toast.makeText(context, proxyTestMessage, Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                enabled = !isTestingProxy && !isProxyActionInProgress && !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Cyan)
            ) {
                Icon(Icons.Default.Speed, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isTestingProxy) "测试中..." else "测试代理", fontSize = 13.sp)
            }
            proxyTestMessage?.let { message ->
                Text(
                    text = message,
                    color = when {
                        message.contains("可用") && !message.contains("不可用") -> Color.Green
                        message.contains("测试") -> Color.Gray
                        else -> Color.Yellow
                    },
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Node list
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Cyan)
            }
        } else if (nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无节点", color = Color.Gray, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("请添加订阅或手动添加节点", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodes, key = { it.id }) { node ->
                    NodeItem(
                        node = node,
                        isActive = node.id == activeNodeId,
                        enabled = !isProxyActionInProgress,
                        onClick = nodeClick@{
                            if (isProxyActionInProgress) return@nodeClick
                            if (node.id == activeNodeId) {
                                Toast.makeText(context, "当前节点已选中", Toast.LENGTH_SHORT).show()
                                return@nodeClick
                            }
                            val shouldRestart = BoxManager.isRunning
                            isProxyActionInProgress = true
                            val switchNode: () -> Unit = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            nodeRepo.setActiveNode(node.id)
                                        }
                                        activeNodeId = node.id
                                        if (shouldRestart) {
                                            if (proxyController.restart()) {
                                                Toast.makeText(context, "已切换节点，正在重启代理", Toast.LENGTH_SHORT).show()
                                                delay(800)
                                                isConnected = proxyController.status().isConnected
                                                autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                                if (isConnected) {
                                                    Toast.makeText(context, "已切换节点并重启代理", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "切换节点失败", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                isConnected = BoxManager.isRunning
                                                autoStartEnabled = ProxyRuntimeConfig.isAutoStartEnabled(context)
                                                Toast.makeText(context, "切换节点失败", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "已选择节点", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        isProxyActionInProgress = false
                                    }
                                }
                            }
                            if (shouldRestart) {
                                runWithNotificationPermission(switchNode)
                            } else {
                                switchNode()
                            }
                        },
                        onDelete = deleteClick@{
                            if (isProxyActionInProgress) return@deleteClick
                            nodePendingDelete = node
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeItem(
    node: Node,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isActive) Color.Cyan else Color.DarkGray.copy(alpha = 0.5f)
    val bgColor = if (isActive) Color.Cyan.copy(alpha = 0.1f) else Color.DarkGray.copy(alpha = 0.2f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Protocol icon
        val protocolColor = when (node.protocol) {
            Protocol.VLESS -> Color(0xFF4FC3F7)
            Protocol.HY2 -> Color(0xFFAB47BC)
            Protocol.DIRECT -> Color.Gray
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(protocolColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (node.protocol) {
                    Protocol.VLESS -> "VL"
                    Protocol.HY2 -> "H2"
                    Protocol.DIRECT -> "DC"
                },
                color = protocolColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                node.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${node.address}:${node.port}",
                color = Color.Gray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Latency
        if (node.latency > 0) {
            Text(
                "${node.latency}ms",
                color = when {
                    node.latency < 200 -> Color.Green
                    node.latency < 500 -> Color.Yellow
                    else -> Color.Red
                },
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Active indicator
        if (isActive) {
            Icon(
                Icons.Default.CheckCircle,
                "活动节点",
                tint = Color.Cyan,
                modifier = Modifier.size(20.dp)
            )
        }

        // Delete button
        IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "删除", tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

private fun proxyImportErrorText(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    val lower = message.lowercase(java.util.Locale.ROOT)
    return when {
        lower.contains("timeout") || lower.contains("timed out") ->
            "$fallback：网络请求超时"
        lower.contains("failed to connect") || lower.contains("unable to resolve") ->
            "$fallback：网络连接失败"
        lower.contains("parse") || lower.contains("解析") || lower.contains("invalid") ->
            "$fallback：链接格式不正确"
        message.isBlank() ->
            fallback
        else -> fallback
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("添加订阅", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("订阅名称", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.Cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅链接", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.Cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消", color = Color.Gray) }
                    Button(
                        onClick = {
                            if (name.isNotBlank() && url.isNotBlank()) {
                                onConfirm(name.trim(), url.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                    ) { Text("确定", color = Color.Black) }
                }
            }
        }
    }
}

@Composable
private fun AddNodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (links: String) -> Unit
) {
    var links by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("添加节点", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = links,
                    onValueChange = { links = it },
                    label = { Text("节点链接（支持多行）", color = Color.Gray) },
                    minLines = 4,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.Cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "支持 vless:// 与 hy2://（可一次粘贴多条）",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消", color = Color.Gray) }
                    Button(
                        onClick = {
                            if (links.isNotBlank()) {
                                onConfirm(links.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                    ) { Text("确定", color = Color.Black) }
                }
            }
        }
    }
}
