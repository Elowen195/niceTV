package com.elowen.niceTV.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MediaProbe {
    private const val RANGE_BYTES = 262143

    suspend fun isMp4FastStart(url: String): Boolean? = withContext(Dispatchers.IO) {
        val lowerUrl = url.lowercase()
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return@withContext null
        }
        return@withContext try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Range", "bytes=0-$RANGE_BYTES")
                .build()
            HttpClient.playerClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body
                val bytes = body.bytes()
                hasMoovBeforeMdat(bytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasMoovBeforeMdat(bytes: ByteArray): Boolean {
        var offset = 0
        while (offset + 8 <= bytes.size) {
            val size = readUint32(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            if (type == "moov") return true
            if (type == "mdat") return false
            val boxSize = when (size) {
                0L -> (bytes.size - offset).toLong()
                1L -> {
                    if (offset + 16 > bytes.size) return false
                    readUint64(bytes, offset + 8)
                }
                else -> size
            }
            if (boxSize < 8) return false
            if (boxSize > Int.MAX_VALUE) return false
            val next = offset + boxSize.toInt()
            if (next <= offset || next > bytes.size) return false
            offset = next
        }
        return false
    }

    private fun readUint32(bytes: ByteArray, offset: Int): Long {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL
    }

    private fun readUint64(bytes: ByteArray, offset: Int): Long {
        return ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long
    }
}
