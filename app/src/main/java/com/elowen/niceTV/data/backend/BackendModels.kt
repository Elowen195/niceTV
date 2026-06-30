package com.elowen.niceTV.data.backend

data class BackendUser(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val role: String = "user"
)

data class AuthResponse(
    val accessToken: String = "",
    val accessTokenExpiresAt: String = "",
    val refreshToken: String = "",
    val refreshTokenExpiresAt: String = "",
    val user: BackendUser = BackendUser()
)

data class UserEnvelope(
    val user: BackendUser = BackendUser()
)

data class LoginRequest(
    val login: String,
    val password: String,
    val deviceName: String = "android"
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String? = null,
    val deviceName: String = "android"
)

data class RefreshRequest(
    val refreshToken: String,
    val deviceName: String = "android"
)

data class LogoutRequest(
    val refreshToken: String
)

data class VideoRef(
    val id: String = "",
    val source: String = "supjav",
    val sourceUrl: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val maker: String? = null
)

data class VideoRefEnvelope(
    val videoRef: VideoRef = VideoRef()
)

data class UpsertVideoRefRequest(
    val source: String = "supjav",
    val sourceUrl: String,
    val title: String,
    val coverUrl: String? = null,
    val maker: String? = null
)

data class FavoriteSnapshot(
    val title: String,
    val coverUrl: String? = null,
    val maker: String? = null,
    val tags: List<String> = emptyList()
)

data class FavoriteUpsertRequest(
    val snapshot: FavoriteSnapshot
)

data class FavoriteSyncChange(
    val source: String = "supjav",
    val sourceUrl: String,
    val op: String,
    val updatedAt: String? = null,
    val snapshot: FavoriteSnapshot
)

data class FavoriteSyncRequest(
    val since: String? = null,
    val changes: List<FavoriteSyncChange> = emptyList()
)

data class FavoriteSyncResult(
    val serverTime: String = "",
    val changes: List<CloudFavorite> = emptyList()
)

data class CloudFavorite(
    val id: String = "",
    val videoRef: VideoRef = VideoRef(),
    val titleSnapshot: String = "",
    val coverSnapshot: String? = null,
    val makerSnapshot: String? = null,
    val tagsSnapshot: List<String> = emptyList(),
    val deletedAt: String? = null
)

data class FavoriteEnvelope(
    val favorite: CloudFavorite = CloudFavorite()
)

data class FavoritesEnvelope(
    val favorites: List<CloudFavorite> = emptyList()
)

data class CommentItem(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val videoRefId: String = "",
    val parentId: String? = null,
    val body: String = "",
    val status: String = "visible",
    val likeCount: Int = 0,
    val createdAt: String = ""
)

data class CommentsEnvelope(
    val comments: List<CommentItem> = emptyList()
)

data class CommentEnvelope(
    val comment: CommentItem = CommentItem()
)

data class CreateCommentRequest(
    val parentId: String? = null,
    val body: String
)

