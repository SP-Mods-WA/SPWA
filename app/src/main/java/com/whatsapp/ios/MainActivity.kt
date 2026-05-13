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

                // Desktop Chrome user-agent to get QR code (not blocked)
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

            // 🎨 Transform desktop UI to perfectly fit phone screen
            view?.evaluateJavascript(
                """
                (function() {
                    // 1. Set proper viewport for mobile
                    let meta = document.querySelector('meta[name=viewport]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        document.head.appendChild(meta);
                    }
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes, viewport-fit=cover');
                    
                    // 2. Inject CSS to make WhatsApp Web responsive as a phone app
                    let style = document.createElement('style');
                    style.id = 'mobile-wa-fix';
                    style.innerHTML = `
                        /* Global reset */
                        html, body {
                            margin: 0 !important;
                            padding: 0 !important;
                            width: 100% !important;
                            overflow-x: hidden !important;
                        }
                        /* Main container adjustments */
                        ._akbu, .app-wrapper, .two, ._aigs {
                            width: 100% !important;
                            max-width: 100% !important;
                            margin: 0 !important;
                            padding: 0 !important;
                            flex-direction: column !important;
                        }
                        /* QR code container - make it big and centered */
                        .landing-window, ._ak8o, [data-testid="landing-window"] {
                            display: flex !important;
                            flex-direction: column !important;
                            align-items: center !important;
                            justify-content: center !important;
                            width: 100% !important;
                            padding: 16px !important;
                            box-sizing: border-box !important;
                        }
                        /* QR code image itself */
                        img[src*="qr"], canvas, ._ak8r {
                            width: 70vw !important;
                            height: 70vw !important;
                            max-width: 300px !important;
                            max-height: 300px !important;
                            margin: 20px auto !important;
                        }
                        /* Text and buttons */
                        ._ak8c, ._ak8d, ._ak8e, ._ak8f {
                            font-size: 16px !important;
                            text-align: center !important;
                            padding: 8px !important;
                        }
                        /* Hide desktop-only download button section or adjust */
                        ._ak8g, ._ak8h, ._ak8i {
                            display: none !important;
                        }
                        /* Buttons and inputs bigger for touch */
                        button, a[role="button"], ._ak8x {
                            min-height: 48px !important;
                            padding: 12px 20px !important;
                            font-size: 16px !important;
                        }
                        /* Ensure the left panel doesn't show as sidebar */
                        ._akbu, ._ak8v {
                            width: 100% !important;
                            flex: 1 !important;
                        }
                        /* Hide any desktop intro panel that might cause overflow */
                        ._ak8s, ._ak8t, ._ak8u {
                            display: none !important;
                        }
                        /* Adjust the main chat area later when logged in */
                        ._akbu._akbw, ._akbw {
                            width: 100% !important;
                            max-width: 100% !important;
                        }
                        /* Padding for bottom navigation (if any) */
                        body {
                            padding-bottom: env(safe-area-inset-bottom) !important;
                        }
                    `;
                    document.head.appendChild(style);
                    
                    // 3. Force a resize event so WhatsApp recalculates layout
                    window.dispatchEvent(new Event('resize'));
                    
                    // 4. If the QR code is inside a canvas, ensure it scales
                    var qrCanvas = document.querySelector('canvas');
                    if (qrCanvas) {
                        qrCanvas.style.width = 'min(70vw, 300px)';
                        qrCanvas.style.height = 'auto';
                    }
                    
                    console.log('Mobile UI injection complete');
                })();
                """.trimIndent(), null
            )
        }
    }
}
