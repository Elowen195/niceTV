package com.elowen.niceTV.core.platform.proxy

import android.content.Context
import android.content.SharedPreferences
import com.elowen.niceTV.utils.SecurePrefs
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.random.Random

object ProxyRuntimeConfig {
    private const val PREFS_NAME_SECURE = "proxy_runtime_secure_v2"
    private const val KEY_PORT = "proxy_port"
    private const val KEY_AUTO_START = "proxy_auto_start"
    private const val RANDOM_MIN_PORT = 20000
    private const val RANDOM_MAX_PORT = 60000
    private const val RANDOM_TRIES = 20

    @Volatile
    private var cachedPort: Int? = null
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        if (cachedPort != null) return
        cachedPort = loadPort(context)
    }

    fun getPort(context: Context): Int {
        return cachedPort ?: loadPort(context).also { cachedPort = it }
    }

    fun getPort(): Int {
        return cachedPort ?: ProxyConstants.PROXY_PORT
    }

    fun setPort(context: Context, port: Int) {
        cachedPort = port
        val prefs = getPrefs(context)
        SecurePrefs.putEncryptedInt(prefs, KEY_PORT, port)
    }

    fun isAutoStartEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        return SecurePrefs.getEncryptedInt(prefs, KEY_AUTO_START, 0) == 1
    }

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        SecurePrefs.putEncryptedInt(prefs, KEY_AUTO_START, if (enabled) 1 else 0)
    }

    fun ensureAvailablePort(context: Context, preferredPort: Int? = null): Int {
        val host = ProxyConstants.PROXY_HOST
        val current = preferredPort ?: getPort(context)
        if (isPortAvailable(host, current)) {
            setPort(context, current)
            return current
        }
        val selected = findRandomAvailablePort(host)
        setPort(context, selected)
        return selected
    }

    private fun loadPort(context: Context): Int {
        val prefs = getPrefs(context)
        return if (SecurePrefs.contains(prefs, KEY_PORT)) {
            SecurePrefs.getEncryptedInt(prefs, KEY_PORT, ProxyConstants.PROXY_PORT)
        } else {
            ProxyConstants.PROXY_PORT
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val cached = cachedPrefs
        if (cached != null) return cached
        synchronized(this) {
            val again = cachedPrefs
            if (again != null) return again
            val securePrefs = createEncryptedPrefs(context.applicationContext)
            cachedPrefs = securePrefs
            return securePrefs
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME_SECURE, Context.MODE_PRIVATE)
    }

    private fun isPortAvailable(host: String, port: Int): Boolean {
        return try {
            ServerSocket().use { socket ->
                socket.bind(InetSocketAddress(InetAddress.getByName(host), port))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findRandomAvailablePort(host: String): Int {
        repeat(RANDOM_TRIES) {
            val port = Random.nextInt(RANDOM_MIN_PORT, RANDOM_MAX_PORT)
            if (isPortAvailable(host, port)) return port
        }
        return reserveEphemeralPort(host)
    }

    private fun reserveEphemeralPort(host: String): Int {
        return try {
            ServerSocket(0, 0, InetAddress.getByName(host)).use { socket ->
                socket.localPort
            }
        } catch (_: Exception) {
            ProxyConstants.PROXY_PORT
        }
    }
}
