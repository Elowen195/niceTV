package com.elowen.niceTV.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elowen.niceTV.data.backend.ApiException
import com.elowen.niceTV.data.backend.AuthRepository
import com.elowen.niceTV.data.backend.BackendRepository
import com.elowen.niceTV.data.backend.CollectionDetail
import com.elowen.niceTV.data.backend.VideoCollection
import com.elowen.niceTV.data.model.VideoDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val myCollections: List<VideoCollection> = emptyList(),
    val publicCollections: List<VideoCollection> = emptyList(),
    val selectedDetail: CollectionDetail? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isAdding: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class CollectionsViewModel(
    private val repository: BackendRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    val state = mutableStateOf(CollectionsUiState())

    fun refreshAll() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null, message = null)
            try {
                val publicCollections = repository.listPublicCollections()
                val myCollections = if (authRepository.currentSession() != null) {
                    repository.listMyCollections()
                } else {
                    emptyList()
                }
                state.value = state.value.copy(
                    myCollections = myCollections,
                    publicCollections = publicCollections,
                    selectedDetail = state.value.selectedDetail?.takeIf { detail ->
                        (myCollections + publicCollections).any { it.id == detail.collection.id }
                    },
                    isLoading = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = state.value.copy(
                    isLoading = false,
                    error = userFacingError(error)
                )
            }
        }
    }

    fun selectCollection(collection: VideoCollection) {
        viewModelScope.launch {
            loadCollectionDetail(collection)
        }
    }

    fun createCollection(title: String, description: String, visibility: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            state.value = state.value.copy(error = "清单标题不能为空", message = null)
            return
        }
        if (authRepository.currentSession() == null) {
            state.value = state.value.copy(error = "登录后才能创建清单", message = null)
            return
        }
        viewModelScope.launch {
            state.value = state.value.copy(isCreating = true, error = null, message = null)
            try {
                val collection = repository.createCollection(
                    title = trimmedTitle,
                    description = description.trim(),
                    visibility = visibility
                )
                if (collection == null) {
                    state.value = state.value.copy(
                        isCreating = false,
                        error = "登录状态已失效，请重新登录"
                    )
                    return@launch
                }
                val myCollections = listOf(collection) + state.value.myCollections.filterNot { it.id == collection.id }
                val publicCollections = if (collection.visibility == "public") {
                    listOf(collection) + state.value.publicCollections.filterNot { it.id == collection.id }
                } else {
                    state.value.publicCollections
                }
                state.value = state.value.copy(
                    myCollections = myCollections,
                    publicCollections = publicCollections,
                    isCreating = false,
                    message = "清单已创建"
                )
                loadCollectionDetail(collection)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = state.value.copy(
                    isCreating = false,
                    error = userFacingError(error)
                )
            }
        }
    }

    fun addCurrentDetailToCollection(collectionId: String, detail: VideoDetail) {
        if (authRepository.currentSession() == null) {
            state.value = state.value.copy(error = "登录后才能加入清单", message = null)
            return
        }
        viewModelScope.launch {
            state.value = state.value.copy(isAdding = true, error = null, message = null)
            try {
                val item = repository.addDetailToCollection(collectionId, detail)
                if (item == null) {
                    state.value = state.value.copy(
                        isAdding = false,
                        error = "当前视频缺少可分享链接"
                    )
                    return@launch
                }
                val myCollections = repository.listMyCollections()
                val publicCollections = repository.listPublicCollections()
                val selected = state.value.selectedDetail?.collection
                    ?.takeIf { it.id == collectionId }
                    ?.let { collection ->
                        repository.getCollectionDetail(
                            myCollections.firstOrNull { it.id == collection.id } ?: collection
                        )
                    } ?: state.value.selectedDetail
                state.value = state.value.copy(
                    myCollections = myCollections,
                    publicCollections = publicCollections,
                    selectedDetail = selected,
                    isAdding = false,
                    message = "已加入清单"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = state.value.copy(
                    isAdding = false,
                    error = userFacingError(error)
                )
            }
        }
    }

    fun copyCollection(collection: VideoCollection) {
        if (authRepository.currentSession() == null) {
            state.value = state.value.copy(error = "登录后才能复制清单", message = null)
            return
        }
        viewModelScope.launch {
            state.value = state.value.copy(isCreating = true, error = null, message = null)
            try {
                val copied = repository.copyCollection(collection)
                if (copied == null) {
                    state.value = state.value.copy(
                        isCreating = false,
                        error = "登录状态已失效，请重新登录"
                    )
                    return@launch
                }
                val myCollections = listOf(copied) + repository.listMyCollections().filterNot { it.id == copied.id }
                state.value = state.value.copy(
                    myCollections = myCollections,
                    isCreating = false,
                    message = "已复制到我的清单"
                )
                loadCollectionDetail(copied)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = state.value.copy(
                    isCreating = false,
                    error = userFacingError(error)
                )
            }
        }
    }

    fun clearMessage() {
        state.value = state.value.copy(error = null, message = null)
    }

    private suspend fun loadCollectionDetail(collection: VideoCollection) {
        state.value = state.value.copy(isDetailLoading = true, error = null, message = null)
        try {
            val detail = repository.getCollectionDetail(collection)
            state.value = state.value.copy(
                selectedDetail = detail,
                isDetailLoading = false
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state.value = state.value.copy(
                isDetailLoading = false,
                error = userFacingError(error)
            )
        }
    }

    private fun userFacingError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is ApiException && error.statusCode == 401 -> "登录状态已失效，请重新登录"
            error is ApiException && error.statusCode == 403 && message.contains("banned", ignoreCase = true) -> "账号已被封禁，无法操作共享清单"
            error is ApiException && error.statusCode == 403 && message.contains("muted", ignoreCase = true) -> "账号已被禁言，暂时不能操作共享清单"
            error is ApiException && error.statusCode == 403 -> "当前网络无法访问共享清单，请开启代理后重试"
            error is ApiException && error.statusCode == 404 -> "清单不存在或没有访问权限"
            error is ApiException && error.statusCode == 429 -> "清单操作太频繁，请稍后再试"
            message.contains("timeout", ignoreCase = true) -> "连接后端超时，请稍后重试"
            message.isBlank() -> "清单操作失败，请稍后重试"
            else -> "清单操作失败：$message"
        }
    }
}
