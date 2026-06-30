package com.elowen.niceTV.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elowen.niceTV.data.repository.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(private val repository: PostRepository) : ViewModel() {
    private val _state = mutableStateOf(MainState())
    val state: State<MainState> = _state

    fun loadPosts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // 调用新接口获取板块数据
                val result = repository.getHomeSections()
                _state.value = _state.value.copy(
                    isLoading = false,
                    sections = result
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = userFacingError(e)
                )
            }
        }
    }

    // 当 Cookie 刷新成功后调用
    fun onCookieRefreshed() {
        loadPosts()
    }

    private fun userFacingError(error: Throwable): String {
        val message = error.message.orEmpty()
        val lower = message.lowercase(Locale.ROOT)
        return when {
            lower.contains("timeout") || lower.contains("timed out") ->
                "网络请求超时，请重试或切换代理"
            lower.contains("403") || lower.contains("forbidden") ->
                "访问验证已失效，请重新验证后再试"
            lower.contains("failed to connect") || lower.contains("unable to resolve") ->
                "网络连接失败，请检查代理或网络"
            message.isBlank() ->
                "加载失败，请稍后重试"
            else -> message
        }
    }
}
