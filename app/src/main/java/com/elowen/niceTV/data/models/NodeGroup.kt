package com.elowen.niceTV.data.models

import com.elowen.niceTV.data.db.SubscriptionEntity

/**
 * 节点分组
 */
sealed class NodeGroup {
    /**
     * 手动添加的节点组
     */
    data class Manual(
        val nodes: List<Node>
    ) : NodeGroup()

    /**
     * 订阅节点组
     */
    data class Subscription(
        val subscription: SubscriptionEntity,
        val nodes: List<Node>
    ) : NodeGroup()
}
