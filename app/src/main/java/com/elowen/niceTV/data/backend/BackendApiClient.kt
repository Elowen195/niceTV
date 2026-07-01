package com.elowen.niceTV.data.backend

import com.elowen.niceTV.BuildConfig
import com.elowen.niceTV.core.NetworkRestrictionManager
import com.elowen.niceTV.core.platform.proxy.ProxyHttpClientFactory
import com.elowen.niceTV.core.platform.proxy.ProxyRuntimeConfig
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiException(
    val statusCode: Int,
    message: String
) : IOException(message)

class BackendApiClient(
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL,
    private val gson: Gson = Gson()
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val directClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    @Volatile
    private var proxiedClient: OkHttpClient? = null
    @Volatile
    private var proxiedClientPort: Int? = null

    suspend fun register(username: String, password: String): AuthResponse {
        return post(
            path = "/v1/auth/register",
            body = RegisterRequest(username = username, password = password),
            responseClass = AuthResponse::class.java
        )
    }

    suspend fun login(login: String, password: String): AuthResponse {
        return post(
            path = "/v1/auth/login",
            body = LoginRequest(login = login, password = password),
            responseClass = AuthResponse::class.java
        )
    }

    suspend fun refresh(refreshToken: String): AuthResponse {
        return post(
            path = "/v1/auth/refresh",
            body = RefreshRequest(refreshToken = refreshToken),
            responseClass = AuthResponse::class.java
        )
    }

    suspend fun logout(refreshToken: String) {
        postNoBody(
            path = "/v1/auth/logout",
            body = LogoutRequest(refreshToken = refreshToken)
        )
    }

    suspend fun me(accessToken: String): BackendUser {
        return get(
            path = "/v1/me",
            accessToken = accessToken,
            responseClass = UserEnvelope::class.java
        ).user
    }

    suspend fun upsertVideoRef(request: UpsertVideoRefRequest): VideoRef {
        return post(
            path = "/v1/video-refs",
            body = request,
            responseClass = VideoRefEnvelope::class.java
        ).videoRef
    }

    suspend fun upsertFavorite(
        accessToken: String,
        videoRefId: String,
        snapshot: FavoriteSnapshot
    ): CloudFavorite {
        return put(
            path = "/v1/favorites/$videoRefId",
            accessToken = accessToken,
            body = FavoriteUpsertRequest(snapshot),
            responseClass = FavoriteEnvelope::class.java
        ).favorite
    }

    suspend fun deleteFavorite(accessToken: String, videoRefId: String) {
        delete(path = "/v1/favorites/$videoRefId", accessToken = accessToken)
    }

    suspend fun syncFavorites(accessToken: String, request: FavoriteSyncRequest): FavoriteSyncResult {
        return post(
            path = "/v1/favorites/sync",
            accessToken = accessToken,
            body = request,
            responseClass = FavoriteSyncResult::class.java
        )
    }

    suspend fun listComments(videoRefId: String): List<CommentItem> {
        return get(
            path = "/v1/video-refs/$videoRefId/comments?limit=50",
            responseClass = CommentsEnvelope::class.java
        ).comments
    }

    suspend fun createComment(accessToken: String, videoRefId: String, body: String): CommentItem {
        return post(
            path = "/v1/video-refs/$videoRefId/comments",
            accessToken = accessToken,
            body = CreateCommentRequest(body = body),
            responseClass = CommentEnvelope::class.java
        ).comment
    }

    suspend fun likeComment(accessToken: String, commentId: String): CommentItem {
        return put(
            path = "/v1/comments/$commentId/like",
            accessToken = accessToken,
            body = emptyMap<String, String>(),
            responseClass = CommentEnvelope::class.java
        ).comment
    }

    suspend fun listMyCollections(accessToken: String): List<VideoCollection> {
        return get(
            path = "/v1/collections/mine",
            accessToken = accessToken,
            responseClass = CollectionsEnvelope::class.java
        ).collections
    }

    suspend fun listPublicCollections(): List<VideoCollection> {
        return get(
            path = "/v1/collections/public",
            responseClass = CollectionsEnvelope::class.java
        ).collections
    }

    suspend fun getCollection(idOrSlug: String): CollectionDetail {
        return get(
            path = "/v1/collections/$idOrSlug",
            responseClass = CollectionDetail::class.java
        )
    }

    suspend fun getMyCollection(accessToken: String, idOrSlug: String): CollectionDetail {
        return get(
            path = "/v1/collections/mine/$idOrSlug",
            accessToken = accessToken,
            responseClass = CollectionDetail::class.java
        )
    }

    suspend fun createCollection(
        accessToken: String,
        title: String,
        description: String,
        visibility: String,
        coverUrl: String? = null
    ): VideoCollection {
        return post(
            path = "/v1/collections",
            accessToken = accessToken,
            body = CollectionRequest(
                title = title,
                description = description,
                visibility = visibility,
                coverUrl = coverUrl
            ),
            responseClass = CollectionEnvelope::class.java
        ).collection
    }

    suspend fun addCollectionItem(
        accessToken: String,
        collectionId: String,
        request: AddCollectionItemRequest
    ): CollectionItem {
        return post(
            path = "/v1/collections/$collectionId/items",
            accessToken = accessToken,
            body = request,
            responseClass = CollectionItemEnvelope::class.java
        ).item
    }

    suspend fun copyCollection(accessToken: String, idOrSlug: String): VideoCollection {
        return post(
            path = "/v1/collections/$idOrSlug/copy",
            accessToken = accessToken,
            body = emptyMap<String, String>(),
            responseClass = CollectionEnvelope::class.java
        ).collection
    }

    private suspend fun <T> get(
        path: String,
        accessToken: String? = null,
        responseClass: Class<T>
    ): T = execute(
        Request.Builder()
            .url(url(path))
            .applyAuth(accessToken)
            .get()
            .build(),
        responseClass
    )

    private suspend fun <T> post(
        path: String,
        accessToken: String? = null,
        body: Any,
        responseClass: Class<T>
    ): T = execute(
        Request.Builder()
            .url(url(path))
            .applyAuth(accessToken)
            .post(jsonBody(body))
            .build(),
        responseClass
    )

    private suspend fun postNoBody(path: String, body: Any) {
        executeNoBody(
            Request.Builder()
                .url(url(path))
                .post(jsonBody(body))
                .build()
        )
    }

    private suspend fun <T> put(
        path: String,
        accessToken: String? = null,
        body: Any,
        responseClass: Class<T>
    ): T = execute(
        Request.Builder()
            .url(url(path))
            .applyAuth(accessToken)
            .put(jsonBody(body))
            .build(),
        responseClass
    )

    private suspend fun delete(path: String, accessToken: String? = null) {
        executeNoBody(
            Request.Builder()
                .url(url(path))
                .applyAuth(accessToken)
                .delete()
                .build()
        )
    }

    private suspend fun <T> execute(request: Request, responseClass: Class<T>): T {
        return withContext(Dispatchers.IO) {
            activeClient().newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    throw ApiException(response.code, raw.ifBlank { response.message })
                }
                gson.fromJson(raw, responseClass)
            }
        }
    }

    private suspend fun executeNoBody(request: Request) {
        withContext(Dispatchers.IO) {
            activeClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body.string()
                    throw ApiException(response.code, raw.ifBlank { response.message })
                }
            }
        }
    }

    private fun jsonBody(body: Any) = gson.toJson(body).toRequestBody(jsonType)

    private fun activeClient(): OkHttpClient {
        if (!NetworkRestrictionManager.isProxyReady()) {
            return directClient
        }
        val port = ProxyRuntimeConfig.getPort()
        val cached = proxiedClient
        if (cached != null && proxiedClientPort == port) {
            return cached
        }
        return ProxyHttpClientFactory.createSocksClient(
            connectTimeoutSeconds = 20,
            readTimeoutSeconds = 20,
            writeTimeoutSeconds = 20
        ).also {
            proxiedClient = it
            proxiedClientPort = port
        }
    }

    private fun url(path: String): String {
        return baseUrl.trimEnd('/') + path
    }

    private fun Request.Builder.applyAuth(accessToken: String?): Request.Builder {
        if (!accessToken.isNullOrBlank()) {
            header("Authorization", "Bearer $accessToken")
        }
        header("Accept", "application/json")
        return this
    }
}
