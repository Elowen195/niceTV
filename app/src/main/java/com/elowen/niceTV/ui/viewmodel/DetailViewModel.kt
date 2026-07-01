package com.elowen.niceTV.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elowen.niceTV.data.model.VideoDetail
import com.elowen.niceTV.data.backend.AuthRepository
import com.elowen.niceTV.data.backend.BackendRepository
import com.elowen.niceTV.data.backend.CommentItem
import com.elowen.niceTV.data.repository.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest

import androidx.media3.exoplayer.offline.Download
import com.elowen.niceTV.data.repository.DownloadRepository
import java.util.Locale

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
data class DetailState(
    val isLoading: Boolean = false,
    val isVideoLoading: Boolean = false,
    val detail: VideoDetail? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val download: Download? = null, // Add Download state
    val showStreamingWarning: Boolean = false,
    val isOffline: Boolean = false,
    // Snapshot fields to force UI updates even if Download object is mutated in-place.
    val downloadProgress: Float = -1f,
    val downloadStateCode: Int? = null,
    val downloadBytes: Long = 0L,
    val remoteVideoRefId: String? = null,
    val comments: List<CommentItem> = emptyList(),
    val areCommentsLoading: Boolean = false,
    val isCommentPosting: Boolean = false,
    val commentError: String? = null
)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DetailViewModel(
    private val repository: PostRepository,
    private val downloadRepository: DownloadRepository,
    private val backendRepository: BackendRepository? = null,
    private val authRepository: AuthRepository? = null
) : ViewModel() {
    val state = mutableStateOf(DetailState())
    private var favoriteJob: Job? = null
    private var downloadJob: Job? = null
    private var loadJob: Job? = null
    private var cloudJob: Job? = null
    private var fastStartCheckedUrl: String? = null

    fun toggleFavorite() {
        val detail = state.value.detail ?: return
        if (detail.postLink.isBlank()) return
        viewModelScope.launch {
            repository.toggleFavorite(detail)
        }
    }

    private fun checkFavoriteStatus(url: String) {
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch {
            repository.isFavorite(url).collectLatest { isFav ->
                state.value = state.value.copy(isFavorite = isFav)
            }
        }
    }

    fun loadDetail(url: String) {
        loadJob?.cancel()
        checkFavoriteStatus(url)
        
        loadJob = viewModelScope.launch {
            val transitionDetail = state.value.detail?.copy(
                videoUrl = null,
                videoReferer = null
            )
            state.value = state.value.copy(
                isLoading = true,
                error = null,
                detail = transitionDetail,
                isFavorite = false,
                showStreamingWarning = false,
                isOffline = false,
                download = null,
                downloadProgress = -1f,
                downloadStateCode = null,
                downloadBytes = 0L,
                remoteVideoRefId = null,
                comments = emptyList(),
                areCommentsLoading = false,
                isCommentPosting = false,
                commentError = null
            )
            
            // [Offline/Direct Link Support]
            val lower = url.substringBefore('?').lowercase(Locale.ROOT)
            if (lower.contains(".m3u8") || lower.contains(".mp4")) {
                 val detail = VideoDetail(
                      title = "本地播放", 
                      videoUrl = url,
                      imageUrl = ""
                 )
                 state.value = state.value.copy(
                      isLoading = false,
                      isVideoLoading = false,
                      detail = detail,
                      isFavorite = false,
                      showStreamingWarning = false,
                      isOffline = false,
                      download = null,
                      downloadProgress = -1f,
                      downloadStateCode = null,
                      downloadBytes = 0L,
                      remoteVideoRefId = null,
                      comments = emptyList(),
                      areCommentsLoading = false,
                      isCommentPosting = false,
                      commentError = null
                 )
                 checkDownloadStatus(url)
                 maybeCheckStreamingWarning(url)
                 return@launch
             }
            
            try {
                // 1. 第一阶段：快速解析元数据和推荐位
                val (partialDetail, doc) = repository.fetchPartialDetail(url)
                
                // 立即更新 UI，显示文字、图片和推荐列表
                state.value = state.value.copy(isLoading = false, isVideoLoading = true, detail = partialDetail)
                loadCloudDetail(partialDetail)

                // 2. 第二阶段：异步解析耗时的视频播放地址（全部 4 个服务器）
                val servers = repository.resolveVideoUrlFromDoc(doc)
                val defaultSource = servers.values.maxWithOrNull(
                    compareBy<com.elowen.niceTV.data.model.VideoSource> { it.qualityBandwidth ?: -1 }
                        .thenBy { serverFallbackRank(it.name) }
                )

                // 更新详情对象，包含所有服务器和默认选中的地址
                state.value = state.value.copy(
                    isVideoLoading = false,
                    detail = state.value.detail?.copy(
                        servers = servers,
                        videoUrl = defaultSource?.url,
                        videoReferer = defaultSource?.referer
                    )
                )
                checkDownloadStatus(url)
                defaultSource?.url?.let { vUrl ->
                    maybeCheckStreamingWarning(vUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(
                    isLoading = false, 
                    isVideoLoading = false, 
                    error = userFacingError(e)
                )
            }
        }
    }

    private fun checkDownloadStatus(postId: String) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloadRepository.observeDownloadProgress(postId).collectLatest { download ->
                val progress = calculateDownloadProgress(download)
                val stateCode = download?.state
                state.value = state.value.copy(
                    download = download,
                    downloadProgress = progress,
                    downloadStateCode = stateCode,
                    downloadBytes = download?.bytesDownloaded ?: 0L
                )
            }
        }
    }

    fun switchServer(serverName: String) {
        val detail = state.value.detail ?: return
        val source = detail.servers[serverName] ?: return
        state.value = state.value.copy(
            detail = detail.copy(
                videoUrl = source.url,
                videoReferer = source.referer
            ),
            showStreamingWarning = false
        )
        source.url?.let { maybeCheckStreamingWarning(it) }
    }

    private fun serverFallbackRank(name: String): Int {
        return when (name.uppercase(Locale.ROOT)) {
            "FST" -> 4
            "TV" -> 3
            "VOE" -> 2
            "ST" -> 1
            else -> 0
        }
    }

    fun startDownload() {
        val detail = state.value.detail ?: return
        val videoUrl = detail.videoUrl ?: return
        val postId = detail.postLink.ifBlank { videoUrl }
        if (postId.isBlank()) return
        downloadRepository.startDownload(
            title = detail.title,
            postUrl = postId,
            videoUrl = videoUrl,
            coverUrl = detail.imageUrl,
            maker = detail.maker,
            tags = detail.tags,
            cast = detail.cast
        )
    }

    fun onDetailCleared() {
        val url = state.value.detail?.videoUrl
        downloadRepository.checkAndCleanCache(url)
        favoriteJob?.cancel()
        favoriteJob = null
        downloadJob?.cancel()
        downloadJob = null
        loadJob?.cancel()
        loadJob = null
        cloudJob?.cancel()
        cloudJob = null
        fastStartCheckedUrl = null
        state.value = DetailState()
    }

    fun loadOfflineDetail(detail: VideoDetail) {
        loadJob?.cancel()
        loadJob = null
        favoriteJob?.cancel()
        favoriteJob = null
        downloadJob?.cancel()
        downloadJob = null
        cloudJob?.cancel()
        cloudJob = null
        state.value = state.value.copy(
            isLoading = false,
            isVideoLoading = false,
            detail = detail,
            error = null,
            isFavorite = false,
            download = null, 
            showStreamingWarning = false,
            isOffline = true,
            downloadProgress = -1f,
            downloadStateCode = null,
            downloadBytes = 0L,
            remoteVideoRefId = null,
            comments = emptyList(),
            areCommentsLoading = false,
            isCommentPosting = false,
            commentError = null
        )
        // Check download status for this POST
        checkDownloadStatus(detail.postLink)
        maybeCheckStreamingWarning(detail.videoUrl ?: "")
    }

    fun submitComment(body: String) {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        if (authRepository?.currentSession() == null) {
            state.value = state.value.copy(commentError = "登录后才能评论")
            return
        }
        val videoRefId = state.value.remoteVideoRefId
        if (videoRefId.isNullOrBlank()) {
            state.value = state.value.copy(commentError = "评论区还在初始化，请稍后再试")
            return
        }
        viewModelScope.launch {
            state.value = state.value.copy(isCommentPosting = true, commentError = null)
            try {
                val comment = backendRepository?.createComment(videoRefId, trimmed)
                state.value = state.value.copy(
                    isCommentPosting = false,
                    comments = listOfNotNull(comment) + state.value.comments,
                    commentError = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(
                    isCommentPosting = false,
                    commentError = "评论发送失败，请稍后重试"
                )
            }
        }
    }

    fun likeComment(commentId: String) {
        if (authRepository?.currentSession() == null) {
            state.value = state.value.copy(commentError = "登录后才能点赞")
            return
        }
        viewModelScope.launch {
            runCatching {
                backendRepository?.likeComment(commentId)
            }.getOrNull()?.let { updated ->
                state.value = state.value.copy(
                    comments = state.value.comments.map { if (it.id == updated.id) updated else it },
                    commentError = null
                )
            }
        }
    }

    private fun loadCloudDetail(detail: VideoDetail) {
        val backend = backendRepository ?: return
        if (detail.postLink.isBlank()) return
        cloudJob?.cancel()
        cloudJob = viewModelScope.launch {
            state.value = state.value.copy(areCommentsLoading = true, commentError = null)
            try {
                val videoRef = backend.upsertVideoRef(detail)
                if (videoRef == null) {
                    state.value = state.value.copy(areCommentsLoading = false)
                    return@launch
                }
                val comments = backend.listComments(videoRef.id)
                state.value = state.value.copy(
                    remoteVideoRefId = videoRef.id,
                    comments = comments,
                    areCommentsLoading = false,
                    commentError = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(
                    areCommentsLoading = false,
                    commentError = "评论加载失败"
                )
            }
        }
    }



    private fun maybeCheckStreamingWarning(videoUrl: String) {
        val lower = videoUrl.substringBefore('?').lowercase(Locale.ROOT)
        if (!lower.endsWith(".mp4")) {
            state.value = state.value.copy(showStreamingWarning = false)
            return
        }
        if (fastStartCheckedUrl == videoUrl) return
        fastStartCheckedUrl = videoUrl
        viewModelScope.launch {
            val optimized = com.elowen.niceTV.data.network.MediaProbe.isMp4FastStart(videoUrl)
            if (fastStartCheckedUrl != videoUrl) return@launch
            when (optimized) {
                true -> state.value = state.value.copy(showStreamingWarning = false)
                false -> state.value = state.value.copy(showStreamingWarning = true)
                null -> state.value = state.value.copy(showStreamingWarning = false)
            }
        }
    }

    private fun calculateDownloadProgress(download: Download?): Float {
        if (download == null) return -1f
        val percent = download.percentDownloaded
        if (percent >= 0f) {
            val length = download.contentLength
            return if (percent == 0f && length <= 0 && download.bytesDownloaded > 0) {
                -1f
            } else {
                percent
            }
        }
        val length = download.contentLength
        return if (length > 0) {
            ((download.bytesDownloaded.toDouble() / length.toDouble()) * 100.0).toFloat()
                .coerceIn(0f, 100f)
        } else {
            -1f
        }
    }

    private fun userFacingError(error: Throwable): String {
        val message = error.message.orEmpty()
        val lower = message.lowercase(Locale.ROOT)
        return when {
            lower.contains("timeout") || lower.contains("timed out") ->
                "网络请求超时，请重试或切换代理"
            lower.contains("account is banned") || lower.contains("banned") ->
                "账号已被封禁，无法继续操作"
            lower.contains("account is muted") || lower.contains("muted") ->
                "账号已被禁言，暂时不能发布内容"
            lower.contains("403") || lower.contains("forbidden") ->
                "访问验证已失效，请重新验证后再试"
            lower.contains("404") || lower.contains("not found") ->
                "视频页面不存在或已被移除"
            lower.contains("failed to connect") || lower.contains("unable to resolve") ->
                "网络连接失败，请检查代理或网络"
            message.isBlank() ->
                "加载失败，请稍后重试"
            else -> message
        }
    }
}
