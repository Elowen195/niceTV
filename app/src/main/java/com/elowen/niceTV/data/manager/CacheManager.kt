package com.elowen.niceTV.data.manager

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.elowen.niceTV.NiceTVApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
object CacheManager {

    private val scope = CoroutineScope(Dispatchers.IO)

    // Call this when opening the app or opening a new video
    fun performGarbageCollection() {
        scope.launch {
            performGarbageCollectionNow()
        }
    }

    suspend fun performGarbageCollectionNow() {
        val dbUrls = NiceTVApplication.downloadDao.getVideoUrls()
        val indexUrls = loadUrlsFromDownloadIndex()
        val validUrls = (dbUrls + indexUrls)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val validKeys = mutableSetOf<String>()
        val keepPrefixes = mutableSetOf<String>()
        validUrls.forEach { url ->
            validKeys.add(CacheKeyUtil.forUrl(url))
            validKeys.add(url)
            if (shouldUsePrefix(url)) {
                buildBaseUrlPrefix(url)?.let { keepPrefixes.add(it) }
            }
        }

        val cache = NiceTVApplication.simpleCache
        val keys = cache.keys.toList()
        for (key in keys) {
            if (validKeys.contains(key)) continue
            if (key.startsWith("http")) {
                val keyNoQuery = key.substringBefore('?')
                if (keepPrefixes.any { keyNoQuery.startsWith(it) }) {
                    continue
                }
            }
            cache.removeResource(key)
        }

        // Clean up orphaned merged HLS files
        cleanOrphanedMergedFiles()
    }

    // Call when closing a specific video (Optional optimization, GC covers it)
    fun checkAndCleanCache(url: String?) {
        if (url == null) return
        scope.launch {
             // Check if it is downloaded
             val exists = NiceTVApplication.downloadDao.getDownloadByVideoUrl(url) != null
             if (!exists) {
                  removeCacheByUrlInternal(url)
             }
        }
    }

    fun removeCacheByUrl(url: String?) {
        if (url.isNullOrBlank()) return
        scope.launch {
            removeCacheByUrlInternal(url)
        }
    }

    private fun removeCacheByUrlInternal(url: String) {
        val cache = NiceTVApplication.simpleCache
        val key = CacheKeyUtil.forUrl(url)
        cache.removeResource(key)
        cache.removeResource(url)
        if (shouldUsePrefix(url)) {
            purgeByPrefix(cache, url)
        }
    }

    private fun loadUrlsFromDownloadIndex(): List<String> {
        return try {
            val urls = mutableListOf<String>()
            NiceTVApplication.downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = cursor.download.request.uri.toString()
                    if (!uri.isNullOrBlank()) {
                        urls.add(uri)
                    }
                }
            }
            urls
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun shouldUsePrefix(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase(java.util.Locale.ROOT)
        return !lower.endsWith(".mp4")
    }

    private fun purgeByPrefix(cache: androidx.media3.datasource.cache.SimpleCache, videoUrl: String) {
        val basePrefix = buildBaseUrlPrefix(videoUrl) ?: return
        val baseNoQuery = basePrefix.substringBefore('?')
        val keys = cache.keys.toList()
        for (key in keys) {
            if (!key.startsWith("http")) continue
            val keyNoQuery = key.substringBefore('?')
            if (keyNoQuery.startsWith(baseNoQuery)) {
                cache.removeResource(key)
            }
        }
    }

    private fun buildBaseUrlPrefix(videoUrl: String): String? {
        val cleaned = videoUrl.substringBefore('?')
        val uri = cleaned.toUri()
        val scheme = uri.scheme ?: return null
        val authority = uri.encodedAuthority ?: return null
        val path = uri.encodedPath ?: return "$scheme://$authority/"
        val basePath = if (path.endsWith("/")) path else path.substringBeforeLast('/', path)
        val normalizedPath = if (basePath.endsWith("/")) basePath else "$basePath/"
        return "$scheme://$authority$normalizedPath"
    }

    private suspend fun cleanOrphanedMergedFiles() {
        try {
            val validPaths = NiceTVApplication.downloadDao.getAllMergedPaths()
                .mapNotNull { path ->
                    runCatching { java.io.File(path).canonicalPath }.getOrNull()
                }
                .toSet()

            val mergedDirs = listOf(
                NiceTVApplication.offlineMediaDir,
                java.io.File(NiceTVApplication.mediaCacheDir, "merged")
            ).map { it.canonicalFile }

            mergedDirs.forEach { mergedDir ->
                if (!mergedDir.exists() || !mergedDir.isDirectory) return@forEach
                mergedDir.listFiles()?.forEach { file ->
                    val isOfflineMergedFile = file.name.startsWith("video_") &&
                        (file.extension.equals("ts", ignoreCase = true) ||
                            file.name.endsWith(".ts.part", ignoreCase = true))
                    val isManagedMergedFile = file.isFile &&
                        (mergedDir != NiceTVApplication.offlineMediaDir.canonicalFile ||
                            isOfflineMergedFile)
                    if (isManagedMergedFile && file.canonicalPath !in validPaths) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) { }
    }
}
