package com.whatsapp.ios

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

                // Desktop Chrome User-Agent (to get QR code)
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

            // Delay injection slightly to ensure DOM is ready
            Handler(Looper.getMainLooper()).postDelayed({
                view?.evaluateJavascript(
                    """
                    (function() {
                        console.log("Starting UI injection...");
                        
                        // 1. Force viewport for mobile scaling
                        let meta = document.querySelector('meta[name=viewport]');
                        if (!meta) {
                            meta = document.createElement('meta');
                            meta.name = 'viewport';
                            document.head.appendChild(meta);
                        }
                        meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes');
                        
                        // 2. Find all possible QR code containers and scale them
                        function scaleQrCode() {
                            // Common selectors for WhatsApp Web QR container
                            let qrContainer = document.querySelector('[data-testid="qr-code"]') ||
                                              document.querySelector('._ak8o') ||
                                              document.querySelector('._ak8r') ||
                                              document.querySelector('div[class*="qr"]') ||
                                              document.querySelector('canvas')?.parentElement;
                            
                            if (qrContainer) {
                                qrContainer.style.display = 'flex';
                                qrContainer.style.flexDirection = 'column';
                                qrContainer.style.alignItems = 'center';
                                qrContainer.style.justifyContent = 'center';
                                qrContainer.style.width = '100%';
                                qrContainer.style.padding = '20px';
                                qrContainer.style.boxSizing = 'border-box';
                            }
                            
                            // Scale the actual QR image or canvas
                            let qrElement = document.querySelector('canvas') || 
                                           document.querySelector('img[alt*="QR"]') ||
                                           document.querySelector('img[src*="qr"]');
                            if (qrElement) {
                                let size = Math.min(window.innerWidth * 0.7, 280);
                                qrElement.style.width = size + 'px';
                                qrElement.style.height = size + 'px';
                                qrElement.style.maxWidth = '100%';
                                qrElement.style.margin = '20px auto';
                            }
                        }
                        
                        // 3. Adjust text and buttons
                        function adjustText() {
                            let allText = document.querySelectorAll('p, span, div, h1, h2, h3, h4, ._ak8c, ._ak8d, ._ak8e');
                            allText.forEach(el => {
                                if (el.innerText && el.innerText.length > 0) {
                                    el.style.fontSize = '16px';
                                    el.style.lineHeight = '1.4';
                                    el.style.textAlign = 'center';
                                }
                            });
                            
                            // Make buttons touch-friendly
                            let buttons = document.querySelectorAll('button, a[role="button"], ._ak8x, ._ak8r');
                            buttons.forEach(btn => {
                                btn.style.minHeight = '48px';
                                btn.style.padding = '12px 24px';
                                btn.style.fontSize = '16px';
                                btn.style.margin = '8px';
                            });
                        }
                        
                        // 4. Hide desktop-only download section
                        function hideDesktopElements() {
                            let downloadSection = document.querySelector('._ak8g, ._ak8h, ._ak8i');
                            if (downloadSection) downloadSection.style.display = 'none';
                            // Also hide any "Download WhatsApp for Windows" text container
                            let downloadText = Array.from(document.querySelectorAll('*')).find(el => el.innerText?.includes('Download WhatsApp for Windows'));
                            if (downloadText) downloadText.style.display = 'none';
                        }
                        
                        // 5. Apply all adjustments
                        scaleQrCode();
                        adjustText();
                        hideDesktopElements();
                        
                        // 6. Observe if DOM changes (e.g., after login)
                        const observer = new MutationObserver(() => {
                            scaleQrCode();
                            adjustText();
                            hideDesktopElements();
                        });
                        observer.observe(document.body, { childList: true, subtree: true });
                        
                        // Force resize to recalculate
                        window.dispatchEvent(new Event('resize'));
                        console.log("Injection complete: QR code scaled, desktop elements hidden.");
                    })();
                    """.trimIndent(), null
                )
            }, 500) // 500ms delay
        }
    }
}
