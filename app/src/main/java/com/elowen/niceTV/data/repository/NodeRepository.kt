package com.elowen.niceTV.data.repository

import android.content.Context
import com.elowen.niceTV.data.db.AppDatabase
import com.elowen.niceTV.data.db.NodeEntity
import com.elowen.niceTV.data.models.Node
import com.elowen.niceTV.data.models.Protocol
import com.elowen.niceTV.data.models.Security
import com.elowen.niceTV.data.models.SecuritySettings
import com.elowen.niceTV.data.models.Transport
import com.elowen.niceTV.data.models.TransportSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class NodeRepository(context: Context) {
    private val nodeDao = AppDatabase.getDatabase(context).nodeDao()

    fun getActiveNodeSync(): Node? {
        return runBlocking {
            nodeDao.getActiveNode()?.toNode()
        }
    }

    fun getAllNodesSync(): List<Node> {
        return runBlocking {
            nodeDao.getAll().map { entity -> entity.toNode() }
        }
    }

    fun setActiveNodeSync(nodeId: String) {
        runBlocking {
            setActiveNode(nodeId)
        }
    }

    fun getActiveNodeIdSync(): String? {
        return runBlocking {
            nodeDao.getActiveNode()?.id
        }
    }

    suspend fun getActiveNode(): Node? {
        return nodeDao.getActiveNode()?.toNode()
    }

    fun getAllNodesFlow(): Flow<List<Node>> {
        return nodeDao.getAllFlow().map { entities ->
            entities.map { it.toNode() }
        }
    }

    suspend fun getAllNodes(): List<Node> {
        return nodeDao.getAll().map { entity -> entity.toNode() }
    }

    suspend fun getNodeById(nodeId: String): Node? {
        return nodeDao.getById(nodeId)?.toNode()
    }

    suspend fun setActiveNode(nodeId: String) {
        nodeDao.clearActiveStatus()
        nodeDao.setActive(nodeId)
    }

    suspend fun clearActiveNode() {
        nodeDao.clearActiveStatus()
    }

    suspend fun addNode(node: Node) {
        nodeDao.insert(node.toEntity())
    }

    suspend fun updateNode(node: Node) {
        nodeDao.update(node.toEntity())
    }

    suspend fun deleteNode(nodeId: String) {
        nodeDao.deleteById(nodeId)
    }

    suspend fun updateNodeStatus(nodeId: String, latency: Long, isAvailable: Boolean = true) {
        nodeDao.updateSpeedTest(id = nodeId, latency = latency, time = System.currentTimeMillis())
    }

    suspend fun updateNodes(nodes: List<Node>) {
        nodeDao.insertAll(nodes.map { it.toEntity() })
    }

    private fun NodeEntity.toNode(): Node {
        return Node(
            id = id, name = name, protocol = Protocol.valueOf(protocol),
            address = address, port = port, uuid = uuid, password = password,
            obfsPassword = obfsPassword, transport = Transport.valueOf(transport),
            transportSettings = transportSettings?.let { TransportSettings.fromJson(JSONObject(it)) },
            security = Security.valueOf(security),
            securitySettings = securitySettings?.let { SecuritySettings.fromJson(JSONObject(it)) },
            flow = flow, latency = latency, lastTestTime = lastTestTime, isActive = isActive
        )
    }

    private fun Node.toEntity(): NodeEntity {
        return NodeEntity(
            id = id, name = name, protocol = protocol.name, address = address,
            port = port, uuid = uuid, password = password, obfsPassword = obfsPassword,
            transport = transport.name,
            transportSettings = transportSettings?.toJson()?.toString(),
            security = security.name,
            securitySettings = securitySettings?.toJson()?.toString(),
            flow = flow, subscriptionId = null, latency = latency,
            lastTestTime = lastTestTime, isAvailable = latency >= 0, isActive = isActive
        )
    }
}
