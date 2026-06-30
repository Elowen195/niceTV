package com.elowen.niceTV.data.model

data class HomeSection(
    val title: String,
    val posts: List<Post>,
    val moreLink: String,
    val isPopular: Boolean = false
)