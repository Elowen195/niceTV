package com.elowen.niceTV.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elowen.niceTV.core.BoxManager
import com.elowen.niceTV.core.NetworkRestrictionManager
import com.elowen.niceTV.core.platform.proxy.ProxyConstants
import com.elowen.niceTV.core.platform.proxy.ProxyRuntimeConfig
import com.elowen.niceTV.utils.WebViewProxy
import com.elowen.niceTV.data.network.HttpClient
import com.elowen.niceTV.data.storage.NodeStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

class ProxyService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var healthJob: kotlinx.coroutines.Job? = null
    private var foregroundStarted = false

    companion object {
        private const val TAG = "NiceTV-Service"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "nicetv_proxy_channel"
        const val ACTION_START = "com.elowen.niceTV.service.START"
        const val ACTION_RESTART = "com.elowen.niceTV.service.RESTART"
        const val ACTION_STOP = "com.elowen.niceTV.service.STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ProxyRuntimeConfig.setAutoStartEnabled(applicationContext, false)
                serviceScope.launch {
                    stopProxyInternal()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                if (!ensureForeground()) return START_NOT_STICKY
                restartProxy()
            }
            else -> {
                if (!ensureForeground()) return START_NOT_STICKY
                startProxy()
            }
        }
        return START_STICKY
    }

    private fun ensureForeground(): Boolean {
        if (foregroundStarted) return true
        return try {
            startForeground(NOTIFICATION_ID, createNotification())
            foregroundStarted = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground proxy service", e)
            serviceScope.launch { stopProxyInternal() }
            stopSelf()
            false
        }
    }

    private fun startProxy() {
        serviceScope.launch {
            try {
                val storage = NodeStorage(applicationContext)
                val activeNode = storage.getActiveNode()

                NetworkRestrictionManager.initialize(applicationContext)
                if (activeNode == null) {
                    ProxyRuntimeConfig.setAutoStartEnabled(applicationContext, false)
                    updateNotification("代理服务未启动：未选择节点")
                    stopProxyInternal()
                    stopSelf()
                    return@launch
                }

                if (BoxManager.isRunning) {
                    NetworkRestrictionManager.updateRunningState(true)
                    val proxyPort = ProxyRuntimeConfig.getPort(applicationContext)
                    WebViewProxy.setProxy(applicationContext, ProxyConstants.PROXY_HOST, proxyPort)
                    HttpClient.configureSocks5Proxy(ProxyConstants.PROXY_HOST, proxyPort)
                    startHealthMonitor(proxyPort)
                    val nodeName = activeNode.name
                    ProxyRuntimeConfig.setAutoStartEnabled(applicationContext, true)
                    updateNotification("代理服务正在运行 - $nodeName")
                    return@launch
                }

                val proxyPort = ProxyRuntimeConfig.ensureAvailablePort(applicationContext)
                WebViewProxy.setProxy(applicationContext, ProxyConstants.PROXY_HOST, proxyPort)
                BoxManager.start(applicationContext, activeNode, proxyPort)
                HttpClient.configureSocks5Proxy(ProxyConstants.PROXY_HOST, proxyPort)
                NetworkRestrictionManager.updateRunningState(true)
                startHealthMonitor(proxyPort)

                val nodeName = activeNode.name
                ProxyRuntimeConfig.setAutoStartEnabled(applicationContext, true)
                updateNotification("代理服务正在运行 - $nodeName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start proxy service", e)
                stopProxyInternal()
                stopSelf()
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun restartProxy() {
        serviceScope.launch {
            try {
                val storage = NodeStorage(applicationContext)
                val activeNode = storage.getActiveNode()

                NetworkRestrictionManager.initialize(applicationContext)
                if (activeNode == null) {
                    ProxyRuntimeConfig.setAutoStartEnabled(applicationContext, false)
                    updateNotification("代理服务未启动：未选择节点")
                    stopProxyInternal()
                    stopSelf()
                    return@launch
                }
                val proxyPort = if (BoxManager.isRunning) {
                    ProxyRuntimeConfig.getPort(applicationContext)
                } else {
                    ProxyRuntimeConfig.ensureAvailablePort(applicationContext)
                }
                WebViewProxy.setProxy(applicationContext, ProxyConstants.PROXY_HOST, proxyPort)
                BoxManager.restart(applicationContext, activeNode, proxyPort)
                HttpClient.configureSocks5Proxy(ProxyConstants.PROXY_HOST, proxyPort)
                NetworkRestrictionManager.updateRunningState(true)
                startHealthMonitor(proxyPort)

                val nodeName = activeNode.name
                ProxyRuntimeConfig.setAutoStartEnabled(applicationContext, true)
                updateNotification("代理服务正在运行 - $nodeName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart proxy service", e)
                stopProxyInternal()
                stopSelf()
            }
        }
    }

    private suspend fun stopProxyInternal() {
        BoxManager.stop()
        WebViewProxy.clearProxy()
        HttpClient.clearProxy()
        NetworkRestrictionManager.updateRunningState(false)
        NetworkRestrictionManager.updateProxyReachable(false)
        healthJob?.cancel()
        healthJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            stopProxyInternal()
            serviceScope.cancel()
        }
    }

    private fun startHealthMonitor(port: Int) {
        healthJob?.cancel()
        healthJob = serviceScope.launch {
            while (isActive) {
                val reachable = isPortReachable(ProxyConstants.PROXY_HOST, port)
                NetworkRestrictionManager.updateProxyReachable(reachable)
                delay(1000)
            }
        }
    }

    private fun isPortReachable(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 200)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun createNotification(contentText: String = "代理服务正在运行"): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "代理服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示本地代理服务的状态"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NiceTV 代理")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
