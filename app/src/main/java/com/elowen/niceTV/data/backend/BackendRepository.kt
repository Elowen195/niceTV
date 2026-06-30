package com.elowen.niceTV.data.backend

import com.elowen.niceTV.data.db.dao.FavoriteDao
import com.elowen.niceTV.data.db.entity.FavoriteEntity
import com.elowen.niceTV.data.model.VideoDetail
import java.time.Instant

class BackendRepository(
    private val api: BackendApiClient,
    private val authRepository: AuthRepository,
    private val favoriteDao: FavoriteDao
) {
    suspend fun upsertVideoRef(detail: VideoDetail): VideoRef? {
        val sourceUrl = detail.postLink.takeIf { it.isNotBlank() } ?: return null
        if (!sourceUrl.startsWith("http")) return null
        return api.upsertVideoRef(
            UpsertVideoRefRequest(
                sourceUrl = sourceUrl,
                title = detail.title,
                coverUrl = detail.imageUrl.takeIf { it.isNotBlank() },
                maker = detail.maker.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun syncFavoriteUpsert(detail: VideoDetail) {
        val videoRef = upsertVideoRef(detail) ?: return
        authRepository.authorized { token ->
            api.upsertFavorite(token, videoRef.id, detail.favoriteSnapshot())
        }
    }

    suspend fun syncFavoriteDelete(entity: FavoriteEntity) {
        if (!entity.link.startsWith("http")) return
        val videoRef = api.upsertVideoRef(entity.videoRefRequest())
        runCatching {
            authRepository.authorized { token ->
                api.deleteFavorite(token, videoRef.id)
            }
        }
    }

    suspend fun syncFavoritesWithCloud(): Int {
        val localFavorites = favoriteDao.getAllFavoritesOnce()
        val changes = localFavorites.filter { it.link.startsWith("http") }.map { favorite ->
            FavoriteSyncChange(
                sourceUrl = favorite.link,
                op = "upsert",
                updatedAt = Instant.ofEpochMilli(favorite.createdAt).toString(),
                snapshot = favorite.favoriteSnapshot()
            )
        }
        val result = authRepository.authorized { token ->
            api.syncFavorites(
                token,
                FavoriteSyncRequest(
                    since = authRepository.lastFavoriteSync(),
                    changes = changes
                )
            )
        } ?: return 0

        result.changes.forEach { favorite ->
            val link = favorite.videoRef.sourceUrl
            if (link.isBlank()) return@forEach
            if (favorite.deletedAt != null) {
                favoriteDao.delete(link)
            } else {
                favoriteDao.insert(favorite.toEntity())
            }
        }
        if (result.serverTime.isNotBlank()) {
            authRepository.saveLastFavoriteSync(result.serverTime)
        }
        return result.changes.size
    }

    suspend fun listComments(videoRefId: String): List<CommentItem> {
        return api.listComments(videoRefId)
    }

    suspend fun createComment(videoRefId: String, body: String): CommentItem? {
        return authRepository.authorized { token ->
            api.createComment(token, videoRefId, body)
        }
    }

    suspend fun likeComment(commentId: String): CommentItem? {
        return authRepository.authorized { token ->
            api.likeComment(token, commentId)
        }
    }

    private fun VideoDetail.favoriteSnapshot(): FavoriteSnapshot {
        return FavoriteSnapshot(
            title = title,
            coverUrl = imageUrl.takeIf { it.isNotBlank() },
            maker = maker.takeIf { it.isNotBlank() },
            tags = tags
        )
    }

    private fun FavoriteEntity.favoriteSnapshot(): FavoriteSnapshot {
        return FavoriteSnapshot(
            title = title,
            coverUrl = imageUrl.takeIf { it.isNotBlank() },
            maker = maker?.takeIf { it.isNotBlank() },
            tags = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        )
    }

    private fun FavoriteEntity.videoRefRequest(): UpsertVideoRefRequest {
        return UpsertVideoRefRequest(
            sourceUrl = link,
            title = title,
            coverUrl = imageUrl.takeIf { it.isNotBlank() },
            maker = maker?.takeIf { it.isNotBlank() }
        )
    }

    private fun CloudFavorite.toEntity(): FavoriteEntity {
        return FavoriteEntity(
            link = videoRef.sourceUrl,
            title = titleSnapshot.ifBlank { videoRef.title },
            imageUrl = coverSnapshot ?: videoRef.coverUrl.orEmpty(),
            maker = makerSnapshot ?: videoRef.maker,
            tags = tagsSnapshot.joinToString(","),
            createdAt = System.currentTimeMillis()
        )
    }
}
