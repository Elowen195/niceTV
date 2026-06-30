package com.elowen.niceTV.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.elowen.niceTV.ui.theme.NeonPurple

data class NavItem(
    val icon: ImageVector,
    val label: String
)

@Composable
fun ResponsiveNavigation(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val windowInfo = LocalWindowInfo.current
    val isLargeScreen = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() } >= 600.dp
    val navItems = listOf(
        NavItem(Icons.Default.Home, "首页"),
        NavItem(Icons.Default.Search, "搜索"),
        NavItem(Icons.Default.Favorite, "收藏"),
        NavItem(Icons.Default.Download, "下载"),
        NavItem(Icons.Default.Person, "我的")
    )

    if (isLargeScreen) {
        // 大屏：左侧导航栏
        NavigationRailWithAvatar(
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            navItems = navItems.dropLast(1),
            userItem = navItems.last(),
            userIndex = navItems.lastIndex,
            modifier = modifier
        )
    } else {
        // 小屏：底部导航栏
        BlurBottomBar(
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            navItems = navItems,
            modifier = modifier
        )
    }
}

@Composable
fun NavigationRailWithAvatar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    navItems: List<NavItem>,
    userItem: NavItem,
    userIndex: Int,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
            contentColor = NeonPurple,
            modifier = Modifier.fillMaxHeight()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 导航项目
            navItems.forEachIndexed { index, item ->
                NavigationRailItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    selected = selectedIndex == index,
                    onClick = { onItemSelected(index) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = NeonPurple,
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部头像
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selectedIndex == userIndex) NeonPurple else Color.Gray.copy(alpha = 0.3f))
                    .clickable { onItemSelected(userIndex) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    userItem.icon,
                    contentDescription = userItem.label,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun BlurBottomBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    navItems: List<NavItem>,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = NeonPurple,
            tonalElevation = 0.dp
        ) {
            navItems.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    selected = selectedIndex == index,
                    onClick = { onItemSelected(index) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonPurple,
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color.Transparent // 去掉点击时的圆形背景
                    )
                )
            }
        }
    }
}

