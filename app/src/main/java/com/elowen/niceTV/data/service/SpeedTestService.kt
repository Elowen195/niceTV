package com.elowen.niceTV.data.service

import android.content.Context
import android.util.Log
import com.elowen.niceTV.core.BoxManager
import com.elowen.niceTV.core.platform.proxy.ProxyHttpClientFactory
import com.elowen.niceTV.core.platform.proxy.ProxyRuntimeConfig
import com.elowen.niceTV.data.models.Node
import com.elowen.niceTV.data.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.math.max

sealed class SpeedTestResult {
    data class Success(val latency: Long) : SpeedTestResult()
    data object Failed : SpeedTestResult()
    data object Testing : SpeedTestResult()
    data object Pending : SpeedTestResult()
}

class SpeedTestService(private val context: Context) {
    private val repository = SubscriptionRepository(context)

    companion object {
        private const val TAG = "SpeedTestService"
        private val TEST_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://www.cloudflare.com/cdn-cgi/trace",
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://cp.cloudflare.com/",
            "https://www.google.com/generate_204"
        )
        private const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
        private const val DEFAULT_TIMEOUT = 8000L
        private const val CONCURRENT_LIMIT = 1
        private const val TEST_ROUNDS = 3
    }

    suspend fun testNode(
        node: Node,
        testUrl: String = DEFAULT_TEST_URL,
        timeout: Long = DEFAULT_TIMEOUT
    ): Long? {
        return withContext(Dispatchers.IO) {
            val previousNode = repository.getActiveNode()
            val wasRunning = BoxManager.isRunning
            var latency: Long? = null
            try {
                repository.setActiveNode(node.id)
                delay(500)

                val proxyPort = ProxyRuntimeConfig.getPort(context)
                BoxManager.restart(context, node, proxyPort)
                delay(1000)

                latency = measureLatency(testUrl, timeout)
                latency
            } catch (e: Exception) {
                Log.e(TAG, "Failed to test node", e)
                null
            } finally {
                runCatching {
                    if (previousNode != null) {
                        repository.setActiveNode(previousNode.id)
                    } else {
                        repository.clearActiveNode()
                    }

                    val proxyPort = ProxyRuntimeConfig.getPort(context)
                    if (wasRunning && previousNode != null) {
                        BoxManager.restart(context, previousNode, proxyPort)
                    } else {
                        BoxManager.stop()
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to restore proxy after speed test", e)
                }
                runCatching {
                    repository.updateNodeSpeedTest(node.id, latency ?: 0L)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to save speed test result", e)
                }
            }
        }
    }

    suspend fun testCurrentNode(timeout: Long = DEFAULT_TIMEOUT): Long? {
        return withContext(Dispatchers.IO) {
            try {
                measureLatencyWithFallback(timeout)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to test current node", e)
                null
            }
        }
    }

    suspend fun testNodes(
        nodes: List<Node>,
        testUrl: String = DEFAULT_TEST_URL,
        timeout: Long = DEFAULT_TIMEOUT,
        onProgress: suspend (String, SpeedTestResult) -> Unit
    ): Map<String, SpeedTestResult> {
        return withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, SpeedTestResult>()

            nodes.forEach { node ->
                results[node.id] = SpeedTestResult.Pending
                onProgress(node.id, SpeedTestResult.Pending)
            }

            nodes.chunked(CONCURRENT_LIMIT).forEach { chunk ->
                coroutineScope {
                    chunk.map { node ->
                        async {
                            results[node.id] = SpeedTestResult.Testing
                            onProgress(node.id, SpeedTestResult.Testing)
                            val latency = testNode(node, testUrl, timeout)
                            val result = if (latency != null) {
                                SpeedTestResult.Success(latency)
                            } else {
                                SpeedTestResult.Failed
                            }
                            results[node.id] = result
                            onProgress(node.id, result)
                        }
                    }.forEach { it.await() }
                }
            }

            results
        }
    }

    private fun measureLatency(testUrl: String, timeout: Long): Long? {
        return try {
            val timeoutSeconds = max(1, ((timeout + 999) / 1000))
            val client = ProxyHttpClientFactory.createSocksClient(
                connectTimeoutSeconds = timeoutSeconds,
                readTimeoutSeconds = timeoutSeconds,
                writeTimeoutSeconds = timeoutSeconds
            ).newBuilder()
                .followRedirects(false)
                .build()

            val latencies = mutableListOf<Long>()

            repeat(TEST_ROUNDS) { round ->
                try {
                    val request = Request.Builder()
                        .url(testUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .get()
                        .build()

                    val startTime = System.nanoTime()
                    client.newCall(request).execute().use { response ->
                        val endTime = System.nanoTime()
                        if (response.code in 200..399) {
                            val latency = (endTime - startTime) / 1_000_000
                            latencies.add(latency)
                        }
                    }

                    if (round < TEST_ROUNDS - 1) {
                        Thread.sleep(100)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Latency round failed", e)
                }
            }

            if (latencies.isNotEmpty()) {
                latencies.sorted()[latencies.size / 2]
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to measure latency", e)
            null
        }
    }

    private fun measureLatencyWithFallback(timeout: Long): Long? {
        for (url in TEST_URLS) {
            val latency = measureLatency(url, timeout)
            if (latency != null) {
                return latency
            }
        }
        return null
    }
}
