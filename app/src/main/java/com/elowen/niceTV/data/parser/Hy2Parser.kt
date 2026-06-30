package com.elowen.niceTV.data.parser

import com.elowen.niceTV.data.models.Node
import com.elowen.niceTV.data.models.Protocol
import com.elowen.niceTV.data.models.Security
import com.elowen.niceTV.data.models.SecuritySettings
import com.elowen.niceTV.data.models.Transport
import java.net.URLDecoder
import java.util.UUID

object Hy2Parser {

    fun parseHy2Url(url: String): Node? {
        try {
            if (!url.startsWith("hy2://", ignoreCase = true) && 
                !url.startsWith("hysteria2://", ignoreCase = true)) {
                return null
            }

            val content = url.substringAfter("://")
            val parts = content.split("#", limit = 2)
            val mainPart = parts[0]
            val remarks = if (parts.size > 1) {
                try { URLDecoder.decode(parts[1], "UTF-8") } catch (e: Exception) { parts[1] }
            } else { "HY2 Node" }

            val urlParts = mainPart.split("?", limit = 2)
            var addressPart = urlParts[0]
            if (addressPart.endsWith("/")) {
                addressPart = addressPart.substring(0, addressPart.length - 1)
            }

            val params = if (urlParts.size > 1) parseQueryParams(urlParts[1]) else emptyMap()

            val atIndex = addressPart.lastIndexOf("@")
            val authPart = if (atIndex >= 0) addressPart.substring(0, atIndex) else ""
            val serverPart = if (atIndex >= 0) addressPart.substring(atIndex + 1) else addressPart

            val colonIndex = serverPart.lastIndexOf(":")
            if (colonIndex == -1) return null

            val address = serverPart.substring(0, colonIndex).removeSurrounding("[", "]")
            if (address.isBlank()) return null
            var portStr = serverPart.substring(colonIndex + 1)
            if (portStr.contains(",")) portStr = portStr.split(",")[0]
            if (portStr.contains("-")) portStr = portStr.split("-")[0]
            
            val port = portStr.trim().toIntOrNull()
            if (port == null || port !in 1..65535) return null

            val allowInsecure = parseBool(params["insecure"]) || parseBool(params["allowInsecure"])
            val alpnList = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

            val securitySettings = if (allowInsecure || !params["sni"].isNullOrBlank() || !alpnList.isNullOrEmpty() || !params["fp"].isNullOrBlank()) {
                SecuritySettings(sni = params["sni"], alpn = alpnList, fingerprint = params["fp"], allowInsecure = allowInsecure)
            } else null

            val decodedAuthPart = if (authPart.isNotBlank()) {
                try { URLDecoder.decode(authPart, "UTF-8") } catch (_: Exception) { authPart }
            } else {
                ""
            }
            val password = decodedAuthPart.takeIf { it.isNotBlank() }
                ?: params["password"]
                ?: params["auth"]
                ?: params["auth-str"]
                ?: params["auth_str"]
            if (password.isNullOrBlank()) return null

            return Node(id = stableId(mainPart), name = remarks, protocol = Protocol.HY2, address = address, port = port, uuid = "", password = password, obfsPassword = params["obfs-password"] ?: params["obfsPassword"], transport = Transport.TCP, security = Security.TLS, securitySettings = securitySettings)
        } catch (e: Exception) {
            return null
        }
    }

    private fun stableId(mainPart: String): String {
        return UUID.nameUUIDFromBytes("hy2:$mainPart".toByteArray(Charsets.UTF_8)).toString()
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split("&").mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                try { parts[0] to URLDecoder.decode(parts[1], "UTF-8") } catch (e: Exception) { parts[0] to parts[1] }
            } else null
        }.toMap()
    }

    private fun parseBool(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase()
        return normalized == "1" || normalized == "true"
    }
}
