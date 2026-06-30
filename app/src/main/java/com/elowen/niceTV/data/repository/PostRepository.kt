package com.elowen.niceTV.data.repository

import com.elowen.niceTV.data.model.HomeSection
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.data.model.VideoDetail
import com.elowen.niceTV.data.model.VideoSource
import com.elowen.niceTV.data.scraper.HtmlScraper
import org.jsoup.nodes.Document
import com.elowen.niceTV.data.db.dao.FavoriteDao
import com.elowen.niceTV.data.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class PostRepository(
    private val scraper: HtmlScraper,
    private val favoriteDao: FavoriteDao
) {
    // 收藏功能
    val allFavorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

    fun isFavorite(link: String): Flow<Boolean> = favoriteDao.isFavorite(link)

    suspend fun toggleFavorite(detail: VideoDetail) {
        val isFav = favoriteDao.isFavoriteSync(detail.postLink)
        if (isFav) {
            favoriteDao.delete(detail.postLink)
        } else {
            favoriteDao.insert(
                FavoriteEntity(
                    link = detail.postLink,
                    title = detail.title,
                    imageUrl = detail.imageUrl,
                    maker = detail.maker,
                    tags = detail.tags.joinToString(","),
                    cast = detail.cast.joinToString(",")
                )
            )
        }
    }

    suspend fun removeFavorites(links: List<String>) {
        if (links.isEmpty()) return
        favoriteDao.deleteAll(links)
    }

    /**
     * 获取首页分板块数据
     */
    suspend fun getHomeSections(): List<HomeSection> {
        val url = "https://supjav.com/zh/"
        return scraper.fetchHomeSections(url)
    }

    suspend fun fetchPartialDetail(url: String): Pair<VideoDetail, Document> {
        return scraper.fetchPartialDetail(url)
    }

    suspend fun resolveVideoUrlFromDoc(doc: Document): Map<String, VideoSource> {
        return scraper.resolveVideoUrlFromDoc(doc)
    }

    suspend fun fetchPosts(url: String): Pair<List<Post>, Int> {
        return scraper.fetchPosts(url)
    }
}
