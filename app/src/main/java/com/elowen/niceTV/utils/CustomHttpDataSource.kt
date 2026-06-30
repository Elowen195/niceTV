package com.elowen.niceTV.utils

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * 自定义 HttpDataSource，用于处理伪装的文件扩展名 (深度还原自 Java 老祖版本)
 * 将 .txt 伪装的 m3u8 文件和 .woff2 伪装的 ts 切片正确识别并修正 Content-Type
 */
@UnstableApi
class CustomHttpDataSource(private val wrappedDataSource: HttpDataSource) : HttpDataSource {
    private var currentUri: Uri? = null

    override fun setRequestProperty(name: String, value: String) {
        wrappedDataSource.setRequestProperty(name, value)
    }

    override fun clearRequestProperty(name: String) {
        wrappedDataSource.clearRequestProperty(name)
    }

    override fun clearAllRequestProperties() {
        wrappedDataSource.clearAllRequestProperties()
    }

    override fun getResponseCode(): Int {
        return wrappedDataSource.responseCode
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        val originalHeaders = wrappedDataSource.responseHeaders
        val uriString = currentUri?.toString() ?: return originalHeaders

        // 如果当前 URI 是伪装的文件，修改 Content-Type
        val modifiedHeaders = HashMap(originalHeaders)

        if (uriString.endsWith(".txt") || uriString.contains("index-f") || uriString.contains(".urlset")) {
            // .txt 文件或包含 HLS 特征的 URL，强制设置为 HLS 播放列表类型
            modifiedHeaders["Content-Type"] = listOf("application/vnd.apple.mpegurl")
            // Log.d("CustomDataSource", "修改 Content-Type 为 HLS 播放列表: $uriString")
        } else if (uriString.endsWith(".woff2") || uriString.contains("seg-")) {
            // .woff2 文件或包含 seg- 的 URL，强制设置为 TS 切片类型
            modifiedHeaders["Content-Type"] = listOf("video/mp2t")
            // Log.d("CustomDataSource", "修改 Content-Type 为 TS 切片: $uriString")
        }

        return modifiedHeaders
    }

    override fun getUri(): Uri? {
        return wrappedDataSource.uri
    }

    @Throws(HttpDataSource.HttpDataSourceException::class)
    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        val url = currentUri.toString()
        // Log.d("CustomDataSource", "打开数据源: $url")
        
        // [FIX] Inject Referer if stored in CookieManager
        val referer = com.elowen.niceTV.data.network.CookieManager.getReferer(url)
        if (referer != null) {
            wrappedDataSource.setRequestProperty("Referer", referer)
            // Log.d("CustomDataSource", "注入 Referer: $referer")
        } else {
            wrappedDataSource.clearRequestProperty("Referer")
        }
        
        return try {
            wrappedDataSource.open(dataSpec)
        } catch (e: HttpDataSource.HttpDataSourceException) {
            // Log.e("CustomDataSource", "打开数据源失败: ${e.message}")
            throw e
        }
    }

    @Throws(HttpDataSource.HttpDataSourceException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return wrappedDataSource.read(buffer, offset, readLength)
    }

    @Throws(HttpDataSource.HttpDataSourceException::class)
    override fun close() {
        wrappedDataSource.close()
        currentUri = null
    }

    override fun addTransferListener(transferListener: TransferListener) {
        wrappedDataSource.addTransferListener(transferListener)
    }

    /**
     * 自定义 DataSource 工厂
     */
    class Factory(private val wrappedFactory: HttpDataSource.Factory) : HttpDataSource.Factory {
        override fun createDataSource(): HttpDataSource {
            return CustomHttpDataSource(wrappedFactory.createDataSource())
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
            wrappedFactory.setDefaultRequestProperties(defaultRequestProperties)
            return this
        }
    }
}
