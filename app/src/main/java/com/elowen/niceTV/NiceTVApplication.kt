package com.elowen.niceTV

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.elowen.niceTV.data.db.AppDatabase
import com.elowen.niceTV.data.db.dao.DownloadDao
import com.elowen.niceTV.data.network.CookieManager
import com.elowen.niceTV.data.network.HttpClient
import com.elowen.niceTV.data.storage.NodeStorage
import com.elowen.niceTV.core.platform.proxy.ProxyRuntimeConfig
import com.elowen.niceTV.utils.CustomHttpDataSource
import com.elowen.niceTV.utils.GlideOkHttpIntegration
import java.io.File
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class NiceTVApplication : Application() {
    companion object {
        lateinit var simpleCache: SimpleCache
        lateinit var downloadManager: DownloadManager
        lateinit var dataSourceFactory: CacheDataSource.Factory
        lateinit var downloadDao: DownloadDao
        lateinit var mediaCacheDir: File
        lateinit var offlineMediaDir: File
        lateinit var nodeStorage: NodeStorage
    }

    override fun onCreate() {
        super.onCreate()
        HttpClient.init(CookieManager(this))
        GlideOkHttpIntegration.register(this)

        // 1. Database Init
        val db = AppDatabase.getDatabase(this)
        downloadDao = db.downloadDao()

        // 1.5 Proxy subsystem init
        ProxyRuntimeConfig.init(this)
        nodeStorage = NodeStorage(this)

        // 2. Cache Init (The "Shared Cache")
        // NoOpCacheEvictor -> We manually play garbage collector, so we don't want auto-eviction.
        val rootDir = getExternalFilesDir(null) ?: filesDir
        mediaCacheDir = File(rootDir, "media_cache")
        if (!mediaCacheDir.exists()) mediaCacheDir.mkdirs()
        offlineMediaDir = File(rootDir, "offline_media")
        if (!offlineMediaDir.exists()) offlineMediaDir.mkdirs()
        
        val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(this)
        simpleCache = SimpleCache(mediaCacheDir, NoOpCacheEvictor(), databaseProvider)

        // 3. DataSource Factories
        // Use a delegating Call.Factory so it always uses the latest playerClient
        // (proxy reconfiguration rebuilds playerClient, but OkHttpDataSource holds the factory reference)
        val dynamicCallFactory = okhttp3.Call.Factory { request -> HttpClient.playerClient.newCall(request) }
        val okHttpFactory = OkHttpDataSource.Factory(dynamicCallFactory)
        val upstreamFactory = CustomHttpDataSource.Factory(okHttpFactory)
        
        // Player Factory -> Read-Only (Read-Through) from cache
        // [FIX] We setCacheWriteDataSinkFactory(null) to avoid conflicts with DownloadTracker.
        // The DownloadTracker will handle the actual caching.
        dataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null) // Player ONLY READS
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 4. DownloadManager Init
        downloadManager = DownloadManager(
            this,
            databaseProvider,
            simpleCache,
            upstreamFactory,
            Executors.newFixedThreadPool(6)
        )
    }
}
