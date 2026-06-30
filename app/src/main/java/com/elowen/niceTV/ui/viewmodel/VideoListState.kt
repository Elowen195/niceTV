package com.elowen.niceTV.ui.viewmodel

import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.data.model.TagItem

data class VideoListState(
    val isLoading: Boolean = false,
    val posts: List<Post> = emptyList(),
    val error: String? = null,
    val title: String = "",
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isLoadingMore: Boolean = false,
    val isSearchPanelVisible: Boolean = false,
    val searchPrefix: String? = null,
    val availableTags: List<TagItem> = emptyList(),
    val selectedTags: Set<String> = emptySet()
)
