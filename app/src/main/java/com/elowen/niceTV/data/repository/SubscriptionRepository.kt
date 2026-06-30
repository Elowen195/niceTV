package com.elowen.niceTV.data.repository

import android.content.Context
import android.util.Log
import com.elowen.niceTV.data.db.AppDatabase
import com.elowen.niceTV.data.db.NodeEntity
import com.elowen.niceTV.data.db.SubscriptionEntity
import com.elowen.niceTV.data.models.Node
import com.elowen.niceTV.data.models.Protocol
import com.elowen.niceTV.data.models.Security
import com.elowen.niceTV.data.models.SecuritySettings
import com.elowen.niceTV.data.models.Transport
import com.elowen.niceTV.data.models.TransportSettings
import com.elowen.niceTV.data.parser.SubscriptionParser
import com.elowen.niceTV.core.platform.proxy.ProxyHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SubscriptionRepository(context: Context) {
    companion object {
        private const val TAG = "SubscriptionRepository"
    }

    private val db = AppDatabase.getDatabase(context)
    private val subscriptionDao = db.subscriptionDao()
    private val nodeDao = db.nodeDao()

    suspend fun getAllSubscriptions(): List<SubscriptionEntity> {
        return withContext(Dispatchers.IO) { subscriptionDao.getAll() }
    }

    suspend fun addSubscription(subscription: SubscriptionEntity): Long {
        return withContext(Dispatchers.IO) { subscriptionDao.insert(subscription) }
    }

    suspend fun updateSubscription(subscription: SubscriptionEntity) {
        withContext(Dispatchers.IO) { subscriptionDao.update(subscription) }
    }

    suspend fun updateSubscriptionAutoUpdate(subscriptionId: Long, autoUpdate: Boolean) {
        withContext(Dispatchers.IO) {
            val subscription = subscriptionDao.getById(subscriptionId)
            if (subscription != null) {
                subscriptionDao.update(subscription.copy(autoUpdate = autoUpdate))
            }
        }
    }

    suspend fun deleteSubscription(subscriptionId: Long) {
        withContext(Dispatchers.IO) {
            nodeDao.deleteBySubscription(subscriptionId)
            subscriptionDao.deleteById(subscriptionId)
        }
    }

    suspend fun updateSubscriptionContent(subscriptionId: Long, useProxy: Boolean): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val subscription = subscriptionDao.getById(subscriptionId)
                    ?: return@withContext Result.failure(Exception("订阅不存在"))
                val content = downloadSubscription(subscription.url, useProxy)
                val nodes = SubscriptionParser.parseSubscription(content)
                if (nodes.isEmpty()) {
                    return@withContext Result.failure(Exception("未解析到任何节点"))
                }
                val activeNodeId = nodeDao.getActiveNode()?.id
                nodeDao.deleteBySubscription(subscriptionId)
                val nodeEntities = nodes.map { node ->
                    node.copy(isActive = node.id == activeNodeId).toEntity(subscriptionId)
                }
                nodeDao.insertAll(nodeEntities)
                val updatedSubscription = subscription.copy(
                    nodeCount = nodes.size, lastUpdateTime = System.currentTimeMillis()
                )
                subscriptionDao.update(updatedSubscription)
                Result.success("更新成功：${nodes.size} 个节点")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update subscription", e)
                Result.failure(e)
            }
        }
    }

    private fun downloadSubscription(url: String, useProxy: Boolean): String {
        val client = if (useProxy) {
            ProxyHttpClientFactory.createSocksClient(connectTimeoutSeconds = 30, readTimeoutSeconds = 30, writeTimeoutSeconds = 30)
        } else {
            OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        }
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("下载失败: ${response.code}")
            return response.body.string()
        }
    }

    suspend fun getSubscriptionNodes(subscriptionId: Long): List<Node> {
        return withContext(Dispatchers.IO) { nodeDao.getNodesBySubscription(subscriptionId).map { it.toNode() } }
    }

    suspend fun getManualNodes(): List<Node> {
        return withContext(Dispatchers.IO) { nodeDao.getManualNodes().map { it.toNode() } }
    }

    suspend fun getAllNodes(): List<Node> {
        return withContext(Dispatchers.IO) { nodeDao.getAll().map { it.toNode() } }
    }

    suspend fun addManualNode(node: Node) {
        withContext(Dispatchers.IO) { nodeDao.insert(node.toEntity(null)) }
    }

    suspend fun deleteNode(nodeId: String) {
        withContext(Dispatchers.IO) { nodeDao.deleteById(nodeId) }
    }

    suspend fun setActiveNode(nodeId: String) {
        withContext(Dispatchers.IO) { nodeDao.clearActiveStatus(); nodeDao.setActive(nodeId) }
    }

    suspend fun clearActiveNode() {
        withContext(Dispatchers.IO) { nodeDao.clearActiveStatus() }
    }

    suspend fun getActiveNode(): Node? {
        return withContext(Dispatchers.IO) { nodeDao.getActiveNode()?.toNode() }
    }

    suspend fun updateNodeSpeedTest(nodeId: String, latency: Long) {
        withContext(Dispatchers.IO) { nodeDao.updateSpeedTest(nodeId, latency, System.currentTimeMillis()) }
    }
}

private fun Node.toEntity(subscriptionId: Long?): NodeEntity {
    return NodeEntity(
        id = id, name = name, protocol = protocol.name, address = address,
        port = port, uuid = uuid, password = password, obfsPassword = obfsPassword,
        transport = transport.name,
        transportSettings = transportSettings?.toJson()?.toString(),
        security = security.name,
        securitySettings = securitySettings?.toJson()?.toString(),
        flow = flow, subscriptionId = subscriptionId, latency = latency,
        lastTestTime = lastTestTime, isAvailable = true, isActive = isActive
    )
}

private fun NodeEntity.toNode(): Node {
    return Node(
        id = id, name = name,
        protocol = Protocol.valueOf(protocol),
        address = address, port = port, uuid = uuid, password = password,
        obfsPassword = obfsPassword,
        transport = Transport.valueOf(transport),
        transportSettings = transportSettings?.let { TransportSettings.fromJson(org.json.JSONObject(it)) },
        security = Security.valueOf(security),
        securitySettings = securitySettings?.let { SecuritySettings.fromJson(org.json.JSONObject(it)) },
        flow = flow, latency = latency, lastTestTime = lastTestTime, isActive = isActive
    )
}
