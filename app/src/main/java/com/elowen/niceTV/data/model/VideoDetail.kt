package com.elowen.niceTV.data.model

data class VideoDetail(
    val title: String = "",
    val postLink: String = "",
    val imageUrl: String = "",
    val videoUrl: String? = "",
    val videoReferer: String? = null,
    val servers: Map<String, VideoSource> = emptyMap(),
    val maker: String = "",
    val makerLink: String = "",
    val cast: List<String> = emptyList(),
    val castLinks: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val recommendations: List<HomeSection> = emptyList()
) {
    fun withActiveServer(name: String): VideoDetail {
        val source = servers[name]
        return copy(videoUrl = source?.url, videoReferer = source?.referer)
    }

    fun availableServers(): List<String> = servers.keys.toList()
}
