package com.elowen.niceTV.utils

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.elowen.niceTV.data.network.HttpClient
import okhttp3.Call
import okhttp3.Request
import java.io.InputStream

object GlideOkHttpIntegration {
    fun register(context: Context) {
        val glide = Glide.get(context)
        val dynamicCallFactory = Call.Factory { request: Request -> HttpClient.client.newCall(request) }
        val factory = OkHttpUrlLoader.Factory(dynamicCallFactory)
        glide.registry.replace(GlideUrl::class.java, InputStream::class.java, factory)
    }
}
