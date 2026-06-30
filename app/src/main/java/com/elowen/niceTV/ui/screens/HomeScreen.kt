package com.elowen.niceTV.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elowen.niceTV.data.model.HomeSection
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.ui.components.SectionHeader
import com.elowen.niceTV.ui.components.VideoCard
import com.elowen.niceTV.ui.viewmodel.MainState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainState,         // 传入整体状态
    onPostClick: (Post) -> Unit,
    onMoreClick: (HomeSection) -> Unit,
    onClearWatchHistory: () -> Unit = {},
    onRefresh: () -> Unit,    // 传入刷新回调
    onRefreshCookies: () -> Unit,
    contentPadding: PaddingValues, // 传入边距
    continueWatchingPost: Post? = null,
    continueWatchingProgressText: String? = null,
    watchHistoryPosts: List<Post> = emptyList()
) {
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    val isCompact = screenWidthDp < 600.dp
    val snackbarHostState = remember { SnackbarHostState() }
    var lastError by remember { mutableStateOf<String?>(null) }
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(state.error) {
        val error = state.error
        if (error.isNullOrBlank()) {
            lastError = null
            return@LaunchedEffect
        }
        if (state.sections.isNotEmpty() && error != lastError) {
            lastError = error
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "重试"
            )
            if (result == SnackbarResult.ActionPerformed) {
                onRefresh()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏 - 沉浸式大字风格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Black.copy(alpha = 0.8f))
                    )
                )
                .statusBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            // 标题行
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NiceTV",
                    style = if (isCompact) androidx.compose.material3.MaterialTheme.typography.titleLarge
                            else androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 内容区域
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.error != null) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = Color.Red)
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onRefresh) { Text("重试") }
                        Button(onClick = onRefreshCookies) { Text("重新验证访问") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    continueWatchingPost?.let { post ->
                        item(key = "continue_watching_header") {
                            SectionHeader(
                                title = "继续观看",
                                showMore = false,
                                onMoreClick = {}
                            )
                        }
                        item(key = "continue_watching_${post.link}") {
                            val cardWidth = when {
                                screenWidthDp >= 900.dp -> 260.dp
                                screenWidthDp >= 600.dp -> 240.dp
                                screenWidthDp >= 400.dp -> 170.dp
                                else -> 150.dp
                            }
                            Box(
                                modifier = Modifier
                                    .width(cardWidth)
                                    .padding(horizontal = if (isCompact) 8.dp else 12.dp)
                            ) {
                                Column {
                                    VideoCard(post, onClick = { onPostClick(post) })
                                    continueWatchingProgressText?.let { progress ->
                                        Text(
                                            text = progress,
                                            color = Color.Cyan,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val visibleHistory = watchHistoryPosts.filterNot { history ->
                        history.link == continueWatchingPost?.link
                    }
                    if (visibleHistory.isNotEmpty()) {
                        item(key = "watch_history_header") {
                            SectionHeader(
                                title = "最近观看",
                                showMore = true,
                                actionText = "清空",
                                onMoreClick = onClearWatchHistory
                            )
                        }
                        item(key = "watch_history_content") {
                            val cardWidth = when {
                                screenWidthDp >= 900.dp -> 220.dp
                                screenWidthDp >= 600.dp -> 200.dp
                                screenWidthDp >= 400.dp -> 145.dp
                                else -> 128.dp
                            }
                            val cardHeight = (cardWidth - 8.dp) * (216f / 320f) + 8.dp
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(1),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cardHeight),
                                contentPadding = PaddingValues(horizontal = if (isCompact) 8.dp else 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(
                                    items = visibleHistory,
                                    key = { post -> "history_${post.link}" }
                                ) { post ->
                                    Box(modifier = Modifier.width(cardWidth)) {
                                        VideoCard(post, onClick = { onPostClick(post) })
                                    }
                                }
                            }
                        }
                    }
                    state.sections.forEach { section ->
                        // 1. 板块标题 (加上 key 提高重绘效率)
                        item(key = "header_${section.title}") {
                            SectionHeader(
                                title = section.title,
                                showMore = section.moreLink.isNotBlank()
                            ) {
                                onMoreClick(section)
                            }
                        }
                        // 2. 板块内容 - 两行水平滑动
                        item(key = "content_${section.title}") {
                            // 根据屏幕宽度计算卡片宽度和高度
                            val cardWidth = when {
                                screenWidthDp >= 900.dp -> 240.dp  // 大屏
                                screenWidthDp >= 600.dp -> 220.dp  // 平板
                                screenWidthDp >= 400.dp -> 150.dp  // 大手机
                                else -> 130.dp                     // 小手机
                            }
                            // 卡片高度 = (宽度 - padding*2) * 图片比例 + padding*2
                            // VideoCard 内部 padding 4dp, aspectRatio 320:216
                            val cardHeight = (cardWidth - 8.dp) * (216f / 320f) + 8.dp
                            // 两行高度
                            val gridHeight = cardHeight * 2
                            
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(gridHeight),
                                contentPadding = PaddingValues(horizontal = if (isCompact) 8.dp else 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(
                                    items = section.posts,
                                    key = { post -> post.link.ifBlank { "${section.title}_${post.title}_${post.imageUrl}" } }
                                ) { post ->
                                    Box(modifier = Modifier.width(cardWidth)) {
                                        VideoCard(post, onClick = { onPostClick(post) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}
