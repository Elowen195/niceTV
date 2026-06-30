package com.elowen.niceTV.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订阅实体 - 存储订阅信息
 */
@Entity(
    tableName = "subscriptions",
    indices = [
        Index("lastUpdateTime"),
        Index("autoUpdate"),
    ]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // 订阅名称
    val url: String,                     // 订阅链接
    val useProxyForUpdate: Boolean,      // 更新时是否使用代理
    val autoUpdate: Boolean,             // 是否自动更新
    val updateInterval: Long,            // 更新间隔（小时）
    val lastUpdateTime: Long,            // 最后更新时间
    val speedTestUrl: String,            // 测速地址
    val speedTestTimeout: Int,           // 测速超时（秒）
    val nodeCount: Int,                  // 节点数量
    val createdTime: Long                // 创建时间
)
