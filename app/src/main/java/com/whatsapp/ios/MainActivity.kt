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
                domStorageEnabled = true
                databaseEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                // 🖥️ Desktop Chrome User-Agent (critical to get QR code)
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            }

            webViewClient = MyWebViewClient()
            webChromeClient = WebChromeClient()

            clearCache(true)
            clearHistory()

            loadUrl("https://web.whatsapp.com")
        }

        rootLayout.addView(webView)
        setContentView(rootLayout)
    }

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

    private inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url.toString()
            return if (url.startsWith("https://web.whatsapp.com")) {
                false
            } else {
                true
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)

            // 🎨 Inject JavaScript to transform desktop UI into mobile-friendly layout
            view?.evaluateJavascript(
                """
                (function() {
                    // Add meta viewport for proper scaling
                    var meta = document.querySelector('meta[name=viewport]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        document.head.appendChild(meta);
                    }
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes');
                    
                    // Inject custom CSS to make WhatsApp Web look like a phone app
                    var style = document.createElement('style');
                    style.innerHTML = `
                        /* Make the entire layout full width and remove sidebars */
                        body, html {
                            overflow-x: hidden !important;
                            width: 100% !important;
                        }
                        .app-wrapper, .two, ._akbu, ._aigs {
                            width: 100% !important;
                            max-width: 100% !important;
                        }
                        /* Force chat list to be full width on mobile */
                        ._ak8q, ._akbu {
                            width: 100vw !important;
                        }
                        /* Adjust font sizes for touch */
                        ._ak8c, ._ak8d, ._ak8e {
                            font-size: 16px !important;
                        }
                        /* Make buttons and inputs larger for fingers */
                        button, div[role="button"], input, ._ak8r {
                            min-height: 44px !important;
                        }
                        /* Hide desktop-only elements that cause horizontal scroll */
                        ._ak8s, ._ak8t, ._ak8u {
                            display: none !important;
                        }
                        /* Ensure main chat area takes full width */
                        ._akbu, ._ak8v {
                            flex: 1 1 100% !important;
                        }
                    `;
                    document.head.appendChild(style);
                    
                    // Force a resize event to trigger WhatsApp's responsive adjustments
                    window.dispatchEvent(new Event('resize'));
                    
                    console.log('Desktop UI transformed to mobile-friendly mode');
                })();
                """.trimIndent(), null
            )
        }
    }
}
