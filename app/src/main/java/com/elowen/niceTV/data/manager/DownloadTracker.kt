package com.elowen.niceTV.data.manager

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.elowen.niceTV.MyDownloadService
import com.elowen.niceTV.NiceTVApplication
import com.elowen.niceTV.data.db.dao.DownloadDao
import com.elowen.niceTV.data.db.entity.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

@OptIn(UnstableApi::class)
class DownloadTracker(
    private val downloadManager: DownloadManager,
    private val downloadDao: DownloadDao
) {

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    init {
        downloadManager.addListener(DownloadManagerListener())
        applicationScope.launch {
            reconcileCompletedOfflineMedia()
        }
    }

    private val mergeLocks = ConcurrentHashMap<String, Mutex>()

    fun startDownload(
        context: Context,
        title: String, 
        postUrl: String, 
        videoUrl: String, 
        coverUrl: String,
        maker: String? = null,
        tags: List<String> = emptyList(),
        cast: List<String> = emptyList()
    ) {
        applicationScope.launch {
            val existing = downloadDao.getDownload(postUrl)
            val existingSameSource = existing?.takeIf { it.url == videoUrl }
            if (existing != null && existing.url != videoUrl) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "切换下载源，将替换旧的下载任务", Toast.LENGTH_SHORT).show()
                }
                removeDownloadInternal(postUrl)
            }

            val uri = videoUrl.toUri()
            val cacheKey = CacheKeyUtil.forUrl(videoUrl)
            val mimeType = guessMimeType(videoUrl)
            val downloadRequestBuilder = DownloadRequest.Builder(postUrl, uri)
                .setMimeType(mimeType)
            if (mimeType == MimeTypes.VIDEO_MP4) {
                downloadRequestBuilder.setCustomCacheKey(cacheKey)
            }
            val downloadRequest = downloadRequestBuilder
                .setData(title.toByteArray())
                .build()

            downloadDao.insert(
                DownloadEntity(
                    postUrl = postUrl,
                    url = videoUrl,
                    title = title,
                    coverUrl = coverUrl,
                    maker = maker,
                    tags = if (tags.isNotEmpty()) tags.joinToString(",") else null,
                    cast = if (cast.isNotEmpty()) cast.joinToString(",") else null,
                    mergedPath = existingSameSource?.mergedPath,
                    mergeState = existingSameSource?.mergeState ?: DownloadEntity.MERGE_NONE,
                    addedTimestamp = existingSameSource?.addedTimestamp ?: System.currentTimeMillis()
                )
            )
            withContext(Dispatchers.Main) {
                try {
                    DownloadService.sendAddDownload(
                        context,
                        MyDownloadService::class.java,
                        downloadRequest,
                        true
                    )
                    Toast.makeText(context, "开始下载: $title", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    applicationScope.launch {
                        downloadDao.deleteByUrl(postUrl)
                    }
                    Toast.makeText(context, downloadStartErrorText(e), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun removeDownload(context: Context, postUrl: String) {
        applicationScope.launch {
            removeDownloadInternal(postUrl)
            withContext(Dispatchers.Main) {
                listOf(postUrl).forEach { id ->
                    try {
                        DownloadService.sendRemoveDownload(context, MyDownloadService::class.java, id, false)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private suspend fun removeDownloadInternal(postUrl: String) {
        val entity = downloadDao.getDownload(postUrl)
        val videoUrl = entity?.url
        entity?.mergedPath?.let { path ->
            deleteManagedMergedFile(path)
        }
        val idsToRemove = mutableSetOf<String>()
        if (postUrl.isNotBlank()) idsToRemove.add(postUrl)
        if (!videoUrl.isNullOrBlank()) idsToRemove.addAll(findDownloadIdsByUrl(videoUrl))
        downloadDao.deleteByUrl(postUrl)
        idsToRemove.forEach { id ->
            if (id.isNotBlank()) downloadManager.removeDownload(id)
        }
        val resolvedUrl = videoUrl ?: getDownloadFromIndex(postUrl)?.request?.uri?.toString()
        if (!resolvedUrl.isNullOrEmpty()) {
            CacheManager.removeCacheByUrl(resolvedUrl)
        }
        kotlinx.coroutines.delay(2000)
        CacheManager.performGarbageCollection()
    }

    fun getAllDownloads(): Flow<List<Download>> = callbackFlow {
        val snapshots = ConcurrentHashMap<String, Download>()

        fun updateFromList(downloads: List<Download>) {
            downloads.forEach { download ->
                snapshots[download.request.id] = download
            }
        }

        fun emitSnapshot() {
            trySend(snapshots.values.toList())
        }

        updateFromList(downloadManager.currentDownloads)
        emitSnapshot()

        val indexJob = launch(Dispatchers.IO) {
            val indexed = loadDownloadsFromIndex()
            if (indexed.isNotEmpty()) {
                updateFromList(indexed)
                emitSnapshot()
            }
        }

        val listener = object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) {
                updateFromList(downloadManager.currentDownloads)
                emitSnapshot()
            }

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                snapshots[download.request.id] = download
                emitSnapshot()
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download
            ) {
                snapshots.remove(download.request.id)
                emitSnapshot()
            }
        }
        
        downloadManager.addListener(listener)
        val pollingJob = launch(Dispatchers.IO) {
            while (true) {
                val downloads = downloadManager.currentDownloads
                if (downloads.isNotEmpty()) {
                    updateFromList(downloads)
                    emitSnapshot()
                }
                val hasActive = downloads.any { dl ->
                    dl.state == Download.STATE_DOWNLOADING || dl.state == Download.STATE_QUEUED
                }
                delay(if (hasActive) 500L else 2000L)
            }
        }
        awaitClose {
            downloadManager.removeListener(listener)
            indexJob.cancel()
            pollingJob.cancel()
        }
    }

    fun getCurrentDownloads(): List<Download> {
        val snapshots = LinkedHashMap<String, Download>()
        loadDownloadsFromIndex().forEach { download ->
            snapshots[download.request.id] = download
        }
        downloadManager.currentDownloads.forEach { download ->
            snapshots[download.request.id] = download
        }
        return snapshots.values.toList()
    }

    private fun loadDownloadsFromIndex(): List<Download> {
        return try {
            val downloads = mutableListOf<Download>()
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    downloads.add(cursor.download)
                }
            }
            downloads
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getDownloadFromIndex(postUrl: String): Download? {
        return withContext(Dispatchers.IO) {
            try {
                downloadManager.downloadIndex.getDownload(postUrl)
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun findDownloadIdsByUrl(videoUrl: String): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val targetUrl = videoUrl.trim()
                val targetNoQuery = targetUrl.substringBefore('?')
                val targetCacheKey = CacheKeyUtil.forUrl(targetUrl)
                val matches = mutableSetOf<String>()
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        val request = download.request
                        val requestUrl = request.uri.toString()
                        val requestNoQuery = requestUrl.substringBefore('?')
                        if (requestUrl == targetUrl ||
                            requestNoQuery == targetNoQuery ||
                            request.customCacheKey == targetCacheKey ||
                            request.id == targetUrl ||
                            request.id == targetNoQuery
                        ) {
                            matches.add(request.id)
                        }
                    }
                }
                matches
            } catch (_: Exception) {
                emptySet()
            }
        }
    }

    private fun deleteManagedMergedFile(path: String) {
        runCatching {
            val target = File(path).canonicalFile
            val allowedRoots = listOf(
                NiceTVApplication.offlineMediaDir,
                File(NiceTVApplication.mediaCacheDir, "merged")
            ).map { it.canonicalFile }

            val isManagedFile = allowedRoots.any { root ->
                target.path == root.path || target.path.startsWith(root.path + File.separator)
            }
            if (isManagedFile && target.isFile) {
                target.delete()
            }
        }
    }

    /**
     * High-frequency progress observation for real-time UI updates.
     * Polls every 250ms during active downloads, 2s when idle.
     */
    @OptIn(UnstableApi::class)
    fun observeDownloadProgress(postId: String): Flow<Download?> = flow {
        while (true) {
            val download = getDownloadSnapshot(postId)
            emit(download)
            // If download is active, poll frequently; otherwise slower
            when (download?.state) {
                Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> delay(250)
                Download.STATE_COMPLETED -> break // Exit after emitting completed state
                else -> delay(2000) // Slower polling when idle/null
            }
        }
    }.flowOn(Dispatchers.IO)

    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            if (download.state == Download.STATE_COMPLETED) {
                scheduleOfflineMaterializationIfNeeded(download)
            }
        }
    }

    private fun guessMimeType(url: String): String? {
        val lower = url.substringBefore('?').lowercase(Locale.ROOT)
        val fullLower = url.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            lower.endsWith(".txt") -> MimeTypes.APPLICATION_M3U8
            fullLower.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            else -> null
        }
    }

    private fun scheduleOfflineMaterializationIfNeeded(download: Download) {
        val mimeType = download.request.mimeType ?: return
        if (download.state != Download.STATE_COMPLETED) return
        if (mimeType != MimeTypes.APPLICATION_M3U8) return
        scheduleHlsMerge(download)
    }

    private suspend fun reconcileCompletedOfflineMedia() {
        loadDownloadsFromIndex()
            .asSequence()
            .filter { it.state == Download.STATE_COMPLETED }
            .forEach { download ->
                scheduleOfflineMaterializationIfNeeded(download)
            }
    }

    private suspend fun getDownloadSnapshot(postId: String): Download? {
        val active = downloadManager.currentDownloads.find { it.request.id == postId }
        return active ?: getDownloadFromIndex(postId)
    }

    private fun hasUsableMergedFile(path: String?): Boolean {
        val mergedPath = path?.takeIf { it.isNotBlank() } ?: return false
        val mergedFile = File(mergedPath)
        return mergedFile.isFile && mergedFile.length() > 0L
    }

    private fun buildMergedOutputFile(entity: DownloadEntity): File {
        return File(
            NiceTVApplication.offlineMediaDir,
            "${CacheKeyUtil.forUrl(entity.postUrl)}.ts"
        )
    }

    private fun scheduleHlsMerge(download: Download) {
        val postUrl = download.request.id
        if (postUrl.isBlank()) return
        val lock = mergeLocks.getOrPut(postUrl) { Mutex() }
        applicationScope.launch {
            lock.withLock {
                val entity = downloadDao.getDownload(postUrl) ?: return@withLock
                val preferredMergedFile = buildMergedOutputFile(entity)
                if (
                    entity.mergeState == DownloadEntity.MERGE_COMPLETED &&
                    hasUsableMergedFile(entity.mergedPath)
                ) {
                    return@withLock
                }
                if (
                    entity.mergeState == DownloadEntity.MERGE_COMPLETED &&
                    preferredMergedFile.isFile &&
                    preferredMergedFile.length() > 0L
                ) {
                    downloadDao.updateMergeState(
                        postUrl,
                        preferredMergedFile.absolutePath,
                        DownloadEntity.MERGE_COMPLETED
                    )
                    return@withLock
                }

                downloadDao.updateMergeState(postUrl, entity.mergedPath, DownloadEntity.MERGE_IN_PROGRESS)
                try {
                    val mergedFile = mergeHlsDownload(entity)
                    if (mergedFile != null && hasUsableMergedFile(mergedFile.absolutePath)) {
                        downloadDao.updateMergeState(postUrl, mergedFile.absolutePath, DownloadEntity.MERGE_COMPLETED)
                    } else {
                        downloadDao.updateMergeState(postUrl, null, DownloadEntity.MERGE_FAILED)
                    }
                } catch (_: Exception) {
                    downloadDao.updateMergeState(postUrl, null, DownloadEntity.MERGE_FAILED)
                }
            }
        }
    }

    private fun mergeHlsDownload(entity: DownloadEntity): File? {
        val playlistUrl = entity.url
        if (playlistUrl.isBlank()) return null
        val playlistContent = readCachedText(playlistUrl) ?: return null
        val segmentUrls = parseM3u8Segments(playlistContent, playlistUrl)
        if (segmentUrls.isEmpty()) return null

        val outputDir = NiceTVApplication.offlineMediaDir
        if (!outputDir.exists() && !outputDir.mkdirs()) return null
        val outputFile = buildMergedOutputFile(entity)
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part")
        tempFile.delete()
        val merged = runCatching {
            tempFile.outputStream().use { out ->
                segmentUrls.forEach { segmentUrl ->
                    val segmentBytes = readCachedBytes(segmentUrl)?.takeIf { it.isNotEmpty() }
                        ?: throw IllegalStateException("Missing cached HLS segment")
                    out.write(segmentBytes)
                }
            }
            if (tempFile.length() <= 0L) {
                throw IllegalStateException("Merged HLS output is empty")
            }
            outputFile.delete()
            if (!tempFile.renameTo(outputFile)) {
                throw IllegalStateException("Failed to finalize merged HLS output")
            }
            outputFile
        }.getOrElse {
            tempFile.delete()
            outputFile.delete()
            return null
        }
        return merged
    }

    private fun readCachedText(url: String): String? {
        val bytes = readCachedBytes(url) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readCachedBytes(url: String): ByteArray? {
        val spans = listOf(url, CacheKeyUtil.forUrl(url))
            .distinct()
            .firstNotNullOfOrNull { key ->
                NiceTVApplication.simpleCache.getCachedSpans(key)
                    .takeIf { it.isNotEmpty() }
                    ?.sortedBy { span -> span.position }
            }
            ?: return null
        if (spans.isEmpty()) return null
        val output = ByteArrayOutputStream()
        var nextPosition = 0L
        for (span in spans) {
            if (span.position > nextPosition) {
                return null
            }
            val spanFile = span.file?.takeIf { it.isFile } ?: return null
            spanFile.inputStream().use { input ->
                input.copyTo(output)
            }
            nextPosition = maxOf(nextPosition, span.position + span.length)
        }
        return output.toByteArray()
    }

    private fun parseM3u8Segments(
        content: String,
        baseUrl: String,
        visitedPlaylists: MutableSet<String> = mutableSetOf()
    ): List<String> {
        if (!visitedPlaylists.add(baseUrl)) return emptyList()
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val hasUnsupportedEncryption = lines.any { line ->
            line.startsWith("#EXT-X-KEY", ignoreCase = true) &&
                !line.contains("METHOD=NONE", ignoreCase = true)
        }
        if (hasUnsupportedEncryption || lines.any { it.startsWith("#EXT-X-MAP", ignoreCase = true) }) {
            return emptyList()
        }

        val baseUri = URI(baseUrl)
        val variantPlaylistUrl = selectVariantPlaylist(lines, baseUri)
        if (variantPlaylistUrl != null) {
            val nestedContent = readCachedText(variantPlaylistUrl) ?: return emptyList()
            return parseM3u8Segments(nestedContent, variantPlaylistUrl, visitedPlaylists)
        }

        return lines.asSequence()
            .filter { !it.startsWith("#") }
            .flatMap { line ->
                val resolved = baseUri.resolve(line)
                val resolvedUrl = resolved.toString()
                if (isM3u8Url(resolvedUrl)) {
                    val nestedContent = readCachedText(resolvedUrl)
                    if (nestedContent == null) {
                        emptySequence()
                    } else {
                        parseM3u8Segments(nestedContent, resolvedUrl, visitedPlaylists).asSequence()
                    }
                } else {
                    sequenceOf(resolvedUrl)
                }
            }
            .toList()
    }

    private fun selectVariantPlaylist(lines: List<String>, baseUri: URI): String? {
        var bestBandwidth = -1
        var bestUrl: String? = null
        val bandwidthRegex = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
        for (index in lines.indices) {
            val line = lines[index]
            if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue
            val nextUri = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
            val bandwidth = bandwidthRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (bestUrl == null || bandwidth > bestBandwidth) {
                bestBandwidth = bandwidth
                bestUrl = baseUri.resolve(nextUri).toString()
            }
        }
        return bestUrl
    }

    private fun isM3u8Url(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.substringBefore('?').endsWith(".m3u8") || lower.contains(".m3u8")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: DownloadTracker? = null

        fun getInstance(): DownloadTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadTracker(
                    NiceTVApplication.downloadManager,
                    NiceTVApplication.downloadDao
                ).also { INSTANCE = it }
            }
        }
    }

    private fun downloadStartErrorText(error: Throwable): String {
        val message = error.message.orEmpty()
        val lower = message.lowercase(Locale.ROOT)
        return when {
            lower.contains("permission") || lower.contains("notification") ->
                "下载启动失败，请检查通知权限"
            lower.contains("foreground") || lower.contains("service") ->
                "下载服务启动失败，请稍后重试"
            lower.contains("network") || lower.contains("connect") ->
                "下载启动失败，请检查网络或代理"
            else -> "下载启动失败，请稍后重试"
        }
    }
}
