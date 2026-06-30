package com.elowen.niceTV.data.manager

import java.security.MessageDigest

object CacheKeyUtil {
    fun forUrl(url: String): String {
        return "video_" + sha256Hex(url)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef"
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            hexChars[i++] = hexArray[v ushr 4]
            hexChars[i++] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
