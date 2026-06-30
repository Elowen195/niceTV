package com.elowen.niceTV.core.platform.proxy

/**
 * 代理认证凭证管理器
 * 用于存储和获取当前会话的代理认证凭证
 */
object ProxyCredentials {
    @Volatile
    private var credentials: Pair<String, String>? = null

    fun set(user: String, pass: String) {
        credentials = user to pass
    }

    fun get(): Pair<String, String>? = credentials

    fun clear() {
        credentials = null
    }

    fun isSet(): Boolean = credentials != null
}
