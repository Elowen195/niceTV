package com.elowen.niceTV.core

import android.content.Context
import android.util.Log
import com.elowen.niceTV.data.models.*
import com.elowen.niceTV.utils.ConfigDebugger
import com.elowen.niceTV.core.platform.proxy.ProxyConstants
import com.elowen.niceTV.core.platform.proxy.ProxyCredentials
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object BoxManager {
    private const val TAG = "NiceTV-Core"
    private var boxService: BoxService? = null
    var isRunning: Boolean = false
        private set
    private val opMutex = Mutex()

    data class ProxyUser(
        val username: String,
        val password: String
    )

    data class Inbound(
        val type: String = "mixed",
        val tag: String = "mixed-in",
        val listen: String = ProxyConstants.PROXY_HOST,
        val listen_port: Int = ProxyConstants.PROXY_PORT,
        val sniff: Boolean = false,
        val set_system_proxy: Boolean = false,
        val users: List<ProxyUser>? = null
    )
    private fun generateProxyCredentials(): Pair<String, String> {
        val username = "proxy_${System.currentTimeMillis()}"
        val password = UUID.randomUUID().toString().replace("-", "")
        return username to password
    }

    suspend fun start(context: Context, node: Node? = null, listenPort: Int = ProxyConstants.PROXY_PORT) {
        opMutex.withLock {
            startInternal(context, node, listenPort)
        }
    }

    suspend fun stop() {
        opMutex.withLock {
            stopInternal()
        }
    }

    suspend fun restart(context: Context, node: Node? = null, listenPort: Int = ProxyConstants.PROXY_PORT) {
        opMutex.withLock {
            stopInternal()
            startInternal(context, node, listenPort)
        }
    }

    private suspend fun startInternal(context: Context, node: Node?, listenPort: Int) = withContext(Dispatchers.IO) {
        try {
            if (boxService != null) {
                if (isRunning) {
                    return@withContext
                }
                runCatching { boxService?.close() }
                boxService = null
            }

            val (username, password) = generateProxyCredentials()
            ProxyCredentials.set(username, password)

            val inbound = Inbound(
                listen_port = listenPort,
                users = listOf(ProxyUser(username, password))
            )
            val configJson = generateConfig(inbound, node, context)

            if (node != null) {
                ConfigDebugger.logNodeDetails(node)
            }
            if (!ConfigDebugger.validateConfig(configJson)) {
                throw IllegalStateException("Invalid configuration generated")
            }

            LibboxRuntime.ensureSetup(context)

            boxService = Libbox.newService(configJson, AndroidPlatformInterface(context))
            boxService?.start()
            isRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy core", e)
            isRunning = false
            runCatching { boxService?.close() }
            boxService = null
            ProxyCredentials.clear()
            throw e
        }
    }

    private suspend fun stopInternal() = withContext(Dispatchers.IO) {
        try {
            if (boxService == null && !isRunning) return@withContext
            boxService?.close()
            boxService = null
            isRunning = false
            ProxyCredentials.clear()
        } catch (e: Exception) {
            // Log.e(TAG, "Failed to stop Libbox", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generateConfig(inbound: Inbound, node: Node?, context: Context): String {
        val inboundJson = JSONObject().apply {
            put("type", inbound.type)
            put("tag", inbound.tag)
            put("listen", inbound.listen)
            put("listen_port", inbound.listen_port)
            put("sniff", inbound.sniff)
            put("set_system_proxy", inbound.set_system_proxy)
            inbound.users?.let { users ->
                if (users.isNotEmpty()) {
                    val usersArray = JSONArray()
                    users.forEach { user ->
                        usersArray.put(JSONObject().apply {
                            put("username", user.username)
                            put("password", user.password)
                        })
                    }
                    put("users", usersArray)
                }
            }
        }

        val logJson = JSONObject().apply {
            put("level", "info")
            put("disabled", false)
        }

        val outbounds = JSONArray()
        val protocol = node?.protocol
        val hasProxy = protocol == Protocol.VLESS || protocol == Protocol.HY2
        val proxyTag = when (protocol) {
            Protocol.VLESS -> "vless-out"
            Protocol.HY2 -> "hy2-out"
            else -> "direct-out"
        }
        when (val proxyNode = node) {
            null -> Unit
            else -> when (proxyNode.protocol) {
                Protocol.VLESS -> outbounds.put(generateVlessOutbound(proxyNode))
                Protocol.HY2 -> outbounds.put(generateHy2Outbound(proxyNode))
                Protocol.DIRECT -> Unit
            }
        }
        outbounds.put(generateDirectOutbound())
        outbounds.put(generateBlockOutbound())
        val experimentalJson = JSONObject().apply {
            val cacheFile = JSONObject().apply {
                put("enabled", false)
            }
            put("cache_file", cacheFile)
        }

        val dnsRules = JSONArray()
        val dnsServers = JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "dns-direct")
                put("address", "223.5.5.5")
                put("detour", "direct-out")
            })
            if (hasProxy) {
                put(JSONObject().apply {
                    put("tag", "dns-remote")
                    put("address", "https://8.8.8.8/dns-query")
                    put("detour", proxyTag)
                })
            }
        }
        val proxyAddress = node?.address
        if (hasProxy && proxyAddress != null && !isIpAddress(proxyAddress)) {
            dnsRules.put(JSONObject().apply {
                put("domain", JSONArray().put(proxyAddress))
                put("server", "dns-direct")
            })
        }
        val dnsJson = JSONObject().apply {
            put("servers", dnsServers)
            put("strategy", "prefer_ipv4")
            put("final", if (hasProxy) "dns-remote" else "dns-direct")
            if (dnsRules.length() > 0) {
                put("rules", dnsRules)
            }
        }

        // Default to blocking IPv6 for the local proxy route.
        val blockIpv6 = true
        val routeRules = JSONArray().apply {
            put(JSONObject().apply {
                put("domain", JSONArray().put("localhost"))
                put("outbound", "direct-out")
            })
            put(JSONObject().apply {
                put("ip_cidr", JSONArray().apply {
                    put("127.0.0.0/8")
                    put("10.0.0.0/8")
                    put("172.16.0.0/12")
                    put("192.168.0.0/16")
                    put("169.254.0.0/16")
                    put("100.64.0.0/10")
                })
                put("outbound", "direct-out")
            })
            if (blockIpv6) {
                put(JSONObject().apply {
                    put("ip_cidr", JSONArray().put("::/0"))
                    put("outbound", "block-out")
                })
            }
            put(JSONObject().apply {
                put("network", "udp")
                put("port", 443)
                put("outbound", "block-out")
            })
        }
        val routeJson = JSONObject().apply {
            put("final", if (hasProxy) proxyTag else "direct-out")
            put("rules", routeRules)
        }

        val config = JSONObject().apply {
            put("log", logJson)
            put("inbounds", JSONArray().put(inboundJson))
            put("outbounds", outbounds)
            put("dns", dnsJson)
            put("route", routeJson)
            put("experimental", experimentalJson)
        }

        return config.toString()
    }

    private fun generateDirectOutbound(): JSONObject {
        return JSONObject().apply {
            put("type", "direct")
            put("tag", "direct-out")
        }
    }

    private fun generateBlockOutbound(): JSONObject {
        return JSONObject().apply {
            put("type", "block")
            put("tag", "block-out")
        }
    }
    private fun generateVlessOutbound(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "vless")
            put("tag", "vless-out")
            put("server", node.address)
            put("server_port", node.port)
            put("uuid", node.uuid)

            node.flow?.let { put("flow", it) }

            if (node.transport != Transport.TCP || node.transportSettings != null) {
                val transportJson = JSONObject()
                when (node.transport) {
                    Transport.WS -> {
                        transportJson.put("type", "ws")
                        node.transportSettings?.let { settings ->
                            settings.path?.let { transportJson.put("path", it) }
                            settings.headers?.let { headers ->
                                val headersJson = JSONObject()
                                headers.forEach { (k, v) -> headersJson.put(k, v) }
                                transportJson.put("headers", headersJson)
                            }
                        }
                    }
                    Transport.GRPC -> {
                        transportJson.put("type", "grpc")
                        node.transportSettings?.serviceName?.let {
                            transportJson.put("service_name", it)
                        }
                    }
                    Transport.HTTP -> {
                        transportJson.put("type", "http")
                        node.transportSettings?.let { settings ->
                            settings.path?.let { transportJson.put("path", it) }
                            settings.host?.let {
                                val hostArray = JSONArray()
                                hostArray.put(it)
                                transportJson.put("host", hostArray)
                            }
                        }
                    }
                    Transport.TCP -> { }
                }
                if (transportJson.length() > 0) {
                    put("transport", transportJson)
                }
            }
            if (node.security != Security.NONE) {
                val tlsJson = JSONObject().apply {
                    put("enabled", true)
                    node.securitySettings?.let { sec ->
                        val serverName = sec.sni ?: if (!isIpAddress(node.address)) node.address else null
                        serverName?.let { put("server_name", it) }
                        put("insecure", sec.allowInsecure)
                        sec.alpn?.let { alpnList ->
                            if (alpnList.isNotEmpty()) {
                                put("alpn", JSONArray(alpnList))
                            }
                        }
                        sec.fingerprint?.let { fp ->
                            val utlsJson = JSONObject()
                            utlsJson.put("enabled", true)
                            utlsJson.put("fingerprint", fp)
                            put("utls", utlsJson)
                        }
                        if (node.security == Security.REALITY) {
                            sec.publicKey?.let {
                                val realityJson = JSONObject()
                                realityJson.put("enabled", true)
                                realityJson.put("public_key", it)
                                sec.shortId?.let { sid -> realityJson.put("short_id", sid) }
                                put("reality", realityJson)
                            }
                        }
                    }
                }
                put("tls", tlsJson)
            }
        }
    }

    private fun generateHy2Outbound(node: Node): JSONObject {
        val password = node.password ?: ""
        if (password.isBlank()) {
            throw IllegalArgumentException("HY2 node is missing password")
        }
        return JSONObject().apply {
            put("type", "hysteria2")
            put("tag", "hy2-out")
            put("server", node.address)
            put("server_port", node.port)
            put("password", password)
            node.obfsPassword?.takeIf { it.isNotBlank() }?.let { obfsPassword ->
                put("obfs", JSONObject().apply {
                    put("type", "salamander")
                    put("password", obfsPassword)
                })
            }
            val tlsJson = JSONObject().apply {
                put("enabled", true)
                val sec = node.securitySettings
                put("insecure", sec?.allowInsecure == true)
                val serverName = sec?.sni ?: if (!isIpAddress(node.address)) node.address else null
                serverName?.let { put("server_name", it) }
                val alpnList = sec?.alpn?.filter { it.isNotBlank() }
                if (!alpnList.isNullOrEmpty()) {
                    put("alpn", JSONArray(alpnList))
                } else {
                    put("alpn", JSONArray().put("h3"))
                }
            }
            put("tls", tlsJson)
        }
    }

    private fun isIpAddress(address: String): Boolean {
        val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        if (ipv4Regex.matches(address)) {
            return true
        }
        return address.contains(":")
    }

    private class AndroidPlatformInterface(private val context: Context) : PlatformInterface {
        override fun autoDetectInterfaceControl(fd: Int) { }
        override fun openTun(options: io.nekohasekai.libbox.TunOptions?): Int {
            throw UnsupportedOperationException("Tun is not supported in this mode")
        }
        override fun readWIFIState(): io.nekohasekai.libbox.WIFIState? = null
        override fun clearDNSCache() { }
        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) { }
        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) { }
        override fun findConnectionOwner(ipProtocol: Int, srcAddress: String?, srcPort: Int, destAddress: String?, destPort: Int): Int = -1
        override fun getInterfaces(): NetworkInterfaceIterator? = null
        override fun includeAllNetworks(): Boolean = false
        override fun localDNSTransport(): LocalDNSTransport? = null
        override fun sendNotification(notification: Notification) { }
        override fun packageNameByUid(uid: Int): String? = ""
        override fun systemCertificates(): StringIterator? = null
        override fun uidByPackageName(packageName: String?): Int = -1
        override fun underNetworkExtension(): Boolean = false
        override fun usePlatformAutoDetectInterfaceControl(): Boolean = false
        override fun useProcFS(): Boolean = false
        override fun writeLog(message: String?) { }
    }
}
