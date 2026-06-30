package com.elowen.niceTV.ui.viewmodel

import com.elowen.niceTV.data.model.HomeSection

data class MainState(
    val isLoading: Boolean = false,
    val sections: List<HomeSection> = emptyList(), // 改为板块列表
    val error: String? = null
)