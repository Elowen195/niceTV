package com.elowen.niceTV.utils

import com.elowen.niceTV.data.models.Node
import org.json.JSONObject

object ConfigDebugger {
    fun logNodeDetails(node: Node) {
        // No-op in production
    }

    fun validateConfig(configJson: String): Boolean {
        return try {
            val config = JSONObject(configJson)
            if (!config.has("inbounds")) return false
            if (!config.has("outbounds")) return false
            val outbounds = config.getJSONArray("outbounds")
            if (outbounds.length() == 0) return false
            val outbound = (0 until outbounds.length())
                .map { outbounds.getJSONObject(it) }
                .firstOrNull { it.optString("type") != "direct" }
                ?: outbounds.getJSONObject(0)
            if (!outbound.has("type")) return false
            val type = outbound.getString("type")
            if (type == "vless") {
                val requiredFields = listOf("server", "server_port", "uuid")
                for (field in requiredFields) {
                    if (!outbound.has(field)) return false
                }
            }
            if (type == "hysteria2") {
                val requiredFields = listOf("server", "server_port", "password")
                for (field in requiredFields) {
                    if (!outbound.has(field)) return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
