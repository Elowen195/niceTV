package com.elowen.niceTV.data.model

data class VideoSource(
    val url: String?,
    val referer: String?,
    val name: String = "",
    val qualityLabel: String? = null,
    val qualityBandwidth: Int? = null
)
