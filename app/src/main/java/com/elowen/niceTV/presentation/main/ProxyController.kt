package com.elowen.niceTV.presentation.main

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.elowen.niceTV.core.BoxManager
import com.elowen.niceTV.data.storage.NodeStorage
import com.elowen.niceTV.service.ProxyService

data class ProxyStatus(
    val isConnected: Boolean,
    val nodeName: String,
)

class ProxyController(
    context: Context,
) {
    private val context = context.applicationContext

    companion object {
        private const val TAG = "ProxyController"
    }

    fun start(): Boolean {
        return try {
            val intent = Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_START)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy service", e)
            false
        }
    }

    fun restart(): Boolean {
        return try {
            val intent = Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_RESTART)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart proxy service", e)
            false
        }
    }

    fun stop(): Boolean {
        return try {
            val intent = Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_STOP)
            context.startService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop proxy service", e)
            false
        }
    }

    fun status(): ProxyStatus {
        val storage = NodeStorage(context.applicationContext)
        val activeNode = storage.getActiveNode()
        val isConnected = BoxManager.isRunning && activeNode != null
        return ProxyStatus(isConnected = isConnected, nodeName = activeNode?.name.orEmpty())
    }
}
