package com.elowen.niceTV.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elowen.niceTV.data.db.entity.FavoriteEntity
import com.elowen.niceTV.data.repository.PostRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class FavoriteState(
    val allFavorites: List<FavoriteEntity> = emptyList(),
    val filteredFavorites: List<FavoriteEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DateNewest,
    // [Multi-Select] Use Sets for filters
    val filterMakers: Set<String> = emptySet(),
    val filterCasts: Set<String> = emptySet(),
    val filterTags: Set<String> = emptySet(),
    // [Logic Toggle] Match Any (OR) vs Match All (AND)
    val isCastMatchAny: Boolean = false,
    val isTagMatchAny: Boolean = false,
    // Available filters for UI
    val availableMakers: List<String> = emptyList(),
    val availableCasts: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val selectedLinks: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

enum class SortOrder(val displayName: String) {
    DateNewest("最新收藏"),
    DateOldest("最早收藏"),
    TitleAz("标题 A-Z")
}

class FavoriteViewModel(private val repository: PostRepository) : ViewModel() {
    val state = mutableStateOf(FavoriteState())

    init {
        viewModelScope.launch {
            repository.allFavorites.collectLatest { list ->
                // Calculate available filters from the full list
                val makers = list.mapNotNull { it.maker }.filter { it.isNotBlank() }.distinct().sorted()
                
                // Cast and Tags are stored as comma-separated strings
                val casts = list.flatMap { it.cast?.split(",") ?: emptyList() }
                    .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                    
                val tags = list.flatMap { it.tags?.split(",") ?: emptyList() }
                    .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()

                state.value = state.value.copy(
                    allFavorites = list,
                    availableMakers = makers,
                    availableCasts = casts,
                    availableTags = tags,
                    selectedLinks = state.value.selectedLinks.filter { selected ->
                        list.any { it.link == selected }
                    }.toSet()
                )
                applyFilters()
            }
        }
    }

    fun onSearch(query: String) {
        state.value = state.value.copy(searchQuery = query)
        applyFilters()
    }

    fun onSortChange(order: SortOrder) {
        state.value = state.value.copy(sortOrder = order)
        applyFilters()
    }

    // Toggle logic for Multi-Select
    fun toggleMakerFilter(maker: String) {
        val current = state.value.filterMakers.toMutableSet()
        if (current.contains(maker)) {
            current.remove(maker)
        } else {
            current.add(maker)
        }
        state.value = state.value.copy(filterMakers = current)
        applyFilters()
    }
    
    fun clearMakerFilter() {
        state.value = state.value.copy(filterMakers = emptySet())
        applyFilters()
    }

    fun toggleCastFilter(cast: String) {
        val current = state.value.filterCasts.toMutableSet()
        if (current.contains(cast)) {
            current.remove(cast)
        } else {
            current.add(cast)
        }
        state.value = state.value.copy(filterCasts = current)
        applyFilters()
    }
    
    fun clearCastFilter() {
        state.value = state.value.copy(filterCasts = emptySet())
        applyFilters()
    }

    fun toggleCastMatchLogic() {
        state.value = state.value.copy(isCastMatchAny = !state.value.isCastMatchAny)
        applyFilters()
    }

    fun toggleTagFilter(tag: String) {
        val current = state.value.filterTags.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        state.value = state.value.copy(filterTags = current)
        applyFilters()
    }
    
    fun clearTagFilter() {
        state.value = state.value.copy(filterTags = emptySet())
        applyFilters()
    }

    fun toggleTagMatchLogic() {
        state.value = state.value.copy(isTagMatchAny = !state.value.isTagMatchAny)
        applyFilters()
    }

    fun clearAllFilters() {
        state.value = state.value.copy(
            searchQuery = "",
            filterMakers = emptySet(),
            filterCasts = emptySet(),
            filterTags = emptySet()
        )
        applyFilters()
    }

    fun setSelectionMode(enabled: Boolean) {
        state.value = state.value.copy(
            isSelectionMode = enabled,
            selectedLinks = if (enabled) state.value.selectedLinks else emptySet()
        )
    }

    fun toggleSelection(link: String) {
        val current = state.value.selectedLinks.toMutableSet()
        if (current.contains(link)) {
            current.remove(link)
        } else {
            current.add(link)
        }
        state.value = state.value.copy(
            selectedLinks = current,
            isSelectionMode = current.isNotEmpty() || state.value.isSelectionMode
        )
    }

    fun selectAllFiltered() {
        val links = state.value.filteredFavorites.map { it.link }.toSet()
        state.value = state.value.copy(
            selectedLinks = links,
            isSelectionMode = links.isNotEmpty()
        )
    }

    fun clearSelection() {
        state.value = state.value.copy(selectedLinks = emptySet(), isSelectionMode = false)
    }

    fun deleteSelected() {
        val links = state.value.selectedLinks.toList()
        if (links.isEmpty()) return
        viewModelScope.launch {
            repository.removeFavorites(links)
            state.value = state.value.copy(selectedLinks = emptySet(), isSelectionMode = false)
        }
    }

    private fun applyFilters() {
        val s = state.value
        var result = s.allFavorites

        // 1. Search
        if (s.searchQuery.isNotBlank()) {
            val query = s.searchQuery.trim()
            result = result.filter { entity ->
                entity.title.contains(query, ignoreCase = true) ||
                    entity.maker.orEmpty().contains(query, ignoreCase = true) ||
                    entity.cast.orEmpty().contains(query, ignoreCase = true) ||
                    entity.tags.orEmpty().contains(query, ignoreCase = true)
            }
        }

        // 2. Filters 
        
        // Filter by Maker (OR logic: if video matches ANY of selected makers)
        if (s.filterMakers.isNotEmpty()) {
            result = result.filter { s.filterMakers.contains(it.maker) }
        }

        // Filter by Cast
        if (s.filterCasts.isNotEmpty()) {
            result = result.filter { entity ->
                val entityCasts = entity.cast?.split(",")?.map { it.trim() } ?: emptyList()
                if (s.isCastMatchAny) {
                    // Match Any (OR)
                    s.filterCasts.any { selected -> entityCasts.contains(selected) }
                } else {
                    // Match All (AND)
                    entityCasts.containsAll(s.filterCasts)
                }
            }
        }
        
        // Filter by Tag
        if (s.filterTags.isNotEmpty()) {
            result = result.filter { entity ->
                val entityTags = entity.tags?.split(",")?.map { it.trim() } ?: emptyList()
                if (s.isTagMatchAny) {
                    // Match Any (OR)
                    s.filterTags.any { selected -> entityTags.contains(selected) }
                } else {
                    // Match All (AND)
                    entityTags.containsAll(s.filterTags)
                }
            }
        }

        // 3. Sort
        result = when (s.sortOrder) {
            SortOrder.DateNewest -> result.sortedByDescending { it.createdAt }
            SortOrder.DateOldest -> result.sortedBy { it.createdAt }
            SortOrder.TitleAz -> result.sortedBy { it.title }
        }

        state.value = s.copy(filteredFavorites = result)
    }
}
