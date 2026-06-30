package com.elowen.niceTV.data.network

import com.elowen.niceTV.core.platform.proxy.ProxyCredentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

object HttpClient {
    private var cookieManager: CookieManager? = null
    private val sharedConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    fun init(manager: CookieManager) {
        cookieManager = manager
    }

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val manager = cookieManager ?: return@Interceptor chain.proceed(original)

        val builder = original.newBuilder()
            .header("User-Agent", manager.getUserAgent())
            .apply {
                if (original.header("Referer") == null) {
                    header("Referer", "https://supjav.com/")
                }
            }
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")

        val host = original.url.host
        if (isSiteHost(host)) {
            val asgfp2 = manager.getAsgfp2()
            val cfClearance = manager.getCfClearance()
            val cookieHeader = buildList {
                if (asgfp2.isNotBlank()) add("asgfp2=$asgfp2")
                if (cfClearance.isNotBlank()) add("cf_clearance=$cfClearance")
            }.joinToString("; ")
            if (cookieHeader.isNotBlank()) {
                builder.header("Cookie", cookieHeader)
            }
        }

        val response = chain.proceed(builder.build())
        
        // [Global 403 Interceptor]
        if (response.code == 403 && isSiteHost(host)) {
            // Emit event to main thread listener (MainActivity)
            _cookieExpiredFlow.tryEmit(Unit)
        }
        
        response
    }
    
    // Event bus for 403 errors
    private val _cookieExpiredFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        replay = 0, 
        extraBufferCapacity = 1, 
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val cookieExpiredFlow: kotlinx.coroutines.flow.SharedFlow<Unit> = _cookieExpiredFlow

    private data class ClientSet(
        val client: OkHttpClient,
        val plainClient: OkHttpClient,
        val playerClient: OkHttpClient
    )

    @Volatile
    private var clientSet: ClientSet = buildClientSet()

    val client: OkHttpClient
        get() = clientSet.client

    val plainClient: OkHttpClient
        get() = clientSet.plainClient

    val playerClient: OkHttpClient
        get() = clientSet.playerClient

    fun configureSocks5Proxy(host: String, port: Int) {
        // Set Java-level SOCKS5 authenticator (required for SOCKS auth)
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (requestingHost == host || requestingHost == "localhost" || requestingHost == "127.0.0.1") {
                    val creds = ProxyCredentials.get()
                    if (creds != null) {
                        return PasswordAuthentication(creds.first, creds.second.toCharArray())
                    }
                }
                return null
            }
        })
        rebuildClients(host, port)
    }

    fun clearProxy() {
        Authenticator.setDefault(null)
        rebuildClients()
    }

    private fun rebuildClients(proxyHost: String? = null, proxyPort: Int? = null) {
        clientSet = buildClientSet(proxyHost, proxyPort)
    }

    private fun isSiteHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "supjav.com" ||
            normalized.endsWith(".supjav.com") ||
            normalized == "supremejav.com" ||
            normalized.endsWith(".supremejav.com")
    }

    private fun buildClientSet(proxyHost: String? = null, proxyPort: Int? = null): ClientSet {
        val builder = OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .connectionPool(sharedConnectionPool)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)

        if (proxyHost != null && proxyPort != null) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort)))
        }

        val baseClient = builder.build()

        val plainClient = baseClient.newBuilder()
            .followRedirects(false)
            .build()

        val playerClient = baseClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()

        return ClientSet(
            client = baseClient,
            plainClient = plainClient,
            playerClient = playerClient
        )
    }
}
