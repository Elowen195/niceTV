package com.elowen.niceTV.core.platform.proxy

import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ProxyHttpClientFactory {

    private class SocksAuthenticator : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (requestingHost == ProxyConstants.PROXY_HOST ||
                requestingHost == "localhost" ||
                requestingHost == "127.0.0.1") {

                val credentials = ProxyCredentials.get()
                if (credentials != null) {
                    val (username, password) = credentials
                    return PasswordAuthentication(username, password.toCharArray())
                }
            }
            return null
        }
    }

    fun createSocksClient(
        connectTimeoutSeconds: Long = 3,
        readTimeoutSeconds: Long = 3,
        writeTimeoutSeconds: Long = 3,
    ): OkHttpClient {
        val proxyPort = ProxyRuntimeConfig.getPort()
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(ProxyConstants.PROXY_HOST, proxyPort))

        Authenticator.setDefault(SocksAuthenticator())

        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }
}
