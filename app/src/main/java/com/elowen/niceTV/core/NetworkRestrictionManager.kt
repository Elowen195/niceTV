package com.elowen.niceTV.core

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.text.htmlEncode
import com.elowen.niceTV.data.storage.NodeStorage
import com.elowen.niceTV.data.models.Node
import com.elowen.niceTV.data.models.Protocol
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream

/**
 * 网络限制管理器
 * 在 Singbox 未启动或节点未设置时，禁止任何联网请求
 */
object NetworkRestrictionManager {

    private var isRunning: Boolean = false
    private var activeNode: Node? = null
    @Volatile
    private var isProxyOverrideApplied: Boolean = false
    @Volatile
    private var isProxyReachable: Boolean = false
    @Volatile
    private var strictProxyMode: Boolean = true

    private val _proxyReadyFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val proxyReadyFlow = _proxyReadyFlow.asStateFlow()

    fun initialize(context: Context) {
        val storage = NodeStorage(context)
        activeNode = storage.getActiveNode()
    }

    fun updateRunningState(running: Boolean) {
        isRunning = running
        if (!running) {
            isProxyReachable = false
        }
        updateFlow()
    }

    fun updateProxyOverrideApplied(applied: Boolean) {
        isProxyOverrideApplied = applied
        updateFlow()
    }

    fun updateProxyReachable(reachable: Boolean) {
        isProxyReachable = reachable
        updateFlow()
    }

    fun setStrictProxyMode(enabled: Boolean) {
        strictProxyMode = enabled
        updateFlow()
    }

    private fun updateFlow() {
        _proxyReadyFlow.value = isProxyReady()
    }

    fun isRequestAllowed(request: WebResourceRequest?): Boolean {
        if (request == null) return false

        val url = request.url?.toString() ?: return false

        if (url.startsWith("data:")) return true
        if (url.startsWith("file:")) return true
        if (url.startsWith("about:")) return true
        if (url.startsWith("chrome-extension:")) return true

        val shouldBlockNetwork = !isProxyReady()
        return !shouldBlockNetwork
    }

    fun isProxyReady(): Boolean {
        if (!isRunning) return false
        val node = activeNode ?: return false

        if (strictProxyMode) {
            if (node.protocol == Protocol.DIRECT) return false
            if (!isProxyOverrideApplied) return false
            if (!isProxyReachable) return false
        }

        return true
    }

    fun isDirectMode(): Boolean {
        return activeNode?.protocol == Protocol.DIRECT
    }

    fun getBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/html",
            "utf-8",
            ByteArrayInputStream(getBlockedHtml().toByteArray())
        ).apply {
            setStatusCodeAndReasonPhrase(418, "I'm a teapot (Proxy not ready)")
        }
    }
    fun getDirectModeWarningResponse(url: String): WebResourceResponse {
        return WebResourceResponse(
            "text/html",
            "utf-8",
            ByteArrayInputStream(getDirectModeWarningHtml(url).toByteArray())
        ).apply {
            setStatusCodeAndReasonPhrase(403, "Forbidden (Direct mode)")
        }
    }

    private fun getBlockedHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        margin: 0;
                        background: #f5f5f5;
                        color: #333;
                    }
                    .container {
                        text-align: center;
                        max-width: 400px;
                        padding: 40px;
                        background: white;
                        border-radius: 16px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.1);
                    }
                    .icon { font-size: 48px; margin-bottom: 20px; }
                    h1 { margin: 0 0 10px 0; font-size: 20px; color: #1a1a1a; }
                    p { margin: 0 0 20px 0; color: #666; line-height: 1.6; }
                    .status {
                        display: inline-block;
                        padding: 4px 12px;
                        background: #ffebee;
                        color: #c62828;
                        border-radius: 4px;
                        font-size: 12px;
                        margin-bottom: 16px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="status">代理未就绪</div>
                    <h1>无法访问网络</h1>
                    <p>请先在设置中启动代理服务并配置节点。严格模式下不允许直连访问。</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
    private fun getDirectModeWarningHtml(url: String): String {
        val escapedUrl = url.htmlEncode()
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        margin: 0;
                        background: #f5f5f5;
                        color: #333;
                        padding: 20px;
                    }
                    .container {
                        text-align: center;
                        max-width: 500px;
                        padding: 40px;
                        background: white;
                        border-radius: 16px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.1);
                    }
                    h1 { margin: 0 0 10px 0; font-size: 20px; color: #1a1a1a; }
                    p { margin: 0 0 20px 0; color: #666; line-height: 1.6; font-size: 14px; }
                    .status {
                        display: inline-block;
                        padding: 4px 12px;
                        background: #fff3e0;
                        color: #e65100;
                        border-radius: 4px;
                        font-size: 12px;
                        margin-bottom: 16px;
                    }
                    .url {
                        background: #f5f5f5;
                        padding: 12px;
                        border-radius: 8px;
                        word-break: break-all;
                        font-size: 12px;
                        color: #666;
                        margin: 16px 0;
                    }
                    .warning {
                        background: #ffebee;
                        padding: 12px;
                        border-radius: 8px;
                        color: #c62828;
                        font-size: 13px;
                        margin-top: 16px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="status">直连模式警告</div>
                    <h1>隐私风险提示</h1>
                    <p>您当前使用的是<strong>直连模式</strong>，访问以下网站可能会暴露您的真实 IP 地址：</p>
                    <div class="url">$escapedUrl</div>
                    <div class="warning">
                        <strong>安全提示</strong><br>
                        建议：在节点列表中选择一个代理节点，而不是使用直连模式。
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
