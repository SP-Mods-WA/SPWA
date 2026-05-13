package com.whatsapp.ios

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true                // WhatsApp Web uses localStorage
                databaseEnabled = true                  // for IndexedDB
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true                  // needed for some internal stuff
                allowContentAccess = true
                setAppCacheEnabled(true)
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                // ✅ Most important: Modern Android Chrome Mobile User-Agent
                userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36"
            }

            webViewClient = MyWebViewClient()
            webChromeClient = WebChromeClient()   // basic one is enough

            // Clear cache and cookies before loading (optional but helpful for testing)
            clearCache(true)
            clearHistory()

            loadUrl("https://web.whatsapp.com")
        }

        rootLayout.addView(webView)
        setContentView(rootLayout)
    }

    // Back button support
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // Custom WebViewClient to prevent external redirects and force mobile version
    private inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url.toString()
            // only allow WhatsApp Web domain
            return if (url.startsWith("https://web.whatsapp.com")) {
                false   // load inside WebView
            } else {
                true    // do not open external browser
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Extra safety: inject viewport meta to force mobile layout
            view?.evaluateJavascript(
                """
                (function() {
                    var meta = document.querySelector('meta[name=viewport]');
                    if (meta) {
                        meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes');
                    } else {
                        var newMeta = document.createElement('meta');
                        newMeta.name = 'viewport';
                        newMeta.content = 'width=device-width, initial-scale=1.0';
                        document.head.appendChild(newMeta);
                    }
                })();
                """.trimIndent(), null
            )
        }
    }
}
