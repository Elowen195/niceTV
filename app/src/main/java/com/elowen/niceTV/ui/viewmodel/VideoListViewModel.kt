package com.elowen.niceTV.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elowen.niceTV.data.repository.PostRepository
import com.elowen.niceTV.data.repository.TagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

private const val DEFAULT_SOURCE_URL = "https://supjav.com/zh"

class VideoListViewModel(
    private val repository: PostRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _state = mutableStateOf(VideoListState())
    val state: State<VideoListState> = _state
    
    // 基础URL，用于分页或者记录当前上下文
    private var currentBaseUrl: String = ""
    private var originalBaseUrl: String = ""

    fun init(url: String, title: String, isSearch: Boolean, showPanel: Boolean = false, searchPrefix: String? = null) {
        _state.value = VideoListState(
            title = title,
            isSearchMode = isSearch,
            isLoading = !isSearch,
            isSearchPanelVisible = showPanel || isSearch,
            searchPrefix = searchPrefix
        )
        currentBaseUrl = url
        originalBaseUrl = url
        
        if (!isSearch) {
            loadPosts(url, isRefresh = true)
        }
        
        // 初始加载标签
        fetchTags()
    }

    private fun fetchTags() {
        viewModelScope.launch {
            try {
                val tags = tagRepository.getTags()
                _state.value = _state.value.copy(availableTags = tags)
            } catch (_: Exception) {
                // 标签加载失败不影响正常功能
            }
        }
    }

    fun toggleSearchPanel() {
        _state.value = _state.value.copy(isSearchPanelVisible = !_state.value.isSearchPanelVisible)
    }

    fun showSearchPanel() {
        _state.value = _state.value.copy(isSearchPanelVisible = true)
    }

    fun toggleTag(slug: String) {
        val currentSelected = _state.value.selectedTags.toMutableSet()
        if (currentSelected.contains(slug)) {
            currentSelected.remove(slug)
        } else {
            currentSelected.add(slug)
        }
        _state.value = _state.value.copy(selectedTags = currentSelected)
    }

    fun clearSearchFilters() {
        _state.value = _state.value.copy(
            searchQuery = "",
            selectedTags = emptySet(),
            title = if (_state.value.isSearchMode) {
                _state.value.searchPrefix ?: "全站搜索"
            } else {
                _state.value.title
            },
            posts = if (_state.value.isSearchMode) emptyList() else _state.value.posts,
            currentPage = 1,
            totalPages = 1,
            error = null,
            isSearchPanelVisible = true
        )
        currentBaseUrl = originalBaseUrl
    }

    fun loadPosts(url: String, isRefresh: Boolean = false, targetPage: Int? = null) {
        if (isRefresh) {
            _state.value = _state.value.copy(isLoading = true, error = null)
        } else {
            _state.value = _state.value.copy(isLoadingMore = true, error = null)
        }

        viewModelScope.launch {
            try {
                val (newPosts, totalPages) = repository.fetchPosts(url)
                
                val currentPosts = if (isRefresh) emptyList() else _state.value.posts
                // 按链接去重合并
                val existingLinks = currentPosts.map { it.link }.toSet()
                val mergedPosts = currentPosts + newPosts.filter { it.link !in existingLinks }
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    posts = mergedPosts,
                    totalPages = totalPages,
                    currentPage = if (isRefresh) 1 else targetPage ?: _state.value.currentPage
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = userFacingError(e)
                )
            }
        }
    }

    fun loadMore() {
        if (_state.value.posts.isEmpty() && _state.value.error != null) {
            loadPosts(currentBaseUrl, isRefresh = true)
            return
        }
        if (_state.value.isLoading || _state.value.isLoadingMore || _state.value.currentPage >= _state.value.totalPages) {
            return
        }
        val nextPage = _state.value.currentPage + 1
        val nextUrl = buildPageUrl(currentBaseUrl, nextPage)
        loadPosts(nextUrl, isRefresh = false, targetPage = nextPage)
    }

    private fun buildPageUrl(baseUrl: String, page: Int): String {
        if (page <= 1) return baseUrl
        val qIdx = baseUrl.indexOf('?')
        return if (qIdx >= 0) {
            val path = baseUrl.substring(0, qIdx).let { if (it.endsWith("/")) it else "$it/" }
            val query = baseUrl.substring(qIdx + 1)
            "${path}page/$page/?$query"
        } else {
            val path = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            "${path}page/$page/"
        }
    }

    fun performSearch(query: String) {
        val selectedTags = _state.value.selectedTags
        if (query.isBlank() && selectedTags.isEmpty()) return
        
        val searchUrl = buildSearchUrl(query, selectedTags)
        
        _state.value = _state.value.copy(
            searchQuery = query,
            title = _state.value.searchPrefix ?: if (query.isNotBlank()) "搜索: $query" else "筛选结果",
            currentPage = 1,
            totalPages = 1,
            posts = emptyList(),
            isSearchPanelVisible = false // 搜索时自动关闭面板
        )
        currentBaseUrl = searchUrl
        loadPosts(searchUrl, isRefresh = true)
    }

    private fun buildSearchUrl(query: String, tags: Set<String>): String {
        val normalizedOriginal = originalBaseUrl.trimEnd('/')
        val base = if (normalizedOriginal.isNotBlank() && normalizedOriginal != DEFAULT_SOURCE_URL) {
            // 如果是分类页或其他子页面，去掉可能存在的旧查询参数
            originalBaseUrl.substringBefore('?')
        } else {
            DEFAULT_SOURCE_URL
        }
        
        val urlWithTrailingSlash = if (base.endsWith("/")) base else "$base/"
        val params = mutableListOf<String>()
        
        if (tags.isNotEmpty()) {
            params.add("tag=${tags.joinToString("+")}")
        }
        if (query.isNotBlank()) {
            params.add("s=${URLEncoder.encode(query, "UTF-8")}")
        }
        
        return if (params.isEmpty()) urlWithTrailingSlash else "$urlWithTrailingSlash?${params.joinToString("&")}"
    }

    private fun userFacingError(error: Throwable): String {
        val message = error.message.orEmpty()
        val lower = message.lowercase(Locale.ROOT)
        return when {
            lower.contains("timeout") || lower.contains("timed out") ->
                "网络请求超时，请重试或切换代理"
            lower.contains("403") || lower.contains("forbidden") ->
                "访问验证已失效，请重新验证后再试"
            lower.contains("failed to connect") || lower.contains("unable to resolve") ->
                "网络连接失败，请检查代理或网络"
            message.isBlank() ->
                "加载失败，请稍后重试"
            else -> message
        }
    }
}
