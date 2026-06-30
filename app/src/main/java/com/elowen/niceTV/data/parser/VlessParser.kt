package com.elowen.niceTV.data.parser

import com.elowen.niceTV.data.models.*
import java.net.URLDecoder
import java.util.UUID

object VlessParser {

    fun parseVlessUrl(url: String): Node? {
        try {
            if (!url.startsWith("vless://", ignoreCase = true)) {
                return null
            }

            val content = url.substringAfter("://")
            val parts = content.split("#", limit = 2)
            val mainPart = parts[0]
            val remarks = if (parts.size > 1) {
                try { URLDecoder.decode(parts[1], "UTF-8") } catch (e: Exception) { parts[1] }
            } else { "VLESS Node" }

            val urlParts = mainPart.split("?", limit = 2)
            var addressPart = urlParts[0]
            if (addressPart.endsWith("/")) {
                addressPart = addressPart.substring(0, addressPart.length - 1)
            }

            val params = if (urlParts.size > 1) parseQueryParams(urlParts[1]) else emptyMap()

            val atIndex = addressPart.indexOf("@")
            if (atIndex == -1) return null

            val uuid = addressPart.substring(0, atIndex)
            if (uuid.isBlank() || !isValidUuid(uuid)) return null
            val serverPart = addressPart.substring(atIndex + 1)
            val colonIndex = serverPart.lastIndexOf(":")
            if (colonIndex == -1) return null

            val address = serverPart.substring(0, colonIndex).removeSurrounding("[", "]")
            if (address.isBlank()) return null
            val port = serverPart.substring(colonIndex + 1).toIntOrNull()
            if (port == null || port !in 1..65535) return null

            val transportType = when (params["type"]?.lowercase()) {
                "ws" -> Transport.WS
                "grpc" -> Transport.GRPC
                "http" -> Transport.HTTP
                "tcp", null -> Transport.TCP
                else -> Transport.TCP
            }

            val transportSettings = if (transportType != Transport.TCP || params.containsKey("path") || params.containsKey("host")) {
                val headers = params["host"]?.let { mapOf("Host" to it) }
                TransportSettings(path = params["path"], host = params["host"], headers = headers, serviceName = params["serviceName"])
            } else null

            val securityType = when (params["security"]?.lowercase()) {
                "tls" -> Security.TLS
                "reality" -> Security.REALITY
                "none", null -> Security.NONE
                else -> Security.NONE
            }
            if (securityType == Security.REALITY && params["pbk"].isNullOrBlank()) return null

            val securitySettings = if (securityType != Security.NONE) {
                val alpnList = params["alpn"]?.split(",")?.map { it.trim() }
                SecuritySettings(sni = params["sni"], alpn = alpnList, fingerprint = params["fp"], allowInsecure = params["allowInsecure"] == "1", publicKey = params["pbk"], shortId = params["sid"])
            } else null

            return Node(id = stableId(mainPart), name = remarks, protocol = Protocol.VLESS, address = address, port = port, uuid = uuid, transport = transportType, transportSettings = transportSettings, security = securityType, securitySettings = securitySettings, flow = params["flow"])
        } catch (e: Exception) {
            return null
        }
    }

    private fun stableId(mainPart: String): String {
        return UUID.nameUUIDFromBytes("vless:$mainPart".toByteArray(Charsets.UTF_8)).toString()
    }

    private fun isValidUuid(value: String): Boolean {
        return runCatching { UUID.fromString(value) }.isSuccess
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split("&").mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                try { parts[0] to URLDecoder.decode(parts[1], "UTF-8") } catch (e: Exception) { parts[0] to parts[1] }
            } else null
        }.toMap()
    }
}
