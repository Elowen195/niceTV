package com.elowen.niceTV.data.model

data class Post(
    val title: String,
    val imageUrl: String,
    val link: String,
    val maker: String? = null,
    val cast: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    var date: String? = null
)