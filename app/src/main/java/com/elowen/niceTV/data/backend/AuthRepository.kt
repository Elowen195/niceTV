package com.elowen.niceTV.data.backend

import android.content.Context
import androidx.core.content.edit
import com.elowen.niceTV.utils.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuthSession(
    val userId: String,
    val username: String,
    val role: String = "user"
)

class AuthRepository(
    context: Context,
    private val api: BackendApiClient
) {
    private val prefs = context.applicationContext.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)

    fun currentSession(): AuthSession? {
        val userId = SecurePrefs.getEncryptedString(prefs, KEY_USER_ID).orEmpty()
        val username = SecurePrefs.getEncryptedString(prefs, KEY_USERNAME).orEmpty()
        val role = SecurePrefs.getEncryptedString(prefs, KEY_USER_ROLE).orEmpty().ifBlank { "user" }
        if (userId.isBlank() || username.isBlank()) return null
        return AuthSession(userId = userId, username = username, role = role)
    }

    suspend fun login(login: String, password: String): AuthSession {
        val response = api.login(login.trim(), password)
        saveAuth(response)
        return AuthSession(response.user.id, response.user.username, response.user.role)
    }

    suspend fun register(username: String, password: String): AuthSession {
        val response = api.register(username.trim(), password)
        saveAuth(response)
        return AuthSession(response.user.id, response.user.username, response.user.role)
    }

    suspend fun logout() {
        val refreshToken = SecurePrefs.getEncryptedString(prefs, KEY_REFRESH_TOKEN).orEmpty()
        if (refreshToken.isNotBlank()) {
            runCatching { api.logout(refreshToken) }
        }
        clear()
    }

    suspend fun accessTokenOrRefresh(): String? {
        val accessToken = SecurePrefs.getEncryptedString(prefs, KEY_ACCESS_TOKEN).orEmpty()
        if (accessToken.isNotBlank()) return accessToken
        return refreshAccessToken()
    }

    suspend fun refreshAccessToken(): String? {
        val refreshToken = SecurePrefs.getEncryptedString(prefs, KEY_REFRESH_TOKEN).orEmpty()
        if (refreshToken.isBlank()) {
            clear()
            return null
        }
        return runCatching {
            val response = api.refresh(refreshToken)
            saveAuth(response)
            response.accessToken
        }.getOrElse {
            clear()
            null
        }
    }

    suspend fun <T> authorized(block: suspend (String) -> T): T? {
        val token = accessTokenOrRefresh() ?: return null
        return try {
            block(token)
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
            val refreshed = refreshAccessToken() ?: return null
            block(refreshed)
        }
    }

    private suspend fun saveAuth(response: AuthResponse) {
        withContext(Dispatchers.IO) {
            SecurePrefs.putEncryptedString(prefs, KEY_ACCESS_TOKEN, response.accessToken)
            SecurePrefs.putEncryptedString(prefs, KEY_REFRESH_TOKEN, response.refreshToken)
            SecurePrefs.putEncryptedString(prefs, KEY_USER_ID, response.user.id)
            SecurePrefs.putEncryptedString(prefs, KEY_USERNAME, response.user.username)
            SecurePrefs.putEncryptedString(prefs, KEY_USER_ROLE, response.user.role)
        }
    }

    private fun clear() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_USER_ROLE)
            remove(KEY_LAST_FAVORITE_SYNC)
        }
    }

    fun lastFavoriteSync(): String? = prefs.getString(KEY_LAST_FAVORITE_SYNC, null)

    fun saveLastFavoriteSync(value: String) {
        prefs.edit { putString(KEY_LAST_FAVORITE_SYNC, value) }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_LAST_FAVORITE_SYNC = "last_favorite_sync"
    }
}
