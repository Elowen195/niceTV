package com.elowen.niceTV.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.elowen.niceTV.NiceTVApplication
import com.elowen.niceTV.data.backend.AuthRepository
import com.elowen.niceTV.data.backend.BackendApiClient
import com.elowen.niceTV.data.backend.BackendRepository
import com.elowen.niceTV.data.db.entity.DownloadEntity
import com.elowen.niceTV.data.network.CookieManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.elowen.niceTV.data.repository.PostRepository
import com.elowen.niceTV.data.repository.TagRepository
import com.elowen.niceTV.data.scraper.HtmlScraper
import com.elowen.niceTV.ui.components.AppBackground
import com.elowen.niceTV.ui.components.rememberNotificationPermissionGate
import com.elowen.niceTV.ui.components.ResponsiveNavigation
import com.elowen.niceTV.ui.screens.HomeScreen
import com.elowen.niceTV.ui.screens.WebViewActivity
import com.elowen.niceTV.ui.theme.NiceTVTheme
import com.elowen.niceTV.ui.viewmodel.MainViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.elowen.niceTV.core.BoxManager
import com.elowen.niceTV.core.NetworkRestrictionManager
import com.elowen.niceTV.core.platform.proxy.ProxyRuntimeConfig
import com.elowen.niceTV.ui.viewmodel.CollectionsViewModel
import com.elowen.niceTV.ui.viewmodel.DetailViewModel
import com.elowen.niceTV.ui.viewmodel.AuthUiState
import com.elowen.niceTV.ui.viewmodel.AuthViewModel
import com.elowen.niceTV.ui.viewmodel.CollectionsUiState
import com.elowen.niceTV.data.model.VideoDetail
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.service.ProxyService
import com.elowen.niceTV.ui.screens.DetailScreen
import com.elowen.niceTV.ui.viewmodel.VideoListViewModel
import com.elowen.niceTV.ui.viewmodel.NavigationState
import com.elowen.niceTV.ui.screens.VideoListScreen
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

private const val DEFAULT_SOURCE_URL = "https://supjav.com/zh"
private const val CONTINUE_PREFS = "continue_watching"
private const val CONTINUE_TITLE = "title"
private const val CONTINUE_IMAGE = "image"
private const val CONTINUE_LINK = "link"
private const val CONTINUE_POSITION = "position_ms"
private const val CONTINUE_DURATION = "duration_ms"
private const val PLAYBACK_PROGRESS_PREFS = "playback_progress"
private const val WATCH_HISTORY_PREFS = "watch_history"
private const val WATCH_HISTORY_ITEMS = "items"
private const val MAX_WATCH_HISTORY = 12
private const val RESUME_MIN_POSITION_MS = 10_000L
private const val FINISHED_THRESHOLD_MS = 30_000L

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var detailViewModel: DetailViewModel
    private lateinit var videoListViewModel: VideoListViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var collectionsViewModel: CollectionsViewModel
    private lateinit var cookieManager: CookieManager

    private var selectedUrl by mutableStateOf<String?>(null)
    private var navState by mutableStateOf<NavigationState>(NavigationState.Home)
    private var isRefreshingCookies = false
    private var listReturnTarget by mutableStateOf<ListReturnTarget?>(null)
    private var homeNavState by mutableStateOf<NavigationState>(NavigationState.Home)
    private var homeListReturnTarget by mutableStateOf<ListReturnTarget?>(null)
    private var detailBackStack by mutableStateOf<List<DetailReturnTarget>>(emptyList())
    private var downloadReturnTarget by mutableStateOf<DetailReturnTarget?>(null)
    private var continueWatchingPost by mutableStateOf<Post?>(null)
    private var continueWatchingProgressText by mutableStateOf<String?>(null)
    private var watchHistoryPosts by mutableStateOf<List<Post>>(emptyList())
    private var hasAccessCookies by mutableStateOf(false)

    private data class ListReturnTarget(
        val navState: NavigationState,
        val detail: VideoDetail,
        val detailUrl: String,
        val isOffline: Boolean
    )

    private data class DetailReturnTarget(
        val navState: NavigationState,
        val detail: VideoDetail,
        val detailUrl: String,
        val isOffline: Boolean
    )

    private val webViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        isRefreshingCookies = false
        if (result.resultCode == RESULT_OK) {
            val cookies = result.data?.getStringExtra("cookies") ?: ""
            val ua = result.data?.getStringExtra("user_agent") ?: ""
            if (cookies.isBlank() || ua.isBlank()) {
                Toast.makeText(this, "访问验证失败，请重试", Toast.LENGTH_SHORT).show()
                loadHomeIfEmpty()
                return@registerForActivityResult
            }

            val cookieMap = mutableMapOf<String, String>()
            cookies.split(";").forEach { item ->
                val parts = item.trim().split("=", limit = 2)
                if (parts.size >= 2) {
                    cookieMap[parts[0]] = parts[1]
                }
            }

            cookieManager.saveCookies(
                asgfp2 = cookieMap["asgfp2"] ?: "",
                cfClearance = cookieMap["cf_clearance"] ?: ""
            )
            cookieManager.saveUserAgent(ua)
            hasAccessCookies = cookieManager.hasAccessCookies()

            viewModel.onCookieRefreshed()
            selectedUrl?.let { detailViewModel.loadDetail(it) }
            if (navState is NavigationState.VideoList) {
                val state = navState as NavigationState.VideoList
                if (state.isSearch) {
                    val query = videoListViewModel.state.value.searchQuery
                    val hasTags = videoListViewModel.state.value.selectedTags.isNotEmpty()
                    if (query.isNotBlank() || hasTags) {
                        videoListViewModel.performSearch(query)
                    }
                } else {
                    videoListViewModel.loadPosts(state.url)
                }
            }
        } else {
            Toast.makeText(this, "访问验证已取消", Toast.LENGTH_SHORT).show()
            loadHomeIfEmpty()
        }
    }

    @SuppressLint("ConfigurationScreenWidthHeight")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = android.graphics.Color.TRANSPARENT
            )
        )
        val db = com.elowen.niceTV.data.db.AppDatabase.getDatabase(this)
        cookieManager = CookieManager(this)
        hasAccessCookies = cookieManager.hasAccessCookies()
        com.elowen.niceTV.data.network.HttpClient.init(cookieManager)
        val scraper = HtmlScraper()
        val backendApi = BackendApiClient()
        val authRepository = AuthRepository(this, backendApi)
        val backendRepository = BackendRepository(backendApi, authRepository, db.favoriteDao())
        val repository = PostRepository(scraper, db.favoriteDao(), backendRepository)
        val tagRepository = TagRepository(scraper)
        val downloadRepository = com.elowen.niceTV.data.repository.DownloadRepository(this)
        continueWatchingPost = loadContinueWatchingPost()
        continueWatchingProgressText = loadContinueWatchingProgressText()
        watchHistoryPosts = loadWatchHistoryPosts()

        viewModel = MainViewModel(repository)
        detailViewModel = DetailViewModel(repository, downloadRepository, backendRepository, authRepository)
        videoListViewModel = VideoListViewModel(repository, tagRepository)
        authViewModel = AuthViewModel(authRepository, repository)
        collectionsViewModel = CollectionsViewModel(backendRepository, authRepository)
        val favoriteViewModel = com.elowen.niceTV.ui.viewmodel.FavoriteViewModel(repository)
        

        
        // [Smart Cleanup] Trigger garbage collection on start
        com.elowen.niceTV.data.manager.CacheManager.performGarbageCollection()
        
        val noticePrefs = getSharedPreferences("app_notices", android.content.Context.MODE_PRIVATE)
        if (noticePrefs.getBoolean("safety_notice_accepted", false)) {
            startInitialLoad()
        }
        setContent {
            val state by viewModel.state
            val detailState by detailViewModel.state
            val videoListState by videoListViewModel.state
            val favoriteState by favoriteViewModel.state
            val authState by authViewModel.state
            val collectionsState by collectionsViewModel.state
            val runWithNotificationPermission = rememberNotificationPermissionGate(
                deniedMessage = "未授予通知权限，下载仍会开始，但后台进度通知可能不可见"
            )
            val noticePrefs = remember {
                getSharedPreferences("app_notices", android.content.Context.MODE_PRIVATE)
            }
            var showSafetyNotice by remember {
                mutableStateOf(!noticePrefs.getBoolean("safety_notice_accepted", false))
            }
            
            // [Global 403 Handler]
            LaunchedEffect(Unit) {
                com.elowen.niceTV.data.network.HttpClient.cookieExpiredFlow.collect {
                    refreshCookies()
                }
            }
            LaunchedEffect(authState.session?.userId) {
                collectionsViewModel.refreshAll()
            }
            LaunchedEffect(
                detailState.detail?.postLink,
                detailState.detail?.videoUrl,
                detailState.isOffline
            ) {
                val detail = detailState.detail
                if (detail != null && !detailState.isOffline && !detail.videoUrl.isNullOrBlank()) {
                    saveContinueWatching(detail)
                }
            }
            NiceTVTheme {
                if (showSafetyNotice) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("使用与隐私提示") },
                        text = {
                            Text("请确认你有权访问所选内容。访问验证信息、收藏、下载记录和代理配置仅保存在此设备；卸载或清除数据后将无法恢复。")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    noticePrefs.edit { putBoolean("safety_notice_accepted", true) }
                                    showSafetyNotice = false
                                    startInitialLoad()
                                }
                            ) {
                                Text("我已了解")
                            }
                        }
                    )
                }
                BackHandler(enabled = selectedUrl != null) {
                    handleDetailBack()
                }
                BackHandler(enabled = selectedUrl == null && navState is NavigationState.VideoList) {
                    handleVideoListBack()
                }
                BackHandler(
                    enabled = selectedUrl == null &&
                        (navState is NavigationState.Favorites ||
                            navState is NavigationState.Collections ||
                            navState is NavigationState.Download ||
                            navState is NavigationState.User)
                ) {
                    if (navState is NavigationState.Favorites && favoriteState.isSelectionMode) {
                        favoriteViewModel.clearSelection()
                    } else if (navState is NavigationState.Download && downloadReturnTarget != null) {
                        downloadReturnTarget?.let { restoreDetailTarget(it) }
                        downloadReturnTarget = null
                    } else {
                        restoreHomeStack()
                    }
                }
                
                AppBackground {
                    val configuration = LocalConfiguration.current
                    val isTablet = configuration.screenWidthDp >= 600
                    Box(modifier = Modifier.fillMaxSize()) {

                        if (isTablet) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                ResponsiveNavigation(
                                    selectedIndex = selectedTopLevelIndex(),
                                    onItemSelected = { index ->
                                        favoriteViewModel.clearSelection()
                                        selectTopLevel(index)
                                    }
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                     androidx.compose.animation.AnimatedContent(
                                         targetState = navState,
                                         transitionSpec = {
                                             (
                                                 fadeIn(
                                                     animationSpec = tween(durationMillis = 180, delayMillis = 40)
                                                 ) + scaleIn(
                                                     initialScale = 0.98f,
                                                     animationSpec = tween(
                                                         durationMillis = 220,
                                                         easing = FastOutSlowInEasing
                                                     )
                                                 )
                                             ).togetherWith(
                                                 fadeOut(animationSpec = tween(durationMillis = 120)) +
                                                     scaleOut(
                                                         targetScale = 1.01f,
                                                         animationSpec = tween(durationMillis = 120)
                                                     )
                                             )
                                         },
                                         label = "ScreenTransition"
                                     ) { currentNavState ->
                                          ContentArea(
                                               navState = currentNavState,
                                               homeState = state,
                                               videoListState = videoListState,
                                               videoListViewModel = videoListViewModel,
                                               favoriteState = favoriteState,
                                               favoriteViewModel = favoriteViewModel,
                                               authState = authState,
                                               collectionsState = collectionsState,
                                               continueWatchingPost = continueWatchingPost,
                                               continueWatchingProgressText = continueWatchingProgressText,
                                               watchHistoryPosts = watchHistoryPosts,
                                               hasListReturnTarget = listReturnTarget != null,
                                               onPostClick = { post ->
                                                   openPostFromList(post)
                                               },
                                               onDownloadClick = { entity ->
                                                   detailBackStack = emptyList()
                                                   downloadReturnTarget = null
                                                   detailViewModel.onDetailCleared()
                                                   val playUrl = resolveOfflinePlaybackUrl(entity)
                                                   selectedUrl = playUrl
                                                   detailViewModel.loadOfflineDetail(
                                                       VideoDetail(
                                                           title = entity.title,
                                                           imageUrl = entity.coverUrl,
                                                           videoUrl = playUrl,
                                                           postLink = entity.postUrl, // Important for status checking
                                                           maker = entity.maker ?: "",
                                                           tags = entity.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                                                           cast = entity.cast?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                                                       )
                                                   )
                                               },
                                              onMoreClick = { section ->
                                                  detailBackStack = emptyList()
                                                  if (section.moreLink == "SEARCH_MODE_TRIGGER") {
                                                      listReturnTarget = null
                                                      navState = NavigationState.VideoList(DEFAULT_SOURCE_URL, "全站搜索", true)
                                                      videoListViewModel.init(DEFAULT_SOURCE_URL, "全站搜索", true, showPanel = true)
                                                  } else {
                                                     listReturnTarget = null
                                                     navState = NavigationState.VideoList(section.moreLink, section.title, false)
                                                     videoListViewModel.init(section.moreLink, section.title, false)
                                                 }
                                               },
                                               onClearWatchHistory = { clearWatchHistory() },
                                               onRefreshHome = { viewModel.loadPosts() },
                                               onRefreshCookies = { refreshCookies() },
                                               onBackFromList = { handleVideoListBack() },
                                               onSearch = { query -> videoListViewModel.performSearch(query) },
                                               onLoadMore = { videoListViewModel.loadMore() },
                                               runWithNotificationPermission = runWithNotificationPermission,
                                               onLogin = { login, password -> authViewModel.login(login, password) },
                                               onRegister = { username, password -> authViewModel.register(username, password) },
                                               onLogout = { authViewModel.logout() },
                                               onSyncFavorites = { authViewModel.syncFavorites() },
                                               onRefreshCollections = { collectionsViewModel.refreshAll() },
                                               onCreateCollection = { title, description, visibility ->
                                                   collectionsViewModel.createCollection(title, description, visibility)
                                               },
                                               onSelectCollection = { collectionsViewModel.selectCollection(it) },
                                               onCopyCollection = { collectionsViewModel.copyCollection(it) }
                                         )
                                     }
                                }
                            }
                        } else {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    // 始终显示导航栏
                                    ResponsiveNavigation(
                                        selectedIndex = selectedTopLevelIndex(),
                                        onItemSelected = { index ->
                                            favoriteViewModel.clearSelection()
                                            selectTopLevel(index)
                                        })
                                }
                            ) { paddingValues ->
                                Box(modifier = Modifier.padding(paddingValues)) {
                                    androidx.compose.animation.AnimatedContent(
                                        targetState = navState,
                                        transitionSpec = {
                                            (
                                                fadeIn(
                                                    animationSpec = tween(durationMillis = 180, delayMillis = 40)
                                                ) + scaleIn(
                                                    initialScale = 0.98f,
                                                    animationSpec = tween(
                                                        durationMillis = 220,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                            ).togetherWith(
                                                fadeOut(animationSpec = tween(durationMillis = 120)) +
                                                    scaleOut(
                                                        targetScale = 1.01f,
                                                        animationSpec = tween(durationMillis = 120)
                                                    )
                                            )
                                        },
                                        label = "ScreenTransition"
                                    ) { currentNavState ->
                                         ContentArea(
                                              navState = currentNavState,
                                              homeState = state,
                                              videoListState = videoListState,
                                              videoListViewModel = videoListViewModel,
                                              favoriteState = favoriteState,
                                              favoriteViewModel = favoriteViewModel,
                                              authState = authState,
                                              collectionsState = collectionsState,
                                              continueWatchingPost = continueWatchingPost,
                                              continueWatchingProgressText = continueWatchingProgressText,
                                              watchHistoryPosts = watchHistoryPosts,
                                              hasListReturnTarget = listReturnTarget != null,
                                             onPostClick = { post ->
                                                  openPostFromList(post)
                                              },
                                              onDownloadClick = { entity ->
                                                  detailBackStack = emptyList()
                                                  downloadReturnTarget = null
                                                  detailViewModel.onDetailCleared()
                                                  val playUrl = resolveOfflinePlaybackUrl(entity)
                                                  selectedUrl = playUrl
                                                  detailViewModel.loadOfflineDetail(
                                                      VideoDetail(
                                                          title = entity.title,
                                                          imageUrl = entity.coverUrl,
                                                          videoUrl = playUrl,
                                                          postLink = entity.postUrl,
                                                          maker = entity.maker ?: "",
                                                          tags = entity.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                                                          cast = entity.cast?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                                                      )
                                                  )
                                             },
                                             onMoreClick = { section ->
                                                 detailBackStack = emptyList()
                                                 if (section.moreLink == "SEARCH_MODE_TRIGGER") {
                                                     listReturnTarget = null
                                                    navState = NavigationState.VideoList(DEFAULT_SOURCE_URL, "全站搜索", true)
                                                    videoListViewModel.init(DEFAULT_SOURCE_URL, "全站搜索", true, showPanel = true)
                                                 } else {
                                                    listReturnTarget = null
                                                    navState = NavigationState.VideoList(section.moreLink, section.title, false)
                                                    videoListViewModel.init(section.moreLink, section.title, false)
                                                }
                                             },
                                             onClearWatchHistory = { clearWatchHistory() },
                                             onRefreshHome = { viewModel.loadPosts() },
                                             onRefreshCookies = { refreshCookies() },
                                             onBackFromList = { handleVideoListBack() },
                                             onSearch = { query -> videoListViewModel.performSearch(query) },
                                             onLoadMore = { videoListViewModel.loadMore() },
                                             runWithNotificationPermission = runWithNotificationPermission,
                                             onLogin = { login, password -> authViewModel.login(login, password) },
                                             onRegister = { username, password -> authViewModel.register(username, password) },
                                             onLogout = { authViewModel.logout() },
                                             onSyncFavorites = { authViewModel.syncFavorites() },
                                             onRefreshCollections = { collectionsViewModel.refreshAll() },
                                             onCreateCollection = { title, description, visibility ->
                                                 collectionsViewModel.createCollection(title, description, visibility)
                                             },
                                             onSelectCollection = { collectionsViewModel.selectCollection(it) },
                                             onCopyCollection = { collectionsViewModel.copyCollection(it) }
                                         )
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = selectedUrl != null,
                            enter = slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(
                                    durationMillis = 400,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeIn(),
                            exit = slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(
                                    durationMillis = 400,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                val currentDetail = detailState.detail
                                if (currentDetail == null && detailState.isLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color.Cyan)
                                    }
                                } else if (currentDetail == null && detailState.error != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                    ) {
                                        Text(
                                            text = detailState.error ?: "加载失败，请稍后重试",
                                            color = Color.Red,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Button(onClick = {
                                                selectedUrl?.let { detailViewModel.loadDetail(it) }
                                            }) {
                                                Text("重试")
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Button(onClick = {
                                                handleDetailBack()
                                            }) {
                                                Text("返回")
                                            }
                                        }
                                    }
                                } else {
                                    DetailScreen(
                                        detail = currentDetail ?: VideoDetail(title = "加载中.."),
                                        isVideoLoading = detailState.isVideoLoading,
                                        isFavorite = detailState.isFavorite,
                                        downloadState = detailState.download,
                                        downloadProgress = detailState.downloadProgress,
                                        downloadBytes = detailState.downloadBytes,
                                        showStreamingWarning = detailState.showStreamingWarning,
                                        isOffline = detailState.isOffline,
                                        comments = detailState.comments,
                                        areCommentsLoading = detailState.areCommentsLoading,
                                        isCommentPosting = detailState.isCommentPosting,
                                        commentError = detailState.commentError,
                                        isLoggedIn = authState.isLoggedIn,
                                        hasAccessCookies = hasAccessCookies,
                                        collections = collectionsState.myCollections,
                                        areCollectionsLoading = collectionsState.isLoading,
                                        isAddingToCollection = collectionsState.isAdding,
                                        collectionMessage = collectionsState.message,
                                        collectionError = collectionsState.error,
                                        onToggleFavorite = { detailViewModel.toggleFavorite() },
                                        onAddToCollection = { collectionId ->
                                            currentDetail?.let { detail ->
                                                collectionsViewModel.addCurrentDetailToCollection(collectionId, detail)
                                            }
                                        },
                                        onSubmitComment = { detailViewModel.submitComment(it) },
                                        onLikeComment = { detailViewModel.likeComment(it) },
                                        onStartDownload = {
                                            runWithNotificationPermission {
                                                detailViewModel.startDownload()
                                            }
                                        },
                                        onOpenDownloads = {
                                            downloadReturnTarget = currentDetailReturnTarget()
                                            detailViewModel.onDetailCleared()
                                            selectedUrl = null
                                            listReturnTarget = null
                                            navState = NavigationState.Download
                                        },
                                        onBackClick = { handleDetailBack() },
                                        onPostClick = { post ->
                                            openPostFromDetail(post)
                                        },
                                        onActorClick = { actorName, actorUrl ->
                                            val returnDetail = detailState.detail
                                            val returnDetailUrl = returnDetail?.postLink?.takeIf { it.isNotBlank() } ?: selectedUrl
                                            listReturnTarget = if (returnDetail != null && !returnDetailUrl.isNullOrBlank()) {
                                                ListReturnTarget(navState, returnDetail, returnDetailUrl, detailState.isOffline)
                                            } else {
                                                null
                                            }
                                            detailViewModel.onDetailCleared()
                                            selectedUrl = null
                                            val targetUrl = actorUrl?.takeIf { it.isNotBlank() }
                                            if (targetUrl != null) {
                                                navState = NavigationState.VideoList(targetUrl, "演员: $actorName", false)
                                                videoListViewModel.init(targetUrl, "演员: $actorName", false, showPanel = false)
                                            } else {
                                                navState = NavigationState.VideoList(DEFAULT_SOURCE_URL, "演员: $actorName", true)
                                                videoListViewModel.init(DEFAULT_SOURCE_URL, "演员: $actorName", true, showPanel = false, searchPrefix = "演员: $actorName")
                                                videoListViewModel.performSearch(actorName)
                                            }
                                        },
                                        onMakerClick = { makerName, makerUrl ->
                                            val returnDetail = detailState.detail
                                            val returnDetailUrl = returnDetail?.postLink?.takeIf { it.isNotBlank() } ?: selectedUrl
                                            listReturnTarget = if (returnDetail != null && !returnDetailUrl.isNullOrBlank()) {
                                                ListReturnTarget(navState, returnDetail, returnDetailUrl, detailState.isOffline)
                                            } else {
                                                null
                                            }
                                            detailViewModel.onDetailCleared()
                                            selectedUrl = null
                                            val targetUrl = makerUrl?.takeIf { it.isNotBlank() }
                                            if (targetUrl != null) {
                                                navState = NavigationState.VideoList(targetUrl, "制作商: $makerName", false)
                                                videoListViewModel.init(targetUrl, "制作商: $makerName", false, showPanel = false)
                                            } else {
                                                navState = NavigationState.VideoList(DEFAULT_SOURCE_URL, "制作商: $makerName", true)
                                                videoListViewModel.init(DEFAULT_SOURCE_URL, "制作商: $makerName", true, showPanel = false, searchPrefix = "制作商: $makerName")
                                                videoListViewModel.performSearch(makerName)
                                            }
                                        },
                                        onTagClick = { tag ->
                                            val returnDetail = detailState.detail
                                            val returnDetailUrl = returnDetail?.postLink?.takeIf { it.isNotBlank() } ?: selectedUrl
                                            listReturnTarget = if (returnDetail != null && !returnDetailUrl.isNullOrBlank()) {
                                                ListReturnTarget(navState, returnDetail, returnDetailUrl, detailState.isOffline)
                                            } else {
                                                null
                                            }
                                            detailViewModel.onDetailCleared()
                                            selectedUrl = null
                                            navState = NavigationState.VideoList(DEFAULT_SOURCE_URL, "标签: $tag", true)
                                            videoListViewModel.init(DEFAULT_SOURCE_URL, "标签: $tag", true, showPanel = false, searchPrefix = "标签: $tag")
                                            videoListViewModel.performSearch(tag)
                                        },
                                        onSwitchServer = { detailViewModel.switchServer(it) },
                                        onRefreshAccess = { refreshCookies() },
                                        openingPostUrl = selectedUrl,
                                        isOpeningPost = detailState.isLoading,
                                        initialPlaybackPositionMs = currentDetail?.let {
                                            playbackPositionFor(it, selectedUrl)
                                        } ?: 0L,
                                        onPlaybackProgress = { positionMs, durationMs ->
                                            currentDetail?.let {
                                                savePlaybackProgress(it, positionMs, durationMs)
                                            }
                                        }
                                    )
                                    if (detailState.isLoading) {
                                        DetailLoadingOverlay()
                                    } else if (detailState.error != null) {
                                        DetailInlineError(
                                            message = detailState.error ?: "加载失败，请稍后重试",
                                            onRetry = { selectedUrl?.let { detailViewModel.loadDetail(it) } },
                                            onBack = { handleDetailBack() }
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

    private fun startInitialLoad() {
        lifecycleScope.launch {
            val shouldWaitForProxy = restoreProxyIfNeeded()
            if (shouldWaitForProxy) {
                withTimeoutOrNull(10_000L) {
                    NetworkRestrictionManager.proxyReadyFlow
                        .first { it }
                }
            }
            viewModel.loadPosts()
        }
    }

    private fun loadHomeIfEmpty() {
        val homeState = viewModel.state.value
        if (homeState.sections.isEmpty() && !homeState.isLoading && homeState.error == null) {
            viewModel.loadPosts()
        }
    }

    private suspend fun restoreProxyIfNeeded(): Boolean {
        val activeNode = withContext(Dispatchers.IO) {
            NiceTVApplication.nodeStorage.getActiveNode()
        } ?: run {
            ProxyRuntimeConfig.setAutoStartEnabled(this, false)
            return false
        }

        if (BoxManager.isRunning) return true
        if (!ProxyRuntimeConfig.isAutoStartEnabled(this)) return false

        return try {
            val intent = Intent(this, ProxyService::class.java)
                .setAction(ProxyService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun selectedTopLevelIndex(): Int {
        return when {
            isHomeStackScreen(navState, listReturnTarget) -> 0
            navState is NavigationState.VideoList &&
                (navState as NavigationState.VideoList).isSearch &&
                listReturnTarget == null -> 1
            navState is NavigationState.Favorites -> 2
            navState is NavigationState.Collections -> 3
            navState is NavigationState.Download -> 4
            navState is NavigationState.User -> 5
            else -> -1
        }
    }

    private fun isHomeStackScreen(
        state: NavigationState = navState,
        returnTarget: ListReturnTarget? = listReturnTarget
    ): Boolean {
        return when (state) {
            is NavigationState.Home -> true
            is NavigationState.VideoList -> {
                returnTarget?.navState is NavigationState.Home ||
                    (!state.isSearch && returnTarget == null)
            }
            else -> false
        }
    }

    private fun rememberHomeStackIfNeeded() {
        if (!isHomeStackScreen()) return
        homeNavState = navState
        homeListReturnTarget = if (navState is NavigationState.VideoList) listReturnTarget else null
    }

    private fun restoreHomeStack() {
        navState = homeNavState
        listReturnTarget = if (homeNavState is NavigationState.VideoList) homeListReturnTarget else null
        downloadReturnTarget = null
        detailBackStack = emptyList()
    }

    private fun resetHomeStackToRoot() {
        selectedUrl = null
        detailViewModel.onDetailCleared()
        listReturnTarget = null
        homeNavState = NavigationState.Home
        homeListReturnTarget = null
        downloadReturnTarget = null
        detailBackStack = emptyList()
        navState = NavigationState.Home
    }

    private fun selectTopLevel(index: Int) {
        val wasHomeStackScreen = isHomeStackScreen()
        val currentList = navState as? NavigationState.VideoList
        val isContextList = listReturnTarget != null

        if (index == 0) {
            if (wasHomeStackScreen && navState !is NavigationState.Home) {
                resetHomeStackToRoot()
            } else if (wasHomeStackScreen) {
                navState = NavigationState.Home
            } else {
                if (selectedUrl != null) {
                    selectedUrl = null
                    detailViewModel.onDetailCleared()
                }
                restoreHomeStack()
            }
            return
        }

        rememberHomeStackIfNeeded()

        if (selectedUrl != null) {
            selectedUrl = null
            detailViewModel.onDetailCleared()
        }
        listReturnTarget = null
        downloadReturnTarget = null
        detailBackStack = emptyList()
        when (index) {
            1 -> {
                val alreadyGlobalSearch = currentList?.isSearch == true &&
                    currentList.url == DEFAULT_SOURCE_URL &&
                    currentList.title == "全站搜索"
                if (!isContextList && alreadyGlobalSearch) {
                    videoListViewModel.showSearchPanel()
                } else {
                    navState = NavigationState.VideoList(DEFAULT_SOURCE_URL, "全站搜索", true)
                    videoListViewModel.init(DEFAULT_SOURCE_URL, "全站搜索", true, showPanel = true)
                }
            }
            2 -> navState = NavigationState.Favorites
            3 -> navState = NavigationState.Collections
            4 -> navState = NavigationState.Download
            5 -> navState = NavigationState.User
        }
    }

    private fun openPostFromList(post: Post) {
        detailBackStack = emptyList()
        downloadReturnTarget = null
        selectedUrl = post.link
        detailViewModel.loadDetail(post.link)
    }

    private fun openPostFromDetail(post: Post) {
        if (post.link == selectedUrl) return
        if (!detailViewModel.state.value.isLoading) {
            pushCurrentDetailTarget()
        }
        selectedUrl = post.link
        detailViewModel.loadDetail(post.link)
    }

    private fun loadContinueWatchingPost(): Post? {
        val prefs = getSharedPreferences(CONTINUE_PREFS, android.content.Context.MODE_PRIVATE)
        val title = prefs.getString(CONTINUE_TITLE, null)?.takeIf { it.isNotBlank() } ?: return null
        val link = prefs.getString(CONTINUE_LINK, null)?.takeIf { it.isNotBlank() } ?: return null
        val image = prefs.getString(CONTINUE_IMAGE, "").orEmpty()
        return Post(title = title, imageUrl = image, link = link)
    }

    private fun loadContinueWatchingProgressText(): String? {
        val prefs = getSharedPreferences(CONTINUE_PREFS, android.content.Context.MODE_PRIVATE)
        val position = prefs.getLong(CONTINUE_POSITION, 0L)
        val duration = prefs.getLong(CONTINUE_DURATION, 0L)
        return progressLabel(position, duration)
    }

    private fun saveContinueWatching(detail: VideoDetail) {
        val link = detail.postLink.ifBlank {
            selectedUrl?.takeIf { it.isNotBlank() } ?: return
        }
        if (detail.title.isBlank()) return
        val post = Post(
            title = detail.title,
            imageUrl = detail.imageUrl,
            link = link,
            maker = detail.maker.takeIf { it.isNotBlank() },
            cast = detail.cast,
            tags = detail.tags
        )
        continueWatchingPost = post
        continueWatchingProgressText = loadContinueWatchingProgressText()
        getSharedPreferences(CONTINUE_PREFS, android.content.Context.MODE_PRIVATE).edit {
            putString(CONTINUE_TITLE, post.title)
            putString(CONTINUE_IMAGE, post.imageUrl)
            putString(CONTINUE_LINK, post.link)
        }
        saveWatchHistoryPost(post)
    }

    private fun playbackPositionFor(detail: VideoDetail, fallbackUrl: String?): Long {
        val id = playbackId(detail, fallbackUrl) ?: return 0L
        val prefs = getSharedPreferences(PLAYBACK_PROGRESS_PREFS, android.content.Context.MODE_PRIVATE)
        val key = playbackKey(id)
        val position = prefs.getLong("${key}_position", 0L)
        val duration = prefs.getLong("${key}_duration", 0L)
        return normalizedResumePosition(position, duration)
    }

    private fun savePlaybackProgress(detail: VideoDetail, positionMs: Long, durationMs: Long) {
        val id = playbackId(detail, selectedUrl) ?: return
        val key = playbackKey(id)
        val normalizedPosition = normalizedStoredPosition(positionMs, durationMs)
        getSharedPreferences(PLAYBACK_PROGRESS_PREFS, android.content.Context.MODE_PRIVATE).edit {
            putLong("${key}_position", normalizedPosition)
            putLong("${key}_duration", durationMs.coerceAtLeast(0L))
        }

        val continueLink = detail.postLink.ifBlank {
            selectedUrl?.takeIf { it.isNotBlank() } ?: return
        }
        getSharedPreferences(CONTINUE_PREFS, android.content.Context.MODE_PRIVATE).edit {
            putString(CONTINUE_TITLE, detail.title)
            putString(CONTINUE_IMAGE, detail.imageUrl)
            putString(CONTINUE_LINK, continueLink)
            putLong(CONTINUE_POSITION, normalizedPosition)
            putLong(CONTINUE_DURATION, durationMs.coerceAtLeast(0L))
        }
        continueWatchingProgressText = progressLabel(normalizedPosition, durationMs)

        if (detail.title.isNotBlank()) {
            saveWatchHistoryPost(
                Post(
                    title = detail.title,
                    imageUrl = detail.imageUrl,
                    link = continueLink,
                    maker = detail.maker.takeIf { it.isNotBlank() },
                    cast = detail.cast,
                    tags = detail.tags
                )
            )
        }
    }

    private fun loadWatchHistoryPosts(): List<Post> {
        val raw = getSharedPreferences(WATCH_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
            .getString(WATCH_HISTORY_ITEMS, "[]")
            .orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                    val link = item.optString("link").takeIf { it.isNotBlank() } ?: continue
                    add(
                        Post(
                            title = title,
                            imageUrl = item.optString("imageUrl"),
                            link = link,
                            maker = item.optString("maker").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveWatchHistoryPost(post: Post) {
        if (post.title.isBlank() || post.link.isBlank()) return
        val updated = (listOf(post) + watchHistoryPosts.filterNot { it.link == post.link })
            .take(MAX_WATCH_HISTORY)
        watchHistoryPosts = updated
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject()
                    .put("title", item.title)
                    .put("imageUrl", item.imageUrl)
                    .put("link", item.link)
                    .put("maker", item.maker.orEmpty())
            )
        }
        getSharedPreferences(WATCH_HISTORY_PREFS, android.content.Context.MODE_PRIVATE).edit {
            putString(WATCH_HISTORY_ITEMS, array.toString())
        }
    }

    private fun clearWatchHistory() {
        watchHistoryPosts = emptyList()
        continueWatchingPost = null
        continueWatchingProgressText = null
        getSharedPreferences(WATCH_HISTORY_PREFS, android.content.Context.MODE_PRIVATE).edit {
            remove(WATCH_HISTORY_ITEMS)
        }
        getSharedPreferences(CONTINUE_PREFS, android.content.Context.MODE_PRIVATE).edit {
            clear()
        }
    }

    private fun playbackId(detail: VideoDetail, fallbackUrl: String?): String? {
        return detail.postLink
            .ifBlank { fallbackUrl.orEmpty() }
            .ifBlank { detail.videoUrl.orEmpty() }
            .takeIf { it.isNotBlank() }
    }

    private fun playbackKey(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            String.format(Locale.ROOT, "%02x", byte)
        }
    }

    private fun normalizedResumePosition(positionMs: Long, durationMs: Long): Long {
        if (positionMs < RESUME_MIN_POSITION_MS) return 0L
        if (durationMs > 0L && durationMs - positionMs <= FINISHED_THRESHOLD_MS) return 0L
        return positionMs
    }

    private fun normalizedStoredPosition(positionMs: Long, durationMs: Long): Long {
        val safePosition = positionMs.coerceAtLeast(0L)
        if (safePosition < RESUME_MIN_POSITION_MS) return 0L
        if (durationMs > 0L && durationMs - safePosition <= FINISHED_THRESHOLD_MS) return 0L
        return safePosition
    }

    private fun progressLabel(positionMs: Long, durationMs: Long): String? {
        val normalizedPosition = normalizedResumePosition(positionMs, durationMs)
        if (normalizedPosition <= 0L) return null
        return if (durationMs > 0L) {
            "上次看到 ${formatPlaybackTime(normalizedPosition)} / ${formatPlaybackTime(durationMs)}"
        } else {
            "上次看到 ${formatPlaybackTime(normalizedPosition)}"
        }
    }

    private fun formatPlaybackTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    private fun currentDetailReturnTarget(): DetailReturnTarget? {
        val currentState = detailViewModel.state.value
        val detail = currentState.detail ?: return null
        val detailUrl = detail.postLink
            .takeIf { it.isNotBlank() }
            ?: selectedUrl?.takeIf { it.isNotBlank() }
            ?: detail.videoUrl?.takeIf { it.isNotBlank() }
            ?: return null
        return DetailReturnTarget(
            navState = navState,
            detail = detail,
            detailUrl = detailUrl,
            isOffline = currentState.isOffline
        )
    }

    private fun pushCurrentDetailTarget() {
        currentDetailReturnTarget()?.let { target ->
            detailBackStack = detailBackStack + target
        }
    }

    private fun restoreDetailTarget(target: DetailReturnTarget) {
        navState = target.navState
        if (target.isOffline) {
            detailViewModel.loadOfflineDetail(target.detail)
            selectedUrl = target.detail.videoUrl ?: target.detailUrl
        } else {
            selectedUrl = target.detailUrl
            detailViewModel.loadDetail(target.detailUrl)
        }
    }

    private fun handleDetailBack() {
        val previous = detailBackStack.lastOrNull()
        if (previous != null) {
            detailBackStack = detailBackStack.dropLast(1)
            detailViewModel.onDetailCleared()
            restoreDetailTarget(previous)
            return
        }

        detailViewModel.onDetailCleared()
        selectedUrl = null
    }

    private fun handleVideoListBack() {
        val navListState = navState as? NavigationState.VideoList ?: return
        val listState = videoListViewModel.state.value
        val returnTarget = listReturnTarget

        if (returnTarget != null) {
            listReturnTarget = null
            navState = returnTarget.navState
            if (returnTarget.isOffline) {
                detailViewModel.loadOfflineDetail(returnTarget.detail)
                selectedUrl = returnTarget.detail.videoUrl ?: returnTarget.detailUrl
            } else {
                val detailUrl = returnTarget.detail.postLink.ifBlank { returnTarget.detailUrl }
                if (detailUrl.isNotBlank()) {
                    selectedUrl = detailUrl
                    detailViewModel.loadDetail(detailUrl)
                } else {
                    selectedUrl = null
                }
            }
            return
        }

        if (navListState.isSearch) {
            val hasSearchInput = listState.searchQuery.isNotEmpty() || listState.selectedTags.isNotEmpty()
            if (hasSearchInput || !listState.isSearchPanelVisible) {
                videoListViewModel.init(
                    navListState.url,
                    navListState.title,
                    true,
                    showPanel = true,
                    searchPrefix = listState.searchPrefix
                )
                return
            }
        }

        listReturnTarget = null
        homeNavState = NavigationState.Home
        homeListReturnTarget = null
        navState = NavigationState.Home
    }

    private fun resolveOfflinePlaybackUrl(entity: DownloadEntity): String {
        if (entity.mergeState != DownloadEntity.MERGE_COMPLETED) {
            return entity.url
        }

        val mergedPath = entity.mergedPath?.takeIf { it.isNotBlank() } ?: return entity.url
        val mergedFile = File(mergedPath)
        return if (mergedFile.isFile && mergedFile.length() > 0L) {
            "file://$mergedPath"
        } else {
            entity.url
        }
    }

    private fun refreshCookies() {
        if (isRefreshingCookies) return
        isRefreshingCookies = true
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("url", DEFAULT_SOURCE_URL)
        }
        webViewLauncher.launch(intent)
    }
}

@androidx.compose.runtime.Composable
private fun DetailLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.Cyan)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "正在打开视频...",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun DetailInlineError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = MaterialTheme.shapes.large
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onRetry) {
                    Text("重试")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onBack) {
                    Text("返回")
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun ContentArea(
    navState: NavigationState,
    homeState: com.elowen.niceTV.ui.viewmodel.MainState,
    videoListState: com.elowen.niceTV.ui.viewmodel.VideoListState,
    videoListViewModel: VideoListViewModel,
    favoriteState: com.elowen.niceTV.ui.viewmodel.FavoriteState,
    favoriteViewModel: com.elowen.niceTV.ui.viewmodel.FavoriteViewModel,
    authState: AuthUiState,
    collectionsState: CollectionsUiState,
    continueWatchingPost: Post?,
    continueWatchingProgressText: String?,
    watchHistoryPosts: List<Post>,
    hasListReturnTarget: Boolean,
    onPostClick: (com.elowen.niceTV.data.model.Post) -> Unit,
    onDownloadClick: (com.elowen.niceTV.data.db.entity.DownloadEntity) -> Unit,
    onMoreClick: (com.elowen.niceTV.data.model.HomeSection) -> Unit,
    onClearWatchHistory: () -> Unit,
    onRefreshHome: () -> Unit,
    onRefreshCookies: () -> Unit,
    onBackFromList: () -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    runWithNotificationPermission: ((() -> Unit) -> Unit),
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSyncFavorites: () -> Unit,
    onRefreshCollections: () -> Unit,
    onCreateCollection: (String, String, String) -> Unit,
    onSelectCollection: (com.elowen.niceTV.data.backend.VideoCollection) -> Unit,
    onCopyCollection: (com.elowen.niceTV.data.backend.VideoCollection) -> Unit
) {
    when (navState) {
        is NavigationState.Home -> {
            HomeScreen(
                state = homeState,
                onPostClick = onPostClick,
                onMoreClick = onMoreClick,
                onClearWatchHistory = onClearWatchHistory,
                onRefresh = onRefreshHome,
                onRefreshCookies = onRefreshCookies,
                contentPadding = PaddingValues(0.dp),
                continueWatchingPost = continueWatchingPost,
                continueWatchingProgressText = continueWatchingProgressText,
                watchHistoryPosts = watchHistoryPosts
            )
        }

        is NavigationState.User -> {
             com.elowen.niceTV.ui.screens.UserScreen(
                 authState = authState,
                 onLogin = onLogin,
                 onRegister = onRegister,
                 onLogout = onLogout,
                 onSyncFavorites = onSyncFavorites
             )
        }

        is NavigationState.Collections -> {
             com.elowen.niceTV.ui.screens.CollectionsScreen(
                 authState = authState,
                 state = collectionsState,
                 onRefresh = onRefreshCollections,
                 onCreateCollection = onCreateCollection,
                 onSelectCollection = onSelectCollection,
                 onCopyCollection = onCopyCollection,
                 onPostClick = onPostClick
             )
        }

        is NavigationState.VideoList -> {
            VideoListScreen(
                state = videoListState,
                onBackClick = onBackFromList,
                onPostClick = onPostClick,
                onSearch = onSearch,
                onLoadMore = onLoadMore,
                onSearchToggle = { videoListViewModel.toggleSearchPanel() },
                onTagToggle = { videoListViewModel.toggleTag(it) },
                onClearFilters = { videoListViewModel.clearSearchFilters() },
                onRefreshCookies = onRefreshCookies,
                showBackButton = !navState.isSearch || hasListReturnTarget
            )
        }

        is NavigationState.Favorites -> {
            com.elowen.niceTV.ui.screens.FavoriteScreen(
                state = favoriteState,
                onSearch = { favoriteViewModel.onSearch(it) },
                onSortChange = { favoriteViewModel.onSortChange(it) },
                // [Updated] Multi-Select
                onToggleMaker = { favoriteViewModel.toggleMakerFilter(it) },
                onClearMaker = { favoriteViewModel.clearMakerFilter() },
                onToggleCast = { favoriteViewModel.toggleCastFilter(it) },
                onClearCast = { favoriteViewModel.clearCastFilter() },
                onToggleCastMatchLogic = { favoriteViewModel.toggleCastMatchLogic() },
                onToggleTag = { favoriteViewModel.toggleTagFilter(it) },
                onClearTag = { favoriteViewModel.clearTagFilter() },
                onToggleTagMatchLogic = { favoriteViewModel.toggleTagMatchLogic() },
                onClearAllFilters = { favoriteViewModel.clearAllFilters() },
                onSelectionModeChange = { favoriteViewModel.setSelectionMode(it) },
                onToggleSelection = { favoriteViewModel.toggleSelection(it) },
                onSelectAllFiltered = { favoriteViewModel.selectAllFiltered() },
                onClearSelection = { favoriteViewModel.clearSelection() },
                onDeleteSelected = { favoriteViewModel.deleteSelected() },
                onBackClick = onBackFromList,
                onPostClick = onPostClick,
                showBackButton = false // Favorites 是顶级 Tab，不显示返回按钮
            )
        }
        
        is NavigationState.Download -> {
             val context = androidx.compose.ui.platform.LocalContext.current
             val downloadRepository = remember { com.elowen.niceTV.data.repository.DownloadRepository(context) }
             val downloadViewModel: com.elowen.niceTV.ui.viewmodel.DownloadViewModel =
                 androidx.lifecycle.viewmodel.compose.viewModel(
                     factory = com.elowen.niceTV.ui.viewmodel.DownloadViewModelFactory(downloadRepository)
                 )
            
             com.elowen.niceTV.ui.screens.DownloadScreen(
                state = downloadViewModel.state.value,
                 onItemClick = { entity ->
                    onDownloadClick(entity)
                },
                onDeleteItem = { entity ->
                    downloadViewModel.removeDownload(entity.postUrl)
                },
                onRetryItem = { entity ->
                    runWithNotificationPermission {
                        downloadViewModel.retryDownload(entity)
                    }
                },
                onClearFailed = {
                    downloadViewModel.clearFailedDownloads()
                },
                onCleanCache = {
                    downloadViewModel.cleanUnusedCache()
                }
             )
        }
    }
}
