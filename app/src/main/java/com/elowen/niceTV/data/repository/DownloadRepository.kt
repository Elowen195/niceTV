package com.elowen.niceTV.data.repository

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import com.elowen.niceTV.data.db.entity.DownloadEntity
import com.elowen.niceTV.data.manager.CacheManager
import com.elowen.niceTV.data.manager.DownloadTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DownloadRepository(context: Context) {

    private val appContext = context.applicationContext
    private val tracker = DownloadTracker.getInstance()
    private val appScope = CoroutineScope(Dispatchers.IO)

    fun startDownload(
        title: String, 
        postUrl: String, 
        videoUrl: String, 
        coverUrl: String,
        maker: String? = null,
        tags: List<String> = emptyList(),
        cast: List<String> = emptyList()
    ) {
        appScope.launch {
            tracker.startDownload(appContext, title, postUrl, videoUrl, coverUrl, maker, tags, cast)
        }
    }

    fun removeDownload(postId: String) {
        tracker.removeDownload(appContext, postId)
    }

    fun getAllDownloads(): Flow<List<Download>> {
        return tracker.getAllDownloads()
    }

    fun observeDownloadProgress(postId: String): Flow<Download?> {
        return tracker.observeDownloadProgress(postId)
    }




    
    // For Cache Cleanup linkage
    fun checkAndCleanCache(url: String?) {
        CacheManager.checkAndCleanCache(url)
    }

    fun getDownloadEntities(): Flow<List<DownloadEntity>> {
        return com.elowen.niceTV.NiceTVApplication.downloadDao.getAllDownloads()
    }

    fun getDownloadSnapshotList(): List<Download> {
        return tracker.getCurrentDownloads()
    }
}
