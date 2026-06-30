package com.elowen.niceTV.data.storage

import android.content.Context
import com.elowen.niceTV.data.models.Node
import com.elowen.niceTV.data.repository.NodeRepository

class NodeStorage(context: Context) {
    private val repository = NodeRepository(context.applicationContext)

    fun saveNodes(nodes: List<Node>) {
        kotlinx.coroutines.runBlocking {
            val existingNodes = repository.getAllNodes()
            existingNodes.forEach { repository.deleteNode(it.id) }
            repository.updateNodes(nodes)
        }
    }

    fun loadNodes(): List<Node> {
        return repository.getAllNodesSync()
    }

    fun addNode(node: Node) {
        kotlinx.coroutines.runBlocking { repository.addNode(node) }
    }

    fun updateNode(node: Node) {
        kotlinx.coroutines.runBlocking { repository.updateNode(node) }
    }

    fun deleteNode(nodeId: String) {
        kotlinx.coroutines.runBlocking {
            val activeNodeId = getActiveNodeId()
            if (activeNodeId == nodeId) { clearActiveNode() }
            repository.deleteNode(nodeId)
        }
    }

    fun getActiveNode(): Node? {
        return repository.getActiveNodeSync()
    }

    fun getActiveNodeId(): String? {
        return repository.getActiveNodeIdSync()
    }

    fun setActiveNode(nodeId: String) {
        repository.setActiveNodeSync(nodeId)
    }

    fun clearActiveNode() {
        kotlinx.coroutines.runBlocking { repository.clearActiveNode() }
    }
}
