package com.elowen.niceTV.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import com.elowen.niceTV.utils.SecurePrefs

class CookieManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("api_cookies", Context.MODE_PRIVATE)
    fun saveCookies(asgfp2: String, cfClearance: String) {
        SecurePrefs.putEncryptedString(prefs, "asgfp2", asgfp2)
        SecurePrefs.putEncryptedString(prefs, "cf_clearance", cfClearance)
    }
    fun saveUserAgent(ua: String) {
        SecurePrefs.putEncryptedString(prefs, "user_agent", ua)
    }
    fun getAsgfp2() = SecurePrefs.getEncryptedString(prefs, "asgfp2", "").orEmpty()
    fun getCfClearance() = SecurePrefs.getEncryptedString(prefs, "cf_clearance", "").orEmpty()
    fun hasAccessCookies() = getAsgfp2().isNotBlank() || getCfClearance().isNotBlank()
    fun getUserAgent() =
        SecurePrefs.getEncryptedString(prefs, "user_agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36").orEmpty()
    // [NEW] Global Referer store for Media3 playback
    companion object {
        private val refererMap = java.util.concurrent.ConcurrentHashMap<String, String>()
        
        fun setReferer(url: String, referer: String) {
            if (url.isBlank() || referer.isBlank()) return
            refererMap[url] = referer
            buildBaseUrlPrefix(url)?.let { refererMap[it] = referer }
        }
        
        fun getReferer(url: String): String? {
            refererMap[url]?.let { return it }
            val normalizedUrl = url.substringBefore('?')
            return refererMap.entries
                .asSequence()
                .filter { (key, _) -> key.endsWith("/") && normalizedUrl.startsWith(key) }
                .maxByOrNull { (key, _) -> key.length }
                ?.value
        }

        private fun buildBaseUrlPrefix(url: String): String? {
            val cleaned = url.substringBefore('?')
            val uri = cleaned.toUri()
            val scheme = uri.scheme ?: return null
            val authority = uri.encodedAuthority ?: return null
            val path = uri.encodedPath ?: return "$scheme://$authority/"
            val basePath = if (path.endsWith("/")) path else path.substringBeforeLast('/', path)
            val normalizedPath = if (basePath.endsWith("/")) basePath else "$basePath/"
            return "$scheme://$authority$normalizedPath"
        }
    }
}
