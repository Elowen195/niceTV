package com.elowen.niceTV.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.ui.components.VideoCard
import com.elowen.niceTV.ui.viewmodel.FavoriteState
import com.elowen.niceTV.ui.viewmodel.SortOrder

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    state: FavoriteState,
    onSearch: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    // [Updated] Multi-Select actions
    onToggleMaker: (String) -> Unit,
    onClearMaker: () -> Unit,
    onToggleCast: (String) -> Unit,
    onClearCast: () -> Unit,
    onToggleCastMatchLogic: () -> Unit,
    onToggleTag: (String) -> Unit,
    onClearTag: () -> Unit,
    onToggleTagMatchLogic: () -> Unit,
    onClearAllFilters: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onSelectAllFiltered: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onBackClick: () -> Unit,
    onPostClick: (Post) -> Unit,
    showBackButton: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    var showSortMenu by remember { mutableStateOf(false) }
    var showMakerMenu by remember { mutableStateOf(false) }
    var showCastMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val hasActiveFilters = state.searchQuery.isNotBlank() ||
        state.filterMakers.isNotEmpty() ||
        state.filterCasts.isNotEmpty() ||
        state.filterTags.isNotEmpty()

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("取消收藏", color = Color.White) },
            text = {
                Text(
                    "将取消收藏选中的 ${state.selectedLinks.size} 个视频。",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSelected()
                    }
                ) {
                    Text("取消收藏", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("返回", color = Color.Cyan)
                }
            },
            containerColor = Color.DarkGray
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black, // 纯黑背景
        topBar = {
            // 自定义沉浸式头部
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
                // 1. 标题行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isSelectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, "退出管理", tint = Color.White)
                        }
                    } else if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Text(
                        text = if (state.isSelectionMode) "已选 ${state.selectedLinks.size}" else "我的收藏",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isSelectionMode) {
                        IconButton(
                            onClick = onSelectAllFiltered,
                            enabled = state.filteredFavorites.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Done, "全选当前结果", tint = Color.Cyan)
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = state.selectedLinks.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "取消收藏",
                                tint = if (state.selectedLinks.isNotEmpty()) Color.Red else Color.Gray
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onSelectionModeChange(true) },
                            enabled = state.filteredFavorites.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Settings, "管理收藏", tint = Color.White)
                        }
                    }
                }

                // 2. 搜索栏
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索已收藏视频...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                    trailingIcon = if (state.searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearch("") }) {
                                Icon(Icons.Default.Close, "Clear", tint = Color.Gray)
                            }
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = Color.Cyan.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.Cyan,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                // 3. 筛选行 (Scrollable Chips)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sort Chip (Single Select)
                    item {
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { showSortMenu = true },
                                label = { Text(state.sortOrder.displayName) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    labelColor = Color.White,
                                    iconColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = Color.White.copy(alpha = 0.3f),
                                    enabled = true,
                                    selected = false
                                )
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                containerColor = Color.DarkGray
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.displayName, color = Color.White) },
                                        onClick = {
                                            onSortChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = if (state.sortOrder == order) {
                                            { Icon(Icons.Default.Done, null, tint = Color.Cyan) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                    
                    // Maker Filter (Multi-Select)
                    item {
                        Box {
                            val count = state.filterMakers.size
                            val label = if (count > 0) "厂商 ($count)" else "厂商"
                            val isSelected = count > 0

                            FilterChip(
                                selected = isSelected,
                                onClick = { showMakerMenu = true },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (isSelected) Color.Cyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                    labelColor = if (isSelected) Color.Cyan else Color.White,
                                    selectedContainerColor = Color.Cyan.copy(alpha = 0.2f),
                                    selectedLabelColor = Color.Cyan
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) Color.Cyan.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                            DropdownMenu(
                                expanded = showMakerMenu,
                                onDismissRequest = { showMakerMenu = false },
                                containerColor = Color.DarkGray
                            ) {
                                DropdownMenuItem(
                                    text = { Text("清空选择", color = Color.Red) },
                                    onClick = { 
                                        onClearMaker()
                                        showMakerMenu = false 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Close, null, tint = Color.Red) }
                                )
                                state.availableMakers.forEach { maker ->
                                    val checked = state.filterMakers.contains(maker)
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = null, // Handled by item click
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color.Cyan,
                                                        checkmarkColor = Color.Black,
                                                        uncheckedColor = Color.Gray
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(maker, color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            onToggleMaker(maker)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Cast Filter (Multi-Select with Match Logic)
                    item {
                        Box {
                            val count = state.filterCasts.size
                            val label = if (count > 0) "演员 ($count)" else "演员"
                            val isSelected = count > 0
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { showCastMenu = true },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (isSelected) Color.Cyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                    labelColor = if (isSelected) Color.Cyan else Color.White,
                                    selectedContainerColor = Color.Cyan.copy(alpha = 0.2f),
                                    selectedLabelColor = Color.Cyan
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) Color.Cyan.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                            DropdownMenu(
                                expanded = showCastMenu,
                                onDismissRequest = { showCastMenu = false },
                                containerColor = Color.DarkGray
                            ) {
                                // Logic Toggle
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (state.isCastMatchAny) "模式: 包含任一 (OR)" else "模式: 包含所有 (AND)", color = Color.Yellow)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Switch(
                                                checked = state.isCastMatchAny,
                                                onCheckedChange = null,
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.Cyan,
                                                    checkedTrackColor = Color.Cyan.copy(alpha = 0.3f)
                                                )
                                            )
                                        }
                                    },
                                    onClick = { onToggleCastMatchLogic() }
                                )
                                
                                DropdownMenuItem(
                                    text = { Text("清空选择", color = Color.Red) },
                                    onClick = { 
                                        onClearCast()
                                        showCastMenu = false 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Close, null, tint = Color.Red) }
                                )
                                state.availableCasts.forEach { cast ->
                                    val checked = state.filterCasts.contains(cast)
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color.Cyan,
                                                        checkmarkColor = Color.Black,
                                                        uncheckedColor = Color.Gray
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(cast, color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            onToggleCast(cast)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Tag Filter (Multi-Select with Match Logic)
                    item {
                        Box {
                            val count = state.filterTags.size
                            val label = if (count > 0) "标签 ($count)" else "标签"
                            val isSelected = count > 0
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { showTagMenu = true },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (isSelected) Color.Cyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                    labelColor = if (isSelected) Color.Cyan else Color.White,
                                    selectedContainerColor = Color.Cyan.copy(alpha = 0.2f),
                                    selectedLabelColor = Color.Cyan
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) Color.Cyan.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                            DropdownMenu(
                                expanded = showTagMenu,
                                onDismissRequest = { showTagMenu = false },
                                containerColor = Color.DarkGray
                            ) {
                                // Logic Toggle
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (state.isTagMatchAny) "模式: 包含任一 (OR)" else "模式: 包含所有 (AND)", color = Color.Yellow)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Switch(
                                                checked = state.isTagMatchAny,
                                                onCheckedChange = null,
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.Cyan,
                                                    checkedTrackColor = Color.Cyan.copy(alpha = 0.3f)
                                                )
                                            )
                                        }
                                    },
                                    onClick = { onToggleTagMatchLogic() }
                                )
                                
                                DropdownMenuItem(
                                    text = { Text("清空选择", color = Color.Red) },
                                    onClick = { 
                                        onClearTag()
                                        showTagMenu = false 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Close, null, tint = Color.Red) }
                                )
                                state.availableTags.forEach { tag ->
                                    val checked = state.filterTags.contains(tag)
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color.Cyan,
                                                        checkmarkColor = Color.Black,
                                                        uncheckedColor = Color.Gray
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(tag, color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            onToggleTag(tag)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${state.allFavorites.size} 个收藏，当前显示 ${state.filteredFavorites.size} 个",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasActiveFilters) {
                        TextButton(onClick = onClearAllFilters) {
                            Text("清空筛选", color = Color.Cyan)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        
        // Content
        if (state.filteredFavorites.isEmpty()) {
            // Empty State
            val hasAnyFavorite = state.allFavorites.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Text(
                         text = if (hasAnyFavorite) "没有符合筛选的收藏" else "还没有收藏内容",
                         color = Color.Gray,
                         style = MaterialTheme.typography.titleMedium
                     )
                     if (!hasAnyFavorite) {
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             text = "在详情页点收藏后会出现在这里",
                             color = Color.Gray,
                             style = MaterialTheme.typography.bodySmall
                         )
                     }
                 }
            }
        } else {
            val gridCells = if (screenWidthDp > 600) {
                 GridCells.Adaptive(minSize = 240.dp)
            } else {
                 GridCells.Fixed(1)
            }

            LazyVerticalGrid(
                columns = gridCells,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = state.filteredFavorites,
                    key = { it.link }
                ) { fav ->
                    // Animated Item
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        val post = Post(
                            title = fav.title,
                            imageUrl = fav.imageUrl,
                            link = fav.link
                        )
                        Box {
                            VideoCard(
                                post,
                                onClick = {
                                    if (state.isSelectionMode) {
                                        onToggleSelection(fav.link)
                                    } else {
                                        onPostClick(post)
                                    }
                                }
                            )
                            if (state.isSelectionMode) {
                                Checkbox(
                                    checked = state.selectedLinks.contains(fav.link),
                                    onCheckedChange = { onToggleSelection(fav.link) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color.Cyan,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
