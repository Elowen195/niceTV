package com.elowen.niceTV.data.repository

import com.elowen.niceTV.data.model.TagItem
import com.elowen.niceTV.data.scraper.HtmlScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TagRepository(private val scraper: HtmlScraper) {

    private var cachedTags: List<TagItem>? = null

    suspend fun getTags(forceRefresh: Boolean = false): List<TagItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedTags?.let { return@withContext it }
        }

        val allTags = mutableListOf<TagItem>()
        // 抓取前3页标签，约 150-200 个常用标签 (Java 版本逻辑)
        val urls = listOf(
            "https://supjav.com/zh/tag?sort=quantity",
            "https://supjav.com/zh/tag/page/2?sort=quantity",
            "https://supjav.com/zh/tag/page/3?sort=quantity"
        )

        for (url in urls) {
            val pageTags = scraper.fetchTags(url)
            allTags.addAll(pageTags)
        }

        // 去重并按数量降序排列
        val finalTags = allTags.distinctBy { it.slug }.sortedByDescending { it.count }
        cachedTags = finalTags
        finalTags
    }
}
