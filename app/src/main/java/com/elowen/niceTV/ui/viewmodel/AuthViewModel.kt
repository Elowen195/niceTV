package com.elowen.niceTV.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elowen.niceTV.data.backend.ApiException
import com.elowen.niceTV.data.backend.AuthRepository
import com.elowen.niceTV.data.backend.AuthSession
import com.elowen.niceTV.data.repository.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class AuthUiState(
    val session: AuthSession? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val isLoggedIn: Boolean get() = session != null
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val postRepository: PostRepository
) : ViewModel() {
    val state = mutableStateOf(AuthUiState(session = authRepository.currentSession()))

    fun login(login: String, password: String) {
        if (login.isBlank() || password.isBlank()) {
            state.value = state.value.copy(error = "请输入账号和密码", message = null)
            return
        }
        viewModelScope.launch {
            runAuthAction {
                val session = authRepository.login(login, password)
                state.value = state.value.copy(session = session, message = "登录成功，正在同步收藏")
                syncFavoritesInternal()
            }
        }
    }

    fun register(username: String, password: String) {
        if (username.isBlank() || password.length < 6) {
            state.value = state.value.copy(error = "用户名不能为空，密码至少 6 位", message = null)
            return
        }
        viewModelScope.launch {
            runAuthAction {
                val session = authRepository.register(username, password)
                state.value = state.value.copy(session = session, message = "注册成功，正在同步收藏")
                syncFavoritesInternal()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runAuthAction {
                authRepository.logout()
                state.value = AuthUiState(message = "已退出登录")
            }
        }
    }

    fun syncFavorites() {
        viewModelScope.launch {
            runAuthAction {
                syncFavoritesInternal()
            }
        }
    }

    fun clearMessage() {
        state.value = state.value.copy(error = null, message = null)
    }

    private suspend fun runAuthAction(block: suspend () -> Unit) {
        state.value = state.value.copy(isLoading = true, error = null, message = null)
        try {
            block()
            state.value = state.value.copy(isLoading = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state.value = state.value.copy(
                isLoading = false,
                error = userFacingError(error),
                message = null
            )
        }
    }

    private suspend fun syncFavoritesInternal() {
        val count = postRepository.syncFavoritesWithCloud()
        state.value = state.value.copy(message = if (count > 0) "收藏同步完成：$count 条变更" else "收藏已同步")
    }

    private fun userFacingError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is ApiException && error.statusCode == 409 -> "用户名或邮箱已被占用"
            error is ApiException && error.statusCode == 401 -> "账号或密码不正确"
            error is ApiException && error.statusCode == 403 && message.contains("banned", ignoreCase = true) -> "账号已被封禁，无法继续登录"
            error is ApiException && error.statusCode == 403 && message.contains("muted", ignoreCase = true) -> "账号已被禁言，暂时不能发布内容"
            error is ApiException && error.statusCode == 403 -> "当前网络无法访问后端，请开启代理后重试"
            error is ApiException && error.statusCode == 429 -> "操作太频繁，请稍后再试"
            message.contains("timeout", ignoreCase = true) -> "连接后端超时，请稍后重试"
            message.isBlank() -> "操作失败，请稍后重试"
            else -> "操作失败：$message"
        }
    }
}
