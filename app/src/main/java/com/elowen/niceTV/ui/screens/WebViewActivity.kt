package com.elowen.niceTV.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.elowen.niceTV.R
import com.elowen.niceTV.core.platform.proxy.ProxyConstants
import com.elowen.niceTV.core.platform.proxy.ProxyCredentials
import java.util.Random

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnRetry: Button
    private val handler = Handler(Looper.getMainLooper())

    // 标记是否已经完成了初始加载
    private var hasCompletedInitialLoad = false

    // Cookie 检测开始时间
    private var cookieCheckStartTime: Long = 0
    private var resultSent = false

    // WebRTC 阻止脚本
    private val webrtcBlockerScript = """
        (function () {
          try {
            const NativePC = window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection;
            if (!NativePC) return;
            function isBadCandidate(candStr) {
              if (!candStr) return false;
              return /\btyp\s+(host|srflx)\b/i.test(candStr);
            }
            function wrapConfig(config) {
              const c = config ? Object.assign({}, config) : {};
              c.iceTransportPolicy = "relay";
              return c;
            }
            const PatchedPC = function (config, constraints) {
              const pc = new NativePC(wrapConfig(config), constraints);
              const desc = Object.getOwnPropertyDescriptor(pc, "onicecandidate");
              let userHandler = null;
              Object.defineProperty(pc, "onicecandidate", {
                configurable: true,
                enumerable: true,
                get() { return userHandler; },
                set(fn) { userHandler = fn; }
              });
              pc.addEventListener("icecandidate", function (ev) {
                try {
                  const cand = ev && ev.candidate && ev.candidate.candidate;
                  if (isBadCandidate(cand)) {
                    ev.stopImmediatePropagation?.();
                    return;
                  }
                } catch (e) {}
                try {
                  if (typeof userHandler === "function") userHandler.call(pc, ev);
                } catch (e) {}
              }, true);
              return pc;
            };
            PatchedPC.prototype = NativePC.prototype;
            Object.setPrototypeOf(PatchedPC, NativePC);
            if (window.RTCPeerConnection) window.RTCPeerConnection = PatchedPC;
            if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = PatchedPC;
          } catch (e) {}
        })();
    """.trimIndent()

    // 黑色背景注入脚本
    private val blackBackgroundScript = """
        (function() {
            document.body.style.backgroundColor = '#000000';
            document.documentElement.style.backgroundColor = '#000000';
        })();
    """.trimIndent()

    // 关键：模拟真实的 Android 设备 UA 列表
    private val userAgents = arrayOf(
        "Mozilla/5.0 (Linux; Android 14; SM-S926U Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.105 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-A546E Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.86 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-G991U Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.118 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-A546U Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.160 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; Pixel 7a Build/TQ3A.230901.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.120 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-G781B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.139 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; Pixel 6 Build/TQ3A.230901.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.94 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-G990U Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.185 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-F946U Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.128 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S916U Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.86 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S911B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.111 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-F731B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.78 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; M2102J20SG Build/TKQ1.220915.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.78 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; CPH2411 Build/TP1A.220905.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.107 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; M2012K11AC Build/TKQ1.221013.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.78 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-M146B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.155 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; V2158 Build/TP1A.220905.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.154 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; 2201116SG Build/TKQ1.221114.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.155 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-S901B Build/SP1A.210812.016) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.155 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G736U Build/SP1A.210812.016) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.111 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; V2046A Build/SP1A.210812.003) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.118 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; M2012K11AC Build/SP1A.210812.003) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.155 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; motorola edge 30 Build/SP1A.210812.003) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36"
    )

    private val cookieCheckTask = object : Runnable {
        override fun run() {
            val cookies = CookieManager.getInstance().getCookie(webView.url)
            if (hasRequiredAccessCookies(cookies)) {
                tvStatus.text = getString(R.string.webview_status_cookie_success)
                handler.postDelayed({
                    finishWithResult(cookies.orEmpty())
                }, 500)
            } else {
                // 检查是否超过10秒
                val elapsedTime = System.currentTimeMillis() - cookieCheckStartTime
                if (elapsedTime > 10000) {
                    // 超过10秒，提示用户可能卡住
                    tvStatus.text = getString(R.string.webview_status_timeout)
                }
                // 继续每秒检查一次
                handler.postDelayed(this, 1000)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // 初始化视图
        webView = findViewById(R.id.webView)
        loadingLayout = findViewById(R.id.loadingLayout)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        btnRetry = findViewById(R.id.btnRetry)

        // 设置重试按钮点击事件
        btnRetry.setOnClickListener {
            retryLoadingWithNewUserAgent()
        }

        // 初始状态：显示 WebView，隐藏遮罩
        loadingLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // 设置 WebView 背景为黑色
        webView.setBackgroundColor(Color.BLACK)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE // 强制重刷
            userAgentString = userAgents[Random().nextInt(userAgents.size)]
        }

        // 清除所有 Cookie 和缓存
        clearAllCookiesAndCache()

        // 允许第三方 Cookie (Cloudflare 验证有时需要)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebViewActivity", "页面开始加载: $url")
                // 在页面开始加载时注入 WebRTC 阻止脚本
                view?.evaluateJavascript(webrtcBlockerScript, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WebViewActivity", "页面加载完成: $url")
                // 注入黑色背景
                view?.evaluateJavascript(blackBackgroundScript, null)
                finishIfAccessVerified(url)

                // 标记已完成初始加载
                if (!hasCompletedInitialLoad) {
                    hasCompletedInitialLoad = true
                }
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("WebViewActivity", "WebView 错误 - Code: $errorCode, Desc: $description, URL: $failingUrl")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e("WebViewActivity", "HTTP 错误 - URL: ${request?.url}, Status: ${errorResponse?.statusCode}")
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                val creds = ProxyCredentials.get()
                if (creds != null && isLocalProxyHost(host)) {
                    handler?.proceed(creds.first, creds.second)
                } else {
                    super.onReceivedHttpAuthRequest(view, handler, host, realm)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.isForMainFrame != true) {
                    return false
                }
                val url = request.url?.toString()
                // 只有在完成初始加载后，检测到重定向到目标 URL 时，才显示遮罩并开始获取 Cookie
                if (hasCompletedInitialLoad &&
                    (url == "https://supjav.com/zh/" || url == "https://supjav.com/zh")) {
                    webView.visibility = View.VISIBLE
                    loadingLayout.visibility = View.VISIBLE
                    tvStatus.text = getString(R.string.webview_status_fetching)
                    // 记录检测开始时间（加上2秒延迟，因为实际检查是2秒后开始的）
                    cookieCheckStartTime = System.currentTimeMillis() + 2000
                    // 开始循环检查 Cookie
                    handler.postDelayed(cookieCheckTask, 2000) // 延迟2秒后开始检查
                }
                return false
            }

        }

        webView.loadUrl(intent.getStringExtra("url") ?: "https://supjav.com/zh")
    }

    private fun isLocalProxyHost(host: String?): Boolean {
        return host == ProxyConstants.PROXY_HOST || host == "localhost" || host == "127.0.0.1"
    }

    /**
     * 清除所有 Cookie 和缓存
     */
    private fun clearAllCookiesAndCache() {
        // 清除 WebView 的所有 Cookie
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()

        // 清除 WebView 缓存
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()

        // 通过 JavaScript 清除 localStorage 和 sessionStorage
        webView.evaluateJavascript(
            """
            localStorage.clear();
            sessionStorage.clear();
            var cookies = document.cookie.split(';');
            for (var i = 0; i < cookies.length; i++) {
                var cookie = cookies[i];
                var eqPos = cookie.indexOf('=');
                var name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;
                document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
            }
            """.trimIndent(),
            null
        )
    }

    /**
     * 重新加载页面并更换 User-Agent
     */
    private fun retryLoadingWithNewUserAgent() {
        // 停止当前的 Cookie 检查任务
        handler.removeCallbacks(cookieCheckTask)

        // 重置状态
        hasCompletedInitialLoad = false
        cookieCheckStartTime = 0

        // 更新状态文本
        tvStatus.text = getString(R.string.webview_status_retrying)

        // 清除所有 Cookie 和缓存
        clearAllCookiesAndCache()

        // 更换 User-Agent
        webView.settings.userAgentString = userAgents[Random().nextInt(userAgents.size)]

        // 隐藏遮罩，显示 WebView
        loadingLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // 重新加载页面
        webView.loadUrl(intent.getStringExtra("url") ?: "https://supjav.com/zh")
    }

    private fun hasRequiredAccessCookies(cookies: String?): Boolean {
        return cookies?.contains("cf_clearance") == true || cookies?.contains("asgfp2") == true
    }

    private fun finishIfAccessVerified(url: String?): Boolean {
        val normalizedUrl = url?.trimEnd('/') ?: return false
        if (normalizedUrl != "https://supjav.com/zh") return false
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!hasRequiredAccessCookies(cookies)) return false
        finishWithResult(cookies.orEmpty())
        return true
    }

    private fun finishWithResult(cookies: String) {
        if (resultSent) return
        resultSent = true
        handler.removeCallbacks(cookieCheckTask)
        // 停止 WebView 加载并跳转到空白页
        webView.stopLoading()
        webView.loadUrl("about:blank")

        val intent = Intent().apply {
            putExtra("cookies", cookies)
            putExtra("user_agent", webView.settings.userAgentString)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理 Handler 回调
        handler.removeCallbacksAndMessages(null)

        // 清理 WebView
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.clearCache(true)
        webView.destroy()
    }
}
