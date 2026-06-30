package com.elowen.niceTV.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.ui.components.VideoCard
import com.elowen.niceTV.ui.viewmodel.VideoListState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VideoListScreen(
    state: VideoListState,
    onBackClick: () -> Unit,
    onPostClick: (Post) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSearchToggle: () -> Unit,
    onTagToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onRefreshCookies: () -> Unit,
    showBackButton: Boolean = true
) {
    val windowInfo = LocalWindowInfo.current
    val context = LocalContext.current
    val screenWidthDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 搜索框的本地状态
    var searchText by remember { mutableStateOf(state.searchQuery) }
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    var lastError by remember { mutableStateOf<String?>(null) }
    val historyPrefs = remember {
        context.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)
    }
    var searchHistory by remember {
        mutableStateOf(
            historyPrefs.getString("queries", "").orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
        )
    }
    
    // 列表状态
    val gridState = rememberSaveable(
        state.title,
        state.isSearchMode,
        saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver
    ) {
        androidx.compose.foundation.lazy.grid.LazyGridState()
    }

    fun submitSearch(query: String = searchText) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            val updated = (listOf(trimmed) + searchHistory.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(8)
            searchHistory = updated
            historyPrefs.edit { putString("queries", updated.joinToString("\n")) }
        }
        onSearch(trimmed)
        keyboardController?.hide()
    }

    fun clearFilters() {
        searchText = ""
        onClearFilters()
        keyboardController?.hide()
    }

    LaunchedEffect(state.title, state.searchQuery, state.searchPrefix) {
        searchText = state.searchQuery
    }

    // 面板显隐时请求焦点
    LaunchedEffect(state.isSearchPanelVisible) {
        if (state.isSearchPanelVisible) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // 监听滚动到末尾触发加载更多
    val shouldLoadMore = remember {
        androidx.compose.runtime.derivedStateOf {
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            lastVisibleItemIndex >= totalItemsCount - 2 // 提前2个触发
        }
    }

    LaunchedEffect(
        shouldLoadMore.value,
        state.isLoading,
        state.isLoadingMore,
        state.currentPage,
        state.totalPages
    ) {
        if (shouldLoadMore.value && !state.isLoading && !state.isLoadingMore && state.currentPage < state.totalPages) {
            onLoadMore()
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error
        if (error.isNullOrBlank()) {
            lastError = null
            return@LaunchedEffect
        }
        if (state.posts.isNotEmpty() && error != lastError) {
            lastError = error
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true
            )
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
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                if (showBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Big Title
                Text(
                    text = state.title.ifEmpty { "列表" },
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = if(showBackButton) 0.dp else 0.dp)
                )

                // Search Action
                if (state.title != "全站搜索") {
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (state.isSearchPanelVisible) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
            }

            // 搜索面板 (展开式)
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.isSearchPanelVisible,
                    enter = androidx.compose.animation.expandVertically() + fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // 1. 关键字输入框
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("搜索关键字...", color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedIndicatorColor = Color(0xFF00E5FF),
                                unfocusedIndicatorColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = Color(0xFF00E5FF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                submitSearch()
                            }),
                            leadingIcon = if (state.searchPrefix != null) {
                                {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        androidx.compose.material3.Surface(
                                            color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = state.searchPrefix,
                                                color = Color(0xFF00E5FF),
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }
                            } else null,
                            trailingIcon = {
                                IconButton(onClick = {
                                    submitSearch()
                                }) {
                                    Icon(Icons.Default.Search, null, tint = Color.Cyan)
                                }
                            }
                        )

                        if (searchHistory.isNotEmpty() && searchText.isBlank() && state.selectedTags.isEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "最近搜索",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                                )
                                TextButton(
                                    onClick = {
                                        searchHistory = emptyList()
                                        historyPrefs.edit { remove("queries") }
                                    }
                                ) {
                                    Text("清空", color = Color.Cyan, fontSize = 12.sp)
                                }
                            }
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                searchHistory.forEach { query ->
                                    AssistChip(
                                        onClick = {
                                            searchText = query
                                            submitSearch(query)
                                        },
                                        label = { Text(query, color = Color.White, fontSize = 12.sp) },
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 2. 标签过滤区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "类别筛选",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f).padding(start = 4.dp)
                            )
                            if (searchText.isNotBlank() || state.selectedTags.isNotEmpty()) {
                                TextButton(onClick = { clearFilters() }) {
                                    Text("清空筛选", color = Color.Cyan, fontSize = 12.sp)
                                }
                            }
                        }
                        
                        val tagScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(tagScrollState)
                        ) {
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                state.availableTags.forEach { tag ->
                                    val isSelected = state.selectedTags.contains(tag.slug)
                                    androidx.compose.material3.FilterChip(
                                        selected = isSelected,
                                        onClick = { onTagToggle(tag.slug) },
                                        label = { 
                                            Text(
                                                tag.name,
                                                fontSize = 12.sp
                                            ) 
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                            containerColor = Color.Transparent,
                                            labelColor = Color.White.copy(alpha = 0.7f),
                                            selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                            selectedLabelColor = Color(0xFF00E5FF)
                                        ),
                                        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                            borderColor = Color.White.copy(alpha = 0.2f),
                                            selectedBorderColor = Color(0xFF00E5FF),
                                            borderWidth = 1.dp,
                                            selectedBorderWidth = 1.dp,
                                            enabled = true,
                                            selected = isSelected
                                        )
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { 
                                submitSearch()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF)
                            )
                        ) {
                            Text("应用筛选", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

        // 内容区域
        Box(modifier = Modifier.weight(1f)) {
            if (state.isLoading && state.posts.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.Cyan)
            } else if (state.error != null && state.posts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error, color = Color.Red)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { 
                            if (searchText.isNotBlank() || state.selectedTags.isNotEmpty()) submitSearch() else onLoadMore()
                        }) { 
                            Text("重试") 
                        }
                        Button(onClick = onRefreshCookies) {
                            Text("重新验证访问")
                        }
                    }
                }
            } else if (state.posts.isEmpty()) {
                 // 仅在非搜索模式，或已发起搜索但结果为空时显示
                 if (!state.isSearchMode || state.searchQuery.isNotEmpty() || state.selectedTags.isNotEmpty()) {
                     val emptyText = if (state.searchQuery.isNotEmpty() || state.selectedTags.isNotEmpty()) {
                         "没有找到匹配内容"
                     } else {
                         "暂无内容"
                     }
                     Text(emptyText, color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                 }
            } else {
                // 计算Grid列数
                val gridCells = if (screenWidthDp > 600.dp) {
                     GridCells.Adaptive(minSize = 240.dp) // 更大的卡片
                } else {
                     GridCells.Fixed(1) // 手机上一列
                }

                LazyVerticalGrid(
                    columns = gridCells,
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = state.posts,
                        key = { it.link }
                    ) { post ->
                        VideoCard(post, onClick = { onPostClick(post) })
                    }
                    
                    // 加载更多指示器
                    if (state.isLoadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.height(24.dp).width(24.dp))
                            }
                        }
                    } else if (state.currentPage >= state.totalPages && state.posts.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("没有更多了", color = Color.Gray, fontSize = 12.sp)
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
