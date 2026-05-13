package com.whatsapp.ios

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create root layout programmatically
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
                domStorageEnabled = true          // WhatsApp Web uses localStorage
                databaseEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                // 🧙‍♂️ Trick WhatsApp: Spoof as Google Chrome on Android phone
                userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36"
            }

            webViewClient = MyWebViewClient()
            webChromeClient = MyWebChromeClient()

            // Enable remote debugging (optional, for Chrome DevTools)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            clearCache(true)
            clearHistory()

            loadUrl("https://web.whatsapp.com")
        }

        rootLayout.addView(webView)
        setContentView(rootLayout)
    }

    // Handle back button for WebView navigation
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

    // Custom WebViewClient to intercept page loading and inject JS
    private inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url.toString()
            // Stay inside WhatsApp Web domain
            return if (url.startsWith("https://web.whatsapp.com")) {
                false
            } else {
                // Open external links in browser (optional)
                true
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)

            // 🎨 Inject JavaScript to force mobile UI and remove any "desktop" detection
            view?.evaluateJavascript(
                """
                (function() {
                    // 1. Override navigator.webdriver to avoid detection as automation
                    Object.defineProperty(navigator, 'webdriver', {
                        get: () => undefined
                    });
                    
                    // 2. Remove any webdriver attribute from html element
                    if (document.documentElement.hasAttribute('webdriver')) {
                        document.documentElement.removeAttribute('webdriver');
                    }
                    
                    // 3. Fake Chrome runtime object
                    window.chrome = window.chrome || { runtime: {}, loadTimes: function() {}, csi: function() {}, app: {} };
                    
                    // 4. Force viewport to be exactly phone-like
                    var meta = document.querySelector('meta[name=viewport]');
                    if (meta) {
                        meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes, viewport-fit=cover');
                    } else {
                        var newMeta = document.createElement('meta');
                        newMeta.name = 'viewport';
                        newMeta.content = 'width=device-width, initial-scale=1.0, user-scalable=yes';
                        document.head.appendChild(newMeta);
                    }
                    
                    // 5. Add custom CSS to make UI fit perfectly on phone
                    var style = document.createElement('style');
                    style.innerHTML = `
                        /* Force WhatsApp Web to use full screen width like a phone app */
                        body {
                            overflow-x: hidden !important;
                        }
                        .app-wrapper, .two, ._akbu {
                            max-width: 100% !important;
                        }
                        /* Make sure the chat list and chat panel take full width */
                        ._akbu {
                            width: 100% !important;
                        }
                        /* Optional: hide desktop-only elements if any */
                        .landing-window, .intro {
                            width: 100% !important;
                        }
                    `;
                    document.head.appendChild(style);
                    
                    console.log('UI forced to mobile phone mode');
                })();
                """.trimIndent(), null
            )
        }
    }

    // Optional: handle JavaScript dialogs, progress, etc.
    private inner class MyWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            // You can show a progress bar here if needed
        }
    }
}
