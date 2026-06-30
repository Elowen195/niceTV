package com.elowen.niceTV.ui.viewmodel

sealed class NavigationState {
    object Home : NavigationState()
    data class VideoList(
        val url: String, 
        val title: String, 
        val isSearch: Boolean = false
    ) : NavigationState()
    object Favorites : NavigationState()
    object Download : NavigationState()
    object User : NavigationState()
}
