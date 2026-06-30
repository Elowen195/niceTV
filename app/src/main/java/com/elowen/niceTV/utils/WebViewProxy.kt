package com.elowen.niceTV.utils

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.elowen.niceTV.core.NetworkRestrictionManager
import java.util.concurrent.Executor

object WebViewProxy {
    private val directExecutor = Executor { runnable -> runnable.run() }

    fun setProxy(context: Context, host: String, port: Int, scheme: String = "http") {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            NetworkRestrictionManager.updateProxyOverrideApplied(false)
            val proxyRule = if (scheme.isNotBlank()) "$scheme://$host:$port" else "$host:$port"
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule(proxyRule)
                .addBypassRule("localhost")
                .addBypassRule("127.0.0.1")
                .build()

            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                directExecutor
            ) {
                NetworkRestrictionManager.updateProxyOverrideApplied(true)
            }
        } else {
            NetworkRestrictionManager.updateProxyOverrideApplied(false)
        }
    }

    fun clearProxy() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(
                directExecutor
            ) {
                NetworkRestrictionManager.updateProxyOverrideApplied(false)
            }
        } else {
            NetworkRestrictionManager.updateProxyOverrideApplied(false)
        }
    }
}
