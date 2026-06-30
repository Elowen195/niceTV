package com.elowen.niceTV.data.parser

import android.util.Base64
import android.util.Log
import com.elowen.niceTV.data.models.Node
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.net.URLEncoder

/**
 * 订阅解析器 - 支持 VLESS/HY2 节点的原始文本、Base64、Clash YAML 和应用 JSON 格式。
 */
object SubscriptionParser {
    private const val TAG = "SubscriptionParser"

    fun parseSubscription(content: String): List<Node> {
        if (content.isBlank()) return emptyList()

        return try {
            when {
                isBase64NodeList(content) -> {
                    val nodes = parseBase64Nodes(content)
                    if (nodes.isNotEmpty()) nodes else parseRawNodes(content)
                }
                content.contains("proxies:") -> parseClashYaml(content)
                content.trim().startsWith("{") || content.trim().startsWith("[") -> parseJsonNodes(content)
                content.contains("://") -> parseRawNodes(content)
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse subscription", e)
            emptyList()
        }
    }

    private fun isBase64NodeList(content: String): Boolean {
        val trimmed = content.trim()
        return try {
            val decoded = decodeBase64(trimmed)
            decoded?.contains("://") == true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseBase64Nodes(content: String): List<Node> {
        val decoded = decodeBase64(content.trim()) ?: return emptyList()
        val lines = decoded.split(Regex("[\\r\\n\\s|]+")).filter { it.isNotBlank() }

        return lines.mapNotNull { line ->
            try {
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                val lower = trimmed.lowercase()
                when {
                    lower.startsWith("vless://") -> VlessParser.parseVlessUrl(trimmed)
                    lower.startsWith("hy2://") || lower.startsWith("hysteria2://") -> Hy2Parser.parseHy2Url(trimmed)
                    else -> null
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse base64 node", e)
                null
            }
        }
    }

    private fun parseRawNodes(content: String): List<Node> {
        val lines = content.split(Regex("[\\r\\n\\s|]+")).filter { it.isNotBlank() }
        
        return lines.mapNotNull { line ->
            try {
                var trimmed = line.trim()
                if (trimmed.startsWith("-")) {
                    trimmed = trimmed.substring(1).trim()
                }
                if (trimmed.isBlank()) return@mapNotNull null
                val lower = trimmed.lowercase()
                when {
                    lower.startsWith("vless://") -> VlessParser.parseVlessUrl(trimmed)
                    lower.startsWith("hy2://") || lower.startsWith("hysteria2://") -> Hy2Parser.parseHy2Url(trimmed)
                    else -> null
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse node", e)
                null
            }
        }
    }

    private fun parseClashYaml(content: String): List<Node> {
        val nodes = mutableListOf<Node>()
        val lines = content.lines()

        var inProxies = false
        var currentProxy = mutableMapOf<String, String>()

        fun flushCurrentProxy() {
            if (currentProxy.isNotEmpty()) {
                parseClashProxy(currentProxy)?.let { nodes.add(it) }
                currentProxy = mutableMapOf()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val isTopLevel = line.takeWhile { it == ' ' || it == '\t' }.isEmpty()
            if (isTopLevel) {
                val sectionName = trimmed.substringBefore(":").trim()
                when {
                    sectionName == "proxies" -> {
                        flushCurrentProxy()
                        inProxies = true
                        continue
                    }
                    inProxies -> {
                        flushCurrentProxy()
                        break
                    }
                    else -> continue
                }
            }

            if (!inProxies) continue

            when {
                trimmed.startsWith("-") -> {
                    flushCurrentProxy()
                    val keyValue = trimmed.substring(1).trim()
                    if (keyValue.startsWith("{") && keyValue.endsWith("}")) {
                        currentProxy.putAll(parseInlineYamlMap(keyValue))
                        flushCurrentProxy()
                    } else if (keyValue.contains(":")) {
                        val parts = keyValue.split(":", limit = 2)
                        currentProxy[cleanYamlValue(parts[0])] = cleanYamlValue(parts[1])
                    }
                }
                trimmed.contains(":") -> {
                    val parts = trimmed.split(":", limit = 2)
                    currentProxy[cleanYamlValue(parts[0])] = cleanYamlValue(parts[1])
                }
            }
        }

        if (inProxies) {
            flushCurrentProxy()
        }

        return nodes
    }

    private fun parseClashProxy(proxy: Map<String, String>): Node? {
        val type = proxy["type"]?.lowercase() ?: return null
        val name = proxy["name"] ?: return null
        val server = proxy["server"] ?: return null
        val port = proxy["port"]?.toIntOrNull() ?: return null
        val host = formatHost(server)

        return when (type) {
            "vless" -> {
                val uuid = proxy["uuid"] ?: return null
                val params = buildList {
                    val network = proxy["network"]?.takeIf { it.isNotBlank() } ?: "tcp"
                    add("type=${urlEncode(network)}")
                    val security = when {
                        parseBool(proxy["reality"]) ||
                            proxy.keys.any { it == "reality-opts" || it.startsWith("reality-opts.") } -> "reality"
                        parseBool(proxy["tls"]) -> "tls"
                        else -> null
                    }
                    security?.let { add("security=$it") }
                    firstNonBlank(proxy["servername"], proxy["serverName"], proxy["sni"])?.let {
                        add("sni=${urlEncode(it)}")
                    }
                    proxy["alpn"]?.takeIf { it.isNotBlank() }?.let {
                        add("alpn=${urlEncode(cleanYamlListValue(it))}")
                    }
                    if (parseBool(proxy["skip-cert-verify"]) || parseBool(proxy["insecure"])) {
                        add("allowInsecure=1")
                    }
                    firstNonBlank(proxy["flow"])?.let { add("flow=${urlEncode(it)}") }
                    firstNonBlank(proxy["path"], proxy["ws-path"], proxy["ws-opts.path"])?.let {
                        add("path=${urlEncode(it)}")
                    }
                    firstNonBlank(
                        proxy["Host"],
                        proxy["host"],
                        proxy["ws-opts.host"],
                        proxy["ws-opts.headers.Host"],
                        proxy["ws-opts.headers.host"]
                    )?.let { add("host=${urlEncode(it)}") }
                    firstNonBlank(
                        proxy["serviceName"],
                        proxy["service-name"],
                        proxy["grpc-service-name"],
                        proxy["grpc-opts.serviceName"],
                        proxy["grpc-opts.service-name"],
                        proxy["grpc-opts.grpc-service-name"]
                    )?.let { add("serviceName=${urlEncode(it)}") }
                    firstNonBlank(proxy["fp"], proxy["client-fingerprint"])?.let { add("fp=${urlEncode(it)}") }
                    firstNonBlank(
                        proxy["public-key"],
                        proxy["pbk"],
                        proxy["reality-opts.public-key"],
                        proxy["reality-opts.pbk"]
                    )?.let { add("pbk=${urlEncode(it)}") }
                    firstNonBlank(
                        proxy["short-id"],
                        proxy["sid"],
                        proxy["reality-opts.short-id"],
                        proxy["reality-opts.sid"]
                    )?.let { add("sid=${urlEncode(it)}") }
                }
                val query = params.joinToString("&", prefix = "?")
                VlessParser.parseVlessUrl("vless://$uuid@$host:$port$query#${urlEncode(name)}")
            }
            "hysteria2", "hy2" -> {
                val password = proxy["password"]
                    ?: proxy["auth"]
                    ?: proxy["auth-str"]
                    ?: proxy["auth_str"]
                    ?: return null
                val params = buildList {
                    proxy["sni"]?.takeIf { it.isNotBlank() }?.let { add("sni=${urlEncode(it)}") }
                    proxy["alpn"]?.takeIf { it.isNotBlank() }?.let { add("alpn=${urlEncode(cleanYamlListValue(it))}") }
                    proxy["insecure"]?.takeIf { it.isNotBlank() }?.let { add("insecure=${urlEncode(it)}") }
                    proxy["obfs-password"]?.takeIf { it.isNotBlank() }?.let { add("obfs-password=${urlEncode(it)}") }
                    proxy["obfsPassword"]?.takeIf { it.isNotBlank() }?.let { add("obfsPassword=${urlEncode(it)}") }
                    proxy["fp"]?.takeIf { it.isNotBlank() }?.let { add("fp=${urlEncode(it)}") }
                }
                val query = if (params.isNotEmpty()) params.joinToString("&", prefix = "?") else ""
                Hy2Parser.parseHy2Url("hy2://${urlEncode(password)}@$host:$port$query#${urlEncode(name)}")
            }
            else -> null
        }
    }

    private fun cleanYamlValue(value: String): String {
        return value.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    private fun cleanYamlListValue(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed.removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .joinToString(",") { cleanYamlValue(it) }
        } else {
            trimmed
        }
    }

    private fun parseInlineYamlMap(value: String): Map<String, String> {
        val body = value.trim().removePrefix("{").removeSuffix("}")
        val result = linkedMapOf<String, String>()
        splitInlineYamlFields(body).forEach { field ->
            val separator = field.indexOf(':')
            if (separator > 0) {
                val key = cleanYamlValue(field.substring(0, separator))
                val parsedValue = cleanYamlValue(field.substring(separator + 1))
                result[key] = parsedValue
                if (parsedValue.startsWith("{") && parsedValue.endsWith("}")) {
                    parseInlineYamlMap(parsedValue).forEach { (nestedKey, nestedValue) ->
                        result["$key.$nestedKey"] = nestedValue
                    }
                }
            }
        }
        return result
    }

    private fun splitInlineYamlFields(value: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var bracketDepth = 0
        var braceDepth = 0
        value.forEach { ch ->
            when {
                quote != null -> {
                    current.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '\'' || ch == '"' -> {
                    quote = ch
                    current.append(ch)
                }
                ch == '[' -> {
                    bracketDepth += 1
                    current.append(ch)
                }
                ch == ']' -> {
                    if (bracketDepth > 0) bracketDepth -= 1
                    current.append(ch)
                }
                ch == '{' -> {
                    braceDepth += 1
                    current.append(ch)
                }
                ch == '}' -> {
                    if (braceDepth > 0) braceDepth -= 1
                    current.append(ch)
                }
                ch == ',' && bracketDepth == 0 && braceDepth == 0 -> {
                    current.toString().trim().takeIf { it.isNotEmpty() }?.let(fields::add)
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(fields::add)
        return fields
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun parseBool(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase()
        return normalized == "1" || normalized == "true" || normalized == "yes"
    }

    private fun formatHost(host: String): String {
        return if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun decodeBase64(content: String): String? {
        val trimmed = content.trim()
        return try {
            String(Base64.decode(trimmed, Base64.DEFAULT), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(Base64.decode(trimmed, Base64.URL_SAFE), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseJsonNodes(content: String): List<Node> {
        return runCatching {
            val trimmed = content.trim()
            when {
                trimmed.startsWith("[") -> parseJsonArray(JSONArray(trimmed))
                trimmed.startsWith("{") -> {
                    val json = JSONObject(trimmed)
                    if (json.has("nodes")) {
                        parseJsonArray(json.getJSONArray("nodes"))
                    } else {
                        listOf(Node.fromJson(json))
                    }
                }
                else -> emptyList()
            }
        }.getOrElse { e ->
            Log.d(TAG, "Failed to parse json nodes", e)
            emptyList()
        }
    }

    private fun parseJsonArray(array: JSONArray): List<Node> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching { Node.fromJson(item) }.getOrNull()?.let(::add)
            }
        }
    }
}
