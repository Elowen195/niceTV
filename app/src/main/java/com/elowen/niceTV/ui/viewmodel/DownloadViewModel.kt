package com.elowen.niceTV.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.elowen.niceTV.NiceTVApplication
import com.elowen.niceTV.data.db.entity.DownloadEntity
import com.elowen.niceTV.data.manager.CacheManager
import com.elowen.niceTV.data.manager.CacheKeyUtil
import com.elowen.niceTV.data.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
data class DownloadItem(
    val entity: DownloadEntity,
    val download: Download?,
    val progressPercent: Float,
    val stateCode: Int?,
    val bytesDownloaded: Long,
    val storageBytes: Long
)

data class DownloadStorageStats(
    val totalBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val offlineBytes: Long = 0L,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0
)

data class DownloadListState(
    val items: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val stats: DownloadStorageStats = DownloadStorageStats()
)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DownloadViewModel(
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    val state = mutableStateOf(DownloadListState())

    init {
        loadDownloads()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            combine(
                downloadRepository.getDownloadEntities(),
                downloadRepository.getAllDownloads()
            ) { entities, downloads ->
                entities.map { entity ->
                    val download = downloads.find { it.request.id == entity.postUrl }
                    toDownloadItem(entity, download)
                }
            }.collectLatest { items ->
                val stats = withContext(Dispatchers.IO) { buildStorageStats(items) }
                state.value = state.value.copy(items = items, stats = stats)
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)
            val entities = downloadRepository.getDownloadEntities().first()
            val downloads = downloadRepository.getDownloadSnapshotList()
            val items = entities.map { entity ->
                val download = downloads.find { it.request.id == entity.postUrl }
                toDownloadItem(entity, download)
            }
            val stats = withContext(Dispatchers.IO) { buildStorageStats(items) }
            state.value = state.value.copy(items = items, isLoading = false, stats = stats)
        }
    }
    
    fun removeDownload(postUrl: String) {
        viewModelScope.launch {
            downloadRepository.removeDownload(postUrl)
        }
    }

    fun retryDownload(entity: DownloadEntity) {
        viewModelScope.launch {
            downloadRepository.startDownload(
                title = entity.title,
                postUrl = entity.postUrl,
                videoUrl = entity.url,
                coverUrl = entity.coverUrl,
                maker = entity.maker,
                tags = entity.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                cast = entity.cast?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            )
        }
    }

    fun clearFailedDownloads() {
        viewModelScope.launch {
            state.value.items
                .filter { item ->
                    item.stateCode == Download.STATE_FAILED ||
                        item.entity.mergeState == DownloadEntity.MERGE_FAILED
                }
                .forEach { item ->
                    downloadRepository.removeDownload(item.entity.postUrl)
                }
        }
    }

    fun cleanUnusedCache() {
        viewModelScope.launch {
            state.value = state.value.copy(isCleaning = true)
            withContext(Dispatchers.IO) {
                CacheManager.performGarbageCollectionNow()
            }
            refresh()
            state.value = state.value.copy(isCleaning = false)
        }
    }

    private fun toDownloadItem(entity: DownloadEntity, download: Download?): DownloadItem {
        val offlinePath = usableOfflinePath(entity)
        val effectiveEntity = if (offlinePath != null &&
            (entity.mergeState != DownloadEntity.MERGE_COMPLETED || entity.mergedPath != offlinePath)
        ) {
            entity.copy(mergedPath = offlinePath, mergeState = DownloadEntity.MERGE_COMPLETED)
        } else {
            entity
        }
        val hasOfflineFile = offlinePath != null
        val progress = if (hasOfflineFile) 100f else calculateDownloadProgress(download)
        val storageBytes = calculateItemStorageBytes(effectiveEntity, download)
        val stateCode = if (hasOfflineFile) Download.STATE_COMPLETED else download?.state
        return DownloadItem(
            entity = effectiveEntity,
            download = download,
            progressPercent = progress,
            stateCode = stateCode,
            bytesDownloaded = download?.bytesDownloaded ?: storageBytes,
            storageBytes = storageBytes
        )
    }

    private fun calculateDownloadProgress(download: Download?): Float {
        if (download == null) return -1f
        val percent = download.percentDownloaded
        if (percent >= 0f) {
            val length = download.contentLength
            return if (percent == 0f && length <= 0 && download.bytesDownloaded > 0) {
                -1f
            } else {
                percent
            }
        }
        val length = download.contentLength
        return if (length > 0) {
            ((download.bytesDownloaded.toDouble() / length.toDouble()) * 100.0).toFloat()
                .coerceIn(0f, 100f)
        } else {
            -1f
        }
    }

    private fun buildStorageStats(items: List<DownloadItem>): DownloadStorageStats {
        val cacheBytes = directorySize(NiceTVApplication.mediaCacheDir)
        val offlineBytes = directorySize(NiceTVApplication.offlineMediaDir)
        return DownloadStorageStats(
            totalBytes = cacheBytes + offlineBytes,
            cacheBytes = cacheBytes,
            offlineBytes = offlineBytes,
            activeCount = items.count { item ->
                item.stateCode == Download.STATE_DOWNLOADING ||
                    item.stateCode == Download.STATE_QUEUED ||
                    item.stateCode == Download.STATE_RESTARTING
            },
            completedCount = items.count { item ->
                item.stateCode == Download.STATE_COMPLETED ||
                    item.entity.mergeState == DownloadEntity.MERGE_COMPLETED
            },
            failedCount = items.count { item ->
                item.stateCode == Download.STATE_FAILED ||
                    item.entity.mergeState == DownloadEntity.MERGE_FAILED
            }
        )
    }

    private fun calculateItemStorageBytes(entity: DownloadEntity, download: Download?): Long {
        val mergedBytes = entity.mergedPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?.length()
            ?: 0L
        return maxOf(mergedBytes, download?.bytesDownloaded ?: 0L)
    }

    private fun usableOfflinePath(entity: DownloadEntity): String? {
        entity.mergedPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile && it.length() > 0L }
            ?.absolutePath
            ?.let { return it }

        return File(NiceTVApplication.offlineMediaDir, "${CacheKeyUtil.forUrl(entity.postUrl)}.ts")
            .takeIf { it.isFile && it.length() > 0L }
            ?.absolutePath
    }

    private fun directorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { child -> directorySize(child) } ?: 0L
    }
}

class DownloadViewModelFactory(
    private val downloadRepository: DownloadRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DownloadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DownloadViewModel(downloadRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
