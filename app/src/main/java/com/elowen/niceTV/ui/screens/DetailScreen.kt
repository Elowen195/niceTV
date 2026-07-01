package com.elowen.niceTV.ui.screens

import androidx.compose.foundation.BorderStroke
import com.elowen.niceTV.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.pm.ActivityInfo
import android.app.Activity
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.data.model.VideoDetail
import com.elowen.niceTV.data.model.VideoSource
import com.elowen.niceTV.data.backend.CommentItem
import com.elowen.niceTV.data.backend.VideoCollection
import com.elowen.niceTV.data.manager.CacheKeyUtil
import com.elowen.niceTV.ui.components.VideoCard
import com.elowen.niceTV.data.network.CookieManager
import com.elowen.niceTV.ui.components.VideoPlayerComponent
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun DetailScreen(
    detail: VideoDetail,
    isVideoLoading: Boolean = false,
    isFavorite: Boolean = false,
    downloadState: androidx.media3.exoplayer.offline.Download? = null,
    downloadProgress: Float = -1f,
    downloadBytes: Long = 0L,
    showStreamingWarning: Boolean = false,
    isOffline: Boolean = false,
    comments: List<CommentItem> = emptyList(),
    areCommentsLoading: Boolean = false,
    isCommentPosting: Boolean = false,
    commentError: String? = null,
    isLoggedIn: Boolean = false,
    hasAccessCookies: Boolean = false,
    collections: List<VideoCollection> = emptyList(),
    areCollectionsLoading: Boolean = false,
    isAddingToCollection: Boolean = false,
    collectionMessage: String? = null,
    collectionError: String? = null,
    onToggleFavorite: () -> Unit = {},
    onAddToCollection: (String) -> Unit = {},
    onSubmitComment: (String) -> Unit = {},
    onLikeComment: (String) -> Unit = {},
    onStartDownload: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onBackClick: () -> Unit,
    onPostClick: (Post) -> Unit = {},
    onActorClick: (String, String?) -> Unit = { _, _ -> },
    onMakerClick: (String, String?) -> Unit = { _, _ -> },
    onTagClick: (String) -> Unit = {},
    onSwitchServer: (String) -> Unit = {},
    onRefreshAccess: () -> Unit = {},
    openingPostUrl: String? = null,
    isOpeningPost: Boolean = false,
    initialPlaybackPositionMs: Long = 0L,
    onPlaybackProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> }
) {
    val windowInfo = LocalWindowInfo.current
    val context = LocalContext.current
    val activity = context as? Activity
    val isTablet = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() } >= 600.dp
    var showCollectionPicker by remember { mutableStateOf(false) }
    val recommendationPosts = detail.recommendations.flatMap { it.posts }
    var selectedContentTab by remember(detail.postLink, isOffline) {
        mutableStateOf(if (isOffline) DetailContentTab.Comments else DetailContentTab.Recommendations)
    }

    val safeCacheProgress = if (downloadProgress >= 0f) downloadProgress else 0f

    val combinedDataSourceFactory = remember {
        androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            com.elowen.niceTV.NiceTVApplication.dataSourceFactory
        )
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(combinedDataSourceFactory)
            )
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    var failedServerUrls by remember(detail.postLink) { mutableStateOf<Set<String>>(emptySet()) }
    var accessPromptedForUrl by remember(detail.postLink) { mutableStateOf<String?>(null) }
    val currentVideoUrlForErrors by rememberUpdatedState(detail.videoUrl)
    val hasAccessCookiesState by rememberUpdatedState(hasAccessCookies)
    val onRefreshAccessState by rememberUpdatedState(onRefreshAccess)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val rawError = playbackErrorRaw(error)
                playerErrorMessage = playerErrorText(rawError)
                currentVideoUrlForErrors?.let { failedUrl ->
                    failedServerUrls = failedServerUrls + failedUrl
                    if (!hasAccessCookiesState &&
                        isLikelyAccessFailure(rawError) &&
                        accessPromptedForUrl != failedUrl
                    ) {
                        accessPromptedForUrl = failedUrl
                        onRefreshAccessState()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playerErrorMessage = null
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(detail.videoUrl, detail.videoReferer) {
        playerErrorMessage = null
        val videoUrl = detail.videoUrl
        if (!videoUrl.isNullOrEmpty()) {
            detail.videoReferer?.let {
                CookieManager.setReferer(videoUrl, it)
            }
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(videoUrl)
            val lower = videoUrl.substringBefore('?').lowercase(java.util.Locale.ROOT)
            if (lower.endsWith(".mp4")) {
                mediaItemBuilder.setCustomCacheKey(CacheKeyUtil.forUrl(videoUrl))
            }
            val mediaItem = mediaItemBuilder.build()
            exoPlayer.setMediaItem(mediaItem, initialPlaybackPositionMs.coerceAtLeast(0L))
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(detail.postLink, detail.videoUrl) {
        val videoUrl = detail.videoUrl
        if (videoUrl.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            delay(5000)
            onPlaybackProgress(
                exoPlayer.currentPosition.coerceAtLeast(0L),
                exoPlayer.duration.coerceAtLeast(0L)
            )
        }
    }

    DisposableEffect(detail.postLink, detail.videoUrl) {
        onDispose {
            if (!detail.videoUrl.isNullOrBlank()) {
                onPlaybackProgress(
                    exoPlayer.currentPosition.coerceAtLeast(0L),
                    exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }
    }

    var isFullScreen by remember { mutableStateOf(false) }
    var areControlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isFullScreen) {
        if (activity == null) return@LaunchedEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullScreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = activity.window
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    if (showCollectionPicker) {
        AddToCollectionDialog(
            collections = collections,
            isLoading = areCollectionsLoading,
            isAdding = isAddingToCollection,
            message = collectionMessage,
            error = collectionError,
            onDismiss = { showCollectionPicker = false },
            onAdd = onAddToCollection
        )
    }

    val nextServerName = remember(detail.servers, detail.videoUrl) {
        detail.servers.entries.firstOrNull { it.value.url != detail.videoUrl }?.key
    }
    val switchServer: (String) -> Unit = { name ->
        playerErrorMessage = null
        onSwitchServer(name)
    }

    if (isFullScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoPlayerComponent(
                player = exoPlayer,
                cacheProgress = safeCacheProgress,
                isFullScreen = isFullScreen,
                controlsVisible = areControlsVisible,
                onBack = { isFullScreen = false },
                onFullScreenToggle = { isFullScreen = it },
                onControlsVisibilityChanged = { areControlsVisible = it }
            )
            playerErrorMessage?.let { message ->
                PlayerErrorOverlay(
                    message = message,
                    onRetry = {
                        playerErrorMessage = null
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                    onSwitchServer = nextServerName?.let { { switchServer(it) } },
                    onRefreshAccess = onRefreshAccess,
                    onBack = { isFullScreen = false }
                )
            }
        }
        return
    }

    LaunchedEffect(detail) {
        if (BuildConfig.DEBUG) { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().shadow(16.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
            )

            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    Column(
                        modifier = Modifier
                            .weight(2.2f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        ) {
                            if (!detail.videoUrl.isNullOrEmpty()) {
                                VideoPlayerComponent(
                                    player = exoPlayer,
                                    cacheProgress = safeCacheProgress,
                                    isFullScreen = isFullScreen,
                                    controlsVisible = areControlsVisible,
                                    onBack = onBackClick,
                                    onFullScreenToggle = { isFullScreen = it },
                                    onControlsVisibilityChanged = { areControlsVisible = it }
                                )
                                playerErrorMessage?.let { message ->
                                    PlayerErrorOverlay(
                                        message = message,
                                        onRetry = {
                                            playerErrorMessage = null
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        },
                                        onSwitchServer = nextServerName?.let { { switchServer(it) } },
                                        onRefreshAccess = onRefreshAccess,
                                        onBack = onBackClick
                                    )
                                }
                            } else {
                                GlideImage(
                                    model = detail.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isVideoLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(48.dp),
                                            color = Color.Cyan
                                        )
                                    }
                                }
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        text = detail.title,
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (showStreamingWarning) {
                                        Text(
                                            text = "该视频可能并非针对在线播放进行优化，播放时可能出现卡顿或无法流畅播放。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Yellow
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    ActionRow(
                                        isFavorite = isFavorite,
                                        onToggleFavorite = onToggleFavorite,
                                        downloadState = downloadState,
                                        downloadProgress = downloadProgress,
                                        downloadBytes = downloadBytes,
                                        isOffline = isOffline,
                                        onStartDownload = onStartDownload,
                                        onOpenDownloads = onOpenDownloads,
                                        canAddToCollection = isLoggedIn && !isOffline,
                                        onOpenCollectionPicker = { showCollectionPicker = true }
                                    )
                                    ServerSelector(detail.servers, detail.videoUrl, failedServerUrls, switchServer)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (detail.cast.isNotEmpty()) {
                                        Text("演员", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            detail.cast.forEach { name ->
                                                val link = detail.castLinks[name]
                                                AssistChip(
                                                    onClick = { onActorClick(name, link) },
                                                    label = { Text(name, color = Color.White) },
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    if (detail.tags.isNotEmpty()) {
                                        Text("标签", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            detail.tags.forEach { tag ->
                                                SuggestionChip(
                                                    onClick = { onTagClick(tag) },
                                                    label = { Text(tag, color = Color.White) },
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    if (detail.maker.isNotEmpty()) {
                                        Text("制作商", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                                        SuggestionChip(
                                            onClick = { onMakerClick(detail.maker, detail.makerLink.ifBlank { null }) },
                                            label = { Text(detail.maker, color = Color.Cyan) },
                                            border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f)),
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    DetailCommentsSection(
                                        comments = comments,
                                        areLoading = areCommentsLoading,
                                        isPosting = isCommentPosting,
                                        error = commentError,
                                        isLoggedIn = isLoggedIn,
                                        onSubmitComment = onSubmitComment,
                                        onLikeComment = onLikeComment
                                    )
                                }
                            }
                        }
                    }
                    if (!isOffline) {
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.1f)))
                        Column(
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxHeight()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "推荐视频",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                detail.recommendations.flatMap { it.posts }.forEach { post ->
                                    item {
                                        val isCurrentOpening = isOpeningPost && openingPostUrl == post.link
                                        VideoCard(
                                            post = post,
                                            onClick = { onPostClick(post) },
                                            enabled = !isCurrentOpening,
                                            isLoading = isCurrentOpening
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        if (!detail.videoUrl.isNullOrEmpty()) {
                            VideoPlayerComponent(
                                player = exoPlayer,
                                cacheProgress = safeCacheProgress,
                                isFullScreen = isFullScreen,
                                controlsVisible = areControlsVisible,
                                    onBack = onBackClick,
                                    onFullScreenToggle = { isFullScreen = it },
                                    onControlsVisibilityChanged = { areControlsVisible = it }
                                )
                                playerErrorMessage?.let { message ->
                                    PlayerErrorOverlay(
                                        message = message,
                                        onRetry = {
                                            playerErrorMessage = null
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        },
                                        onSwitchServer = nextServerName?.let { { switchServer(it) } },
                                        onRefreshAccess = onRefreshAccess,
                                        onBack = onBackClick
                                    )
                                }
                            } else {
                            GlideImage(
                                model = detail.imageUrl,
                                contentScale = ContentScale.Fit,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isVideoLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = Color.Cyan
                                    )
                                }
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = detail.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (showStreamingWarning) {
                                    Text(
                                        text = "该视频可能并非针对在线播放进行优化，播放时可能出现卡顿或无法流畅播放。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Yellow
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                ActionRow(
                                    isFavorite = isFavorite,
                                    onToggleFavorite = onToggleFavorite,
                                    downloadState = downloadState,
                                    downloadProgress = downloadProgress,
                                    downloadBytes = downloadBytes,
                                    isOffline = isOffline,
                                    onStartDownload = onStartDownload,
                                    onOpenDownloads = onOpenDownloads,
                                    canAddToCollection = isLoggedIn && !isOffline,
                                    onOpenCollectionPicker = { showCollectionPicker = true }
                                )
                                ServerSelector(detail.servers, detail.videoUrl, failedServerUrls, switchServer)
                                Spacer(modifier = Modifier.height(16.dp))
                                if (detail.cast.isNotEmpty()) {
                                    Text("演员", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        detail.cast.forEach { name ->
                                            val link = detail.castLinks[name]
                                            AssistChip(
                                                onClick = { onActorClick(name, link) },
                                                label = { Text(name, color = Color.White) },
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                if (detail.tags.isNotEmpty()) {
                                    Text("标签", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        detail.tags.forEach { tag ->
                                            SuggestionChip(
                                                onClick = { onTagClick(tag) },
                                                label = { Text(tag, color = Color.White) },
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                if (detail.maker.isNotEmpty()) {
                                    Text(
                                        text = "制作商: ${detail.maker}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Cyan,
                                        modifier = Modifier.clickable { onMakerClick(detail.maker, detail.makerLink.ifBlank { null }) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                DetailContentTabs(
                                    selectedTab = selectedContentTab,
                                    commentsCount = comments.size,
                                    recommendationCount = recommendationPosts.size,
                                    showRecommendations = !isOffline,
                                    onSelectTab = { selectedContentTab = it }
                                )
                            }
                        }
                        when (selectedContentTab) {
                            DetailContentTab.Comments -> {
                                item {
                                    Column(
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 12.dp,
                                            bottom = 20.dp
                                        )
                                    ) {
                                        DetailCommentsSection(
                                            comments = comments,
                                            areLoading = areCommentsLoading,
                                            isPosting = isCommentPosting,
                                            error = commentError,
                                            isLoggedIn = isLoggedIn,
                                            onSubmitComment = onSubmitComment,
                                            onLikeComment = onLikeComment,
                                            showHeader = false
                                        )
                                    }
                                }
                            }
                            DetailContentTab.Recommendations -> if (!isOffline) {
                                if (recommendationPosts.isEmpty()) {
                                    item {
                                        Text(
                                            text = "暂无推荐视频",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                                items(recommendationPosts) { post ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        val isCurrentOpening = isOpeningPost && openingPostUrl == post.link
                                        VideoCard(
                                            post = post,
                                            onClick = { onPostClick(post) },
                                            enabled = !isCurrentOpening,
                                            isLoading = isCurrentOpening
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class DetailContentTab {
    Recommendations,
    Comments
}

@Composable
private fun DetailContentTabs(
    selectedTab: DetailContentTab,
    commentsCount: Int,
    recommendationCount: Int,
    showRecommendations: Boolean,
    onSelectTab: (DetailContentTab) -> Unit
) {
    val tabs = if (showRecommendations) {
        listOf(DetailContentTab.Recommendations, DetailContentTab.Comments)
    } else {
        listOf(DetailContentTab.Comments)
    }
    val selectedIndex = tabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SecondaryTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = Color.Transparent,
            contentColor = Color.Cyan
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onSelectTab(tab) },
                    text = {
                        Text(
                            text = when (tab) {
                                DetailContentTab.Recommendations -> {
                                    if (recommendationCount > 0) "推荐 $recommendationCount" else "推荐"
                                }
                                DetailContentTab.Comments -> {
                                    if (commentsCount > 0) "评论 $commentsCount" else "评论"
                                }
                            },
                            color = if (selectedIndex == index) Color.Cyan else Color.Gray
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailCommentsSection(
    comments: List<CommentItem>,
    areLoading: Boolean,
    isPosting: Boolean,
    error: String?,
    isLoggedIn: Boolean,
    onSubmitComment: (String) -> Unit,
    onLikeComment: (String) -> Unit,
    showHeader: Boolean = true
) {
    var body by remember { mutableStateOf("") }
    if (showHeader) {
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("评论", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
    }
    if (!isLoggedIn) {
        Text("登录后可以发表评论和点赞。", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
    }
    OutlinedTextField(
        value = body,
        onValueChange = { body = it.take(1000) },
        enabled = isLoggedIn && !isPosting,
        label = { Text(if (isLoggedIn) "写下评论" else "未登录") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = isLoggedIn && !isPosting && body.isNotBlank(),
            onClick = {
                onSubmitComment(body)
                body = ""
            }
        ) {
            Text(if (isPosting) "发送中" else "发送")
        }
        if (areLoading) {
            Spacer(modifier = Modifier.width(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
    error?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (!areLoading && comments.isEmpty()) {
        Text("还没有评论。", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        comments.forEach { comment ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(comment.username, color = Color.Cyan, style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        enabled = isLoggedIn,
                        onClick = { onLikeComment(comment.id) }
                    ) {
                        Text("赞 ${comment.likeCount}")
                    }
                }
                Text(comment.body, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AddToCollectionDialog(
    collections: List<VideoCollection>,
    isLoading: Boolean,
    isAdding: Boolean,
    message: String?,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入清单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    isLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在加载清单...")
                        }
                    }
                    collections.isEmpty() -> {
                        Text("还没有清单，先到我的页面新建一个。", color = Color.Gray)
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(collections) { collection ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                        .clickable(enabled = !isAdding) { onAdd(collection.id) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = null,
                                        tint = Color.Cyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            collection.title,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${collection.itemCount} 条 · ${collection.visibility}",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (isAdding) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在加入...")
                    }
                }
                message?.let { Text(it, color = Color.Cyan, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onSwitchServer: (() -> Unit)?,
    onRefreshAccess: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRetry) {
                    Text("重试")
                }
                if (onSwitchServer != null) {
                    Button(onClick = onSwitchServer) {
                        Text("切换线路")
                    }
                }
                Button(onClick = onRefreshAccess) {
                    Text("重新验证访问")
                }
                Button(onClick = onBack) {
                    Text("返回")
                }
            }
        }
    }
}

private fun playbackErrorRaw(error: PlaybackException): String {
    return listOf(
        error.message.orEmpty(),
        error.cause?.message.orEmpty(),
        error.cause?.cause?.message.orEmpty()
    ).joinToString(" ").lowercase(java.util.Locale.ROOT)
}

private fun playerErrorText(raw: String): String {
    return when {
        raw.contains("timeout") || raw.contains("timed out") ->
            "播放请求超时，请重试或切换线路"
        raw.contains("403") || raw.contains("forbidden") ->
            "当前线路无法访问，请切换线路或重新验证访问"
        raw.contains("404") || raw.contains("not found") ->
            "当前线路资源不存在，请切换线路"
        isLikelyAccessFailure(raw) ->
            "播放连接被关闭，可能缺少访问验证，请重新验证访问"
        raw.contains("behind live window") ->
            "播放位置已失效，请重试"
        else -> "播放失败，请重试或切换线路"
    }
}

private fun isLikelyAccessFailure(raw: String): Boolean {
    return raw.contains("connection closed") ||
        raw.contains("connection reset") ||
        raw.contains("unexpected end") ||
        raw.contains("unexpected end of stream") ||
        raw.contains("stream was reset")
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
fun ActionRow(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    downloadState: androidx.media3.exoplayer.offline.Download?,
    downloadProgress: Float,
    downloadBytes: Long,
    isOffline: Boolean,
    onStartDownload: () -> Unit,
    onOpenDownloads: () -> Unit,
    canAddToCollection: Boolean,
    onOpenCollectionPicker: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = onToggleFavorite,
            label = {
                Text(
                    text = if (isFavorite) "已收藏" else "收藏",
                    color = if (isFavorite) Color.Red else Color.White
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color.Red else Color.White
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (isFavorite) Color.Red.copy(alpha = 0.1f) else Color.Transparent
            ),
            border = AssistChipDefaults.assistChipBorder(
                enabled = true,
                borderColor = if (isFavorite) Color.Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f)
            )
        )

        val (text, icon, color) = when (downloadState?.state) {
            androidx.media3.exoplayer.offline.Download.STATE_COMPLETED ->
                Triple("已下载", Icons.Filled.DownloadDone, Color.Green)
            androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> {
                val p = if (downloadProgress >= 0f) downloadProgress.toInt() else downloadState.percentDownloaded.toInt()
                val label = if (p >= 0) {
                    "$p%"
                } else {
                    val bytesLabel = formatBytes(downloadBytes)
                    if (bytesLabel.isNotEmpty()) "下载中 $bytesLabel" else "下载中"
                }
                Triple(label, Icons.Filled.Downloading, Color.Yellow)
            }
            androidx.media3.exoplayer.offline.Download.STATE_QUEUED ->
                Triple("等待中", Icons.Filled.HourglassEmpty, Color.Yellow)
            androidx.media3.exoplayer.offline.Download.STATE_FAILED ->
                Triple("重试", Icons.Filled.Error, Color.Red)
            else -> Triple("下载", Icons.Filled.Download, Color.Cyan)
        }

        AssistChip(
            enabled = !isOffline,
            onClick = {
                if (!isOffline) {
                    if (downloadState == null || downloadState.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED) {
                        onStartDownload()
                    } else {
                        onOpenDownloads()
                    }
                }
            },
            label = { Text(text, color = color) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = "Download",
                    tint = color
                )
            },
            border = AssistChipDefaults.assistChipBorder(
                enabled = true,
                borderColor = color.copy(alpha = 0.3f)
            )
        )

        AssistChip(
            enabled = canAddToCollection,
            onClick = onOpenCollectionPicker,
            label = { Text("加入清单", color = if (canAddToCollection) Color.White else Color.Gray) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Add to collection",
                    tint = if (canAddToCollection) Color.Cyan else Color.Gray
                )
            },
            border = AssistChipDefaults.assistChipBorder(
                enabled = true,
                borderColor = if (canAddToCollection) Color.Cyan.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun ServerSelector(
    servers: Map<String, VideoSource>,
    currentVideoUrl: String?,
    failedServerUrls: Set<String> = emptySet(),
    onSwitchServer: (String) -> Unit
) {
    if (servers.isEmpty()) return
    Text("播放源", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        servers.forEach { (name, source) ->
            val isActive = source.url == currentVideoUrl
            val hasFailed = source.url != null && failedServerUrls.contains(source.url)
            val displayName = source.qualityLabel
                ?.takeIf { it.isNotBlank() }
                ?.let { "$name $it" }
                ?: name
            AssistChip(
                onClick = { onSwitchServer(name) },
                label = {
                    Text(
                        if (hasFailed) "$displayName 异常" else displayName,
                        color = when {
                            isActive -> Color.Cyan
                            hasFailed -> Color.Red
                            else -> Color.White
                        }
                    )
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        isActive -> Color.Cyan
                        hasFailed -> Color.Red.copy(alpha = 0.55f)
                        else -> Color.White.copy(alpha = 0.3f)
                    }
                ),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = when {
                        isActive -> Color.Cyan.copy(alpha = 0.15f)
                        hasFailed -> Color.Red.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    }
                )
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
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
