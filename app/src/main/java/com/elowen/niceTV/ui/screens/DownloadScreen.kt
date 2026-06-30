package com.elowen.niceTV.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.elowen.niceTV.BuildConfig
import com.elowen.niceTV.NiceTVApplication
import com.elowen.niceTV.data.db.entity.DownloadEntity
import com.elowen.niceTV.ui.viewmodel.DownloadItem
import com.elowen.niceTV.ui.viewmodel.DownloadListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FORCE_DEBUG_TOOLS_IN_RELEASE = false

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun DownloadScreen(
    state: DownloadListState,
    onItemClick: (DownloadEntity) -> Unit,
    onDeleteItem: (DownloadEntity) -> Unit,
    onRetryItem: (DownloadEntity) -> Unit,
    onClearFailed: () -> Unit,
    onCleanCache: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugTitle by remember { mutableStateOf("") }
    var debugInfo by remember { mutableStateOf("") }
    var debugWorking by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var confirmClearFailed by remember { mutableStateOf(false) }
    val runDebugTask: (String, suspend () -> String) -> Unit = runDebugTask@{ title, block ->
        if (debugWorking) return@runDebugTask
        scope.launch {
            debugWorking = true
            debugTitle = title
            debugInfo = "处理中..."
            val result = withContext(Dispatchers.IO) {
                runCatching { block() }.getOrElse { "错误: ${it.message ?: it}" }
            }
            debugInfo = result
            debugWorking = false
        }
    }

    pendingDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除下载", color = Color.White) },
            text = {
                Text(
                    "将移除该下载任务及其离线文件。",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteItem(entity)
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = Color.Cyan)
                }
            },
            containerColor = Color.DarkGray
        )
    }

    if (confirmClearFailed) {
        AlertDialog(
            onDismissRequest = { confirmClearFailed = false },
            title = { Text("清理失败项", color = Color.White) },
            text = {
                Text(
                    "将移除所有下载失败或合并失败的任务及其离线文件。",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearFailed = false
                        onClearFailed()
                    }
                ) {
                    Text("清理", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFailed = false }) {
                    Text("取消", color = Color.Cyan)
                }
            },
            containerColor = Color.DarkGray
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .statusBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "下载管理",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DownloadSummaryCard(
                    state = state,
                    onCleanCache = onCleanCache,
                    onClearFailed = { confirmClearFailed = true }
                )
            }
            if (BuildConfig.DEBUG || FORCE_DEBUG_TOOLS_IN_RELEASE) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "调试工具",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            runDebugTask("查看数据库") {
                                val downloads = NiceTVApplication.downloadDao.getAllDownloads().first()
                                buildDownloadsReport(downloads)
                            }
                        }) {
                            Text("查看数据库", color = Color.Cyan)
                        }
                        TextButton(onClick = {
                            runDebugTask("查看应用路径文件") {
                                buildPathReport(context)
                            }
                        }) {
                            Text("查看应用路径文件", color = Color.Cyan)
                        }
                        if (debugTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (debugWorking) "$debugTitle (处理中)" else debugTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        if (debugInfo.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = debugInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
            if (state.items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂无下载",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "下载的视频会显示在这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(state.items) { item ->
                    DownloadItemRow(
                        item = item,
                        onClick = { onItemClick(item.entity) },
                        onDelete = { pendingDelete = item.entity },
                        onRetry = { onRetryItem(item.entity) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSummaryCard(
    state: DownloadListState,
    onCleanCache: () -> Unit,
    onClearFailed: () -> Unit
) {
    val stats = state.stats
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.32f), MaterialTheme.shapes.medium)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "空间占用",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "共 ${formatBytes(stats.totalBytes)} · 缓存 ${formatBytes(stats.cacheBytes)} · 离线 ${formatBytes(stats.offlineBytes)}",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.isCleaning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.Cyan,
                    strokeWidth = 2.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("下载中 ${stats.activeCount}", Color.Yellow)
            StatusPill("已完成 ${stats.completedCount}", Color.Green)
            StatusPill("失败 ${stats.failedCount}", Color.Red)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCleanCache,
                enabled = !state.isCleaning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isCleaning) "清理中..." else "清理缓存", color = Color.Cyan)
            }
            Button(
                onClick = onClearFailed,
                enabled = stats.failedCount > 0 && !state.isCleaning,
                modifier = Modifier.weight(1f)
            ) {
                Text("清理失败项")
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun DownloadItemRow(
    item: DownloadItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val percent = item.progressPercent
    val stateCode = item.stateCode
    val isCompleted = stateCode == Download.STATE_COMPLETED || percent >= 99.9f
    val isFailed = stateCode == Download.STATE_FAILED
    val mergedFile = item.entity.mergedPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    val hasMerged = item.entity.mergeState == DownloadEntity.MERGE_COMPLETED &&
        (mergedFile?.let { it.isFile && it.length() > 0L } == true)
    val canOpen = isCompleted || hasMerged
    val mergeStatusText = when (item.entity.mergeState) {
        DownloadEntity.MERGE_COMPLETED -> if (hasMerged) "已合并" else "离线文件缺失"
        DownloadEntity.MERGE_IN_PROGRESS -> "合并中"
        DownloadEntity.MERGE_FAILED -> "合并失败"
        else -> null
    }
    val statusText = when {
        canOpen -> "已下载"
        stateCode == Download.STATE_DOWNLOADING -> if (percent >= 0f) "下载中 ${percent.toInt()}%" else "下载中"
        stateCode == Download.STATE_QUEUED -> "等待中"
        stateCode == Download.STATE_FAILED -> "下载失败"
        stateCode == Download.STATE_STOPPED -> "已暂停"
        stateCode == Download.STATE_REMOVING -> "删除中"
        stateCode == Download.STATE_RESTARTING -> "重试中"
        else -> "准备中"
    }
    
    val statusColor = when {
        canOpen -> Color.Green
        stateCode == Download.STATE_DOWNLOADING -> Color.Yellow
        stateCode == Download.STATE_FAILED -> Color.Red
        else -> Color.Gray
    }
    val mergeColor = when (item.entity.mergeState) {
        DownloadEntity.MERGE_COMPLETED -> if (hasMerged) Color.Green else Color.Red
        DownloadEntity.MERGE_IN_PROGRESS -> Color.Yellow
        DownloadEntity.MERGE_FAILED -> Color.Red
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
            .clickable(enabled = canOpen, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面
        Box(
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(16f / 9f)
                .background(Color.Black, MaterialTheme.shapes.small)
        ) {
            GlideImage(
                model = item.entity.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (stateCode == Download.STATE_DOWNLOADING && !isCompleted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (percent >= 0f) {
                        CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                        )
                    }
                }
            } else if (canOpen) {
                 Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.entity.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasMerged) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("离线", color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
            if (mergeStatusText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mergeStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = mergeColor
                )
            }
            if (item.storageBytes > 0L) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "占用 ${formatBytes(item.storageBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (stateCode == Download.STATE_DOWNLOADING && !isCompleted) {
                Spacer(modifier = Modifier.height(4.dp))
                if (percent >= 0f) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color.Yellow,
                        trackColor = Color.Gray
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color.Yellow,
                        trackColor = Color.Gray
                    )
                }
            }
        }
        
        if (isFailed) {
            TextButton(onClick = onRetry) {
                Text("重试", color = Color.Cyan)
            }
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Gray)
        }
    }
}

private fun buildDownloadsReport(downloads: List<DownloadEntity>): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return buildString {
        append("downloads=").append(downloads.size).append('\n')
        downloads.forEachIndexed { index, entity ->
            append(index + 1).append(". ").append(entity.title).append('\n')
            append("  postUrl=").append(entity.postUrl).append('\n')
            append("  url=").append(entity.url).append('\n')
            append("  coverUrl=").append(entity.coverUrl).append('\n')
            if (!entity.maker.isNullOrBlank()) {
                append("  maker=").append(entity.maker).append('\n')
            }
            if (!entity.tags.isNullOrBlank()) {
                append("  tags=").append(entity.tags).append('\n')
            }
            if (!entity.cast.isNullOrBlank()) {
                append("  cast=").append(entity.cast).append('\n')
            }
            append("  added=").append(formatter.format(Date(entity.addedTimestamp))).append('\n')
        }
    }
}

private fun buildPathReport(context: android.content.Context, maxEntriesPerPath: Int = 200): String {
    val paths = listOf(
        "filesDir" to context.filesDir,
        "cacheDir" to context.cacheDir,
        "externalCacheDir" to context.externalCacheDir,
        "externalFilesDir" to context.getExternalFilesDir(null),
        "media_cache" to NiceTVApplication.mediaCacheDir,
        "offline_media" to NiceTVApplication.offlineMediaDir
    )
    return buildString {
        paths.forEachIndexed { index, (label, dir) ->
            if (index > 0) append('\n')
            append('[').append(label).append(']').append('\n')
            append(buildSinglePathReport(dir, maxEntriesPerPath))
        }
    }
}

private data class FileTreeStats(
    var files: Int = 0,
    var dirs: Int = 0,
    var bytes: Long = 0,
    var truncated: Boolean = false
)

private fun buildSinglePathReport(dir: File?, maxEntries: Int): String {
    if (dir == null) return "<null>\n"
    if (!dir.exists()) return "${dir.absolutePath}\n<missing>\n"
    val entries = mutableListOf<String>()
    val stats = FileTreeStats()
    scanFileTree(dir, dir, maxEntries, entries, stats)
    return buildString {
        append(dir.absolutePath).append('\n')
        append("size=").append(formatBytes(stats.bytes))
        append(" files=").append(stats.files)
        append(" dirs=").append(stats.dirs).append('\n')
        entries.forEach { line ->
            append(line).append('\n')
        }
        if (stats.truncated) {
            append("... truncated ...\n")
        }
    }
}

private fun scanFileTree(
    root: File,
    current: File,
    maxEntries: Int,
    entries: MutableList<String>,
    stats: FileTreeStats
) {
    if (current.isDirectory) {
        stats.dirs += 1
        val rel = relativePath(root, current)
        if (rel.isNotEmpty() && entries.size < maxEntries) {
            entries.add("$rel/")
        } else if (rel.isNotEmpty() && entries.size >= maxEntries) {
            stats.truncated = true
        }
        val children = current.listFiles()?.sortedBy { it.name } ?: return
        for (child in children) {
            scanFileTree(root, child, maxEntries, entries, stats)
        }
    } else {
        stats.files += 1
        stats.bytes += current.length()
        val rel = relativePath(root, current)
        if (entries.size < maxEntries) {
            entries.add("$rel (${formatBytes(current.length())})")
        } else {
            stats.truncated = true
        }
    }
}

private fun relativePath(root: File, target: File): String {
    return runCatching {
        root.toPath().relativize(target.toPath()).toString()
    }.getOrElse { target.name }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> "%.1fGB".format(bytes / gb)
        bytes >= mb -> "%.1fMB".format(bytes / mb)
        bytes >= kb -> "%.0fKB".format(bytes / kb)
        else -> "${bytes}B"
    }
}
