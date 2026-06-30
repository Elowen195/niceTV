package com.elowen.niceTV.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 节点实体 - 存储代理节点信息
 */
@Entity(
    tableName = "nodes",
    indices = [
        Index("subscriptionId"),
        Index("isActive"),
        Index("latency"),
    ]
)
data class NodeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val password: String? = null,
    val obfsPassword: String? = null,

    // Transport settings (JSON string)
    val transport: String,
    val transportSettings: String? = null,

    // Security settings (JSON string)
    val security: String,
    val securitySettings: String? = null,

    // Flow control
    val flow: String? = null,

    // Subscription info
    val subscriptionId: Long? = null,    // 所属订阅ID（null表示手动添加）

    // Speed test info
    val latency: Long = -1,              // -1 = not tested, 0 = timeout
    val lastTestTime: Long = 0,
    val isAvailable: Boolean = true,

    // Active status
    val isActive: Boolean = false
)
