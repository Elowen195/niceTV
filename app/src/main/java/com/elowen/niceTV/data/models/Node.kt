package com.elowen.niceTV.data.models

import org.json.JSONArray
import org.json.JSONObject

data class Node(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    val uuid: String,
    val password: String? = null,
    val obfsPassword: String? = null,

    // Transport settings
    val transport: Transport = Transport.TCP,
    val transportSettings: TransportSettings? = null,

    // Security settings
    val security: Security = Security.NONE,
    val securitySettings: SecuritySettings? = null,

    // Flow control (for XTLS)
    val flow: String? = null,

    // Metadata
    val latency: Long = -1,  // -1 = not tested, 0 = timeout
    val lastTestTime: Long = 0,
    val isActive: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("protocol", protocol.name)
            put("address", address)
            put("port", port)
            put("uuid", uuid)
            password?.let { put("password", it) }
            obfsPassword?.let { put("obfsPassword", it) }
            put("transport", transport.name)
            transportSettings?.let { put("transportSettings", it.toJson()) }
            put("security", security.name)
            securitySettings?.let { put("securitySettings", it.toJson()) }
            flow?.let { put("flow", it) }
            put("latency", latency)
            put("lastTestTime", lastTestTime)
            put("isActive", isActive)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Node {
            return Node(
                id = json.getString("id"),
                name = json.getString("name"),
                protocol = Protocol.valueOf(json.getString("protocol")),
                address = json.getString("address"),
                port = json.getInt("port"),
                uuid = json.optString("uuid", ""),
                password = if (json.has("password")) json.getString("password") else null,
                obfsPassword = if (json.has("obfsPassword")) json.getString("obfsPassword") else null,
                transport = Transport.valueOf(json.optString("transport", "TCP")),
                transportSettings = if (json.has("transportSettings")) {
                    TransportSettings.fromJson(json.getJSONObject("transportSettings"))
                } else null,
                security = Security.valueOf(json.optString("security", "NONE")),
                securitySettings = if (json.has("securitySettings")) {
                    SecuritySettings.fromJson(json.getJSONObject("securitySettings"))
                } else null,
                flow = json.optStringOrNull("flow"),
                latency = json.optLong("latency", -1),
                lastTestTime = json.optLong("lastTestTime", 0),
                isActive = json.optBoolean("isActive", false)
            )
        }
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    optString(name).takeIf { it.isNotBlank() }

enum class Protocol {
    VLESS,
    HY2,
    DIRECT
}

enum class Transport {
    TCP,
    WS,
    GRPC,
    HTTP
}

data class TransportSettings(
    val path: String? = null,
    val host: String? = null,
    val headers: Map<String, String>? = null,
    val serviceName: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            path?.let { put("path", it) }
            host?.let { put("host", it) }
            headers?.let { h ->
                val headersJson = JSONObject()
                h.forEach { (k, v) -> headersJson.put(k, v) }
                put("headers", headersJson)
            }
            serviceName?.let { put("serviceName", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TransportSettings {
            val headers = if (json.has("headers")) {
                val headersJson = json.getJSONObject("headers")
                val map = mutableMapOf<String, String>()
                headersJson.keys().forEach { key ->
                    map[key] = headersJson.getString(key)
                }
                map
            } else null

            return TransportSettings(
                path = json.optStringOrNull("path"),
                host = json.optStringOrNull("host"),
                headers = headers,
                serviceName = json.optStringOrNull("serviceName")
            )
        }
    }
}

enum class Security {
    NONE,
    TLS,
    REALITY
}

data class SecuritySettings(
    val sni: String? = null,
    val alpn: List<String>? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false,
    val publicKey: String? = null,
    val shortId: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            sni?.let { put("sni", it) }
            alpn?.let { put("alpn", JSONArray(it)) }
            fingerprint?.let { put("fingerprint", it) }
            put("allowInsecure", allowInsecure)
            publicKey?.let { put("publicKey", it) }
            shortId?.let { put("shortId", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SecuritySettings {
            val alpn = if (json.has("alpn")) {
                val alpnArray = json.getJSONArray("alpn")
                List(alpnArray.length()) { alpnArray.getString(it) }
            } else null

            return SecuritySettings(
                sni = json.optStringOrNull("sni"),
                alpn = alpn,
                fingerprint = json.optStringOrNull("fingerprint"),
                allowInsecure = json.optBoolean("allowInsecure", false),
                publicKey = json.optStringOrNull("publicKey"),
                shortId = json.optStringOrNull("shortId")
            )
        }
    }
}
