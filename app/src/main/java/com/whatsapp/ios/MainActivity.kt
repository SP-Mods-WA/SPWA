package com.whatsapp.ios

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var splashLayout: RelativeLayout
    private lateinit var rootLayout: RelativeLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make app full screen / edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.parseColor("#075E54")
        window.navigationBarColor = Color.parseColor("#075E54")

        // Root layout
        rootLayout = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#ECE5DD"))
        }

        // WebView
        webView = WebView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.INVISIBLE
        }

        // Top progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 6
            )
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            layoutParams = params
            visibility = View.GONE
        }

        // Splash / loading screen
        splashLayout = buildSplashLayout()

        rootLayout.addView(webView)
        rootLayout.addView(progressBar)
        rootLayout.addView(splashLayout)
        setContentView(rootLayout)

        setupWebView()
        loadWhatsAppWeb()
    }

    private fun buildSplashLayout(): RelativeLayout {
        val splash = RelativeLayout(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#075E54"))
            id = View.generateViewId()
        }

        // WhatsApp icon (text-based since we can't use drawables easily here)
        val icon = TextView(this).apply {
            text = "💬"
            textSize = 72f
            gravity = android.view.Gravity.CENTER
            id = View.generateViewId()
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.CENTER_IN_PARENT)
            params.bottomMargin = dpToPx(60)
            layoutParams = params
        }

        val appName = TextView(this).apply {
            text = "WhatsApp"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.BELOW, icon.id)
            params.addRule(RelativeLayout.CENTER_HORIZONTAL)
            params.topMargin = dpToPx(12)
            layoutParams = params
            id = View.generateViewId()
        }

        val loadingBar = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            val params = RelativeLayout.LayoutParams(dpToPx(40), dpToPx(40))
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.CENTER_HORIZONTAL)
            params.bottomMargin = dpToPx(80)
            layoutParams = params
        }

        splash.addView(icon)
        splash.addView(appName)
        splash.addView(loadingBar)
        return splash
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        @Suppress("DEPRECATION")
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.databaseEnabled = true

        val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        webSettings.userAgentString = desktopUserAgent

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // Inject custom mobile-app style CSS + JS after page loads
                view?.evaluateJavascript(getInjectionScript(), null)

                // Show webview, hide splash after short delay
                webView.postDelayed({
                    splashLayout.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            splashLayout.visibility = View.GONE
                            webView.visibility = View.VISIBLE
                            webView.animate().alpha(1f).setDuration(300).start()
                        }.start()
                }, 1200)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let {
                    if (it.startsWith("https://web.whatsapp.com") || it.startsWith("https://whatsapp.com")) {
                        return false
                    }
                }
                return super.shouldOverrideUrlLoading(view, url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            // Block ALL JS alert() / confirm() dialogs globally
            override fun onJsAlert(
                view: WebView?, url: String?, message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                result?.confirm()
                return true
            }

            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                result?.confirm()
                return true
            }
        }

        webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    /**
     * JavaScript injection to transform WhatsApp Web into a native app-like UI.
     * Hides desktop elements, scales QR to fit screen, adds app-style header & footer.
     * FIX: Removed alert(). QR copied via toDataURL() to avoid blank canvas cross-origin issue.
     */
    private fun getInjectionScript(): String {
        return """
        (function() {
            if (window.__waInjected) return;
            window.__waInjected = true;

            // === SUPPRESS ALL JS DIALOGS AT JS LEVEL ===
            // WhatsApp Web may use React modals OR native alert — kill both
            window.alert = function() {};
            window.confirm = function() { return true; };
            window.prompt = function() { return ''; };

            // === INJECT STYLE ===
            var style = document.createElement('style');
            style.innerHTML = `
                /* Reset & base */
                * { box-sizing: border-box; }
                html, body {
                    margin: 0 !important;
                    padding: 0 !important;
                    background: #ECE5DD !important;
                    overflow-x: hidden !important;
                    font-family: 'Segoe UI', sans-serif !important;
                }
                
                /* Hide desktop-only top bar, footer nav, sidebar, download banners */
                [data-testid="wa-web-landing-screen"] > div > div:first-child,
                ._aigs, ._aigd, ._aig-, 
                [data-testid="intro-md-beta-logo-dark"],
                [data-testid="intro-md-beta-logo-light"] { display: none !important; }

                /* Hide "Download for Windows" banner */
                .landing-wrapper .app-wrapper-web,
                a[href*="windows"],
                div[class*="download"] { display: none !important; }

                /* === CUSTOM HEADER === */
                #wa-app-header {
                    position: fixed;
                    top: 0; left: 0; right: 0;
                    height: 60px;
                    background: #075E54;
                    display: flex;
                    align-items: center;
                    padding: 0 20px;
                    z-index: 99999;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.3);
                }
                #wa-app-header span {
                    color: white;
                    font-size: 22px;
                    font-weight: 700;
                    letter-spacing: 0.3px;
                }
                #wa-app-header .wa-logo {
                    font-size: 28px;
                    margin-right: 10px;
                }

                /* === MAIN CARD === */
                #wa-login-card {
                    position: fixed;
                    top: 72px; left: 0; right: 0; bottom: 60px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                    background: #ECE5DD;
                    overflow-y: auto;
                }
                #wa-qr-wrapper {
                    background: white;
                    border-radius: 24px;
                    padding: 24px;
                    width: 100%;
                    max-width: 360px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.12);
                }
                #wa-qr-title {
                    font-size: 20px;
                    font-weight: 700;
                    color: #111;
                    margin-bottom: 6px;
                    text-align: center;
                }
                #wa-qr-subtitle {
                    font-size: 13px;
                    color: #667781;
                    margin-bottom: 20px;
                    text-align: center;
                    line-height: 1.5;
                }
                #wa-qr-canvas-holder {
                    width: 240px;
                    height: 240px;
                    border-radius: 16px;
                    overflow: hidden;
                    border: 2px solid #f0f0f0;
                }
                #wa-qr-canvas-holder canvas,
                #wa-qr-canvas-holder img {
                    width: 100% !important;
                    height: 100% !important;
                }
                
                /* Steps */
                #wa-steps {
                    margin-top: 20px;
                    width: 100%;
                }
                .wa-step {
                    display: flex;
                    align-items: flex-start;
                    margin-bottom: 14px;
                }
                .wa-step-num {
                    min-width: 26px;
                    height: 26px;
                    border-radius: 50%;
                    border: 1.5px solid #25D366;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 12px;
                    font-weight: 700;
                    color: #25D366;
                    margin-right: 12px;
                    margin-top: 1px;
                }
                .wa-step-text {
                    font-size: 13px;
                    color: #3B4A54;
                    line-height: 1.5;
                }
                
                /* Phone number login button */
                #wa-phone-btn {
                    margin-top: 16px;
                    width: 100%;
                    padding: 14px;
                    border-radius: 28px;
                    background: #25D366;
                    color: white;
                    font-size: 15px;
                    font-weight: 600;
                    border: none;
                    cursor: pointer;
                    text-align: center;
                }

                /* === BOTTOM BAR === */
                #wa-bottom-bar {
                    position: fixed;
                    bottom: 0; left: 0; right: 0;
                    height: 56px;
                    background: white;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 13px;
                    color: #667781;
                    border-top: 1px solid #e9edef;
                    z-index: 99999;
                }
                #wa-bottom-bar a {
                    color: #25D366;
                    font-weight: 600;
                    text-decoration: none;
                    margin-left: 4px;
                }

                /* Hide all original page content */
                body > * { visibility: hidden !important; }
                #wa-app-header, #wa-login-card, #wa-bottom-bar { visibility: visible !important; }
            `;
            document.head.appendChild(style);

            // === INJECT CUSTOM HTML STRUCTURE ===
            
            // Header
            var header = document.createElement('div');
            header.id = 'wa-app-header';
            header.innerHTML = '<span class="wa-logo">💬</span><span>WhatsApp</span>';
            document.body.appendChild(header);

            // Main card
            var card = document.createElement('div');
            card.id = 'wa-login-card';
            card.innerHTML = '<div id="wa-qr-wrapper">' +
                '<div id="wa-qr-title">Scan to log in</div>' +
                '<div id="wa-qr-subtitle">Open WhatsApp on your phone<br>and scan this code</div>' +
                '<div id="wa-qr-canvas-holder"></div>' +
                '<div id="wa-steps">' +
                    '<div class="wa-step"><div class="wa-step-num">1</div><div class="wa-step-text">Scan the QR code with your phone camera</div></div>' +
                    '<div class="wa-step"><div class="wa-step-num">2</div><div class="wa-step-text">Tap the link to open WhatsApp</div></div>' +
                    '<div class="wa-step"><div class="wa-step-num">3</div><div class="wa-step-text">Scan the QR code again to link your account</div></div>' +
                '</div>' +
                '<button id="wa-phone-btn">Log in with phone number</button>' +
            '</div>';
            document.body.appendChild(card);

            // Bottom bar
            var bottom = document.createElement('div');
            bottom.id = 'wa-bottom-bar';
            bottom.innerHTML = "Don't have an account? <a href='https://whatsapp.com/dl/'>Get started</a>";
            document.body.appendChild(bottom);

            // === MOVE REAL QR CANVAS INTO OUR HOLDER ===
            // Canvas cloneNode = blank (pixel data not copied). We MOVE the real element.
            function moveQR() {
                var holder = document.getElementById('wa-qr-canvas-holder');
                if (!holder) return;

                var realCanvas = document.querySelector('canvas');
                if (realCanvas && realCanvas.parentNode !== holder) {
                    realCanvas.style.cssText = 'width:100%!important;height:100%!important;display:block;border-radius:12px;';
                    holder.innerHTML = '';
                    holder.appendChild(realCanvas);
                    return;
                }
                // Fallback: base64 data URI image
                var realImg = document.querySelector('img[src^="data:image"]');
                if (realImg && realImg.parentNode !== holder) {
                    realImg.style.cssText = 'width:100%!important;height:100%!important;display:block;border-radius:12px;';
                    holder.innerHTML = '';
                    holder.appendChild(realImg);
                }
            }

            // MutationObserver — watch for QR canvas appearing in DOM
            var qrObserver = new MutationObserver(function() {
                var canvas = document.querySelector('canvas');
                if (canvas) {
                    moveQR();
                    // Re-check every 1s for QR refresh (WhatsApp rotates it)
                    setInterval(moveQR, 1000);
                    qrObserver.disconnect();
                }
            });
            qrObserver.observe(document.body, { childList: true, subtree: true });
            // Also try immediately in case already rendered
            moveQR();

            // Phone number button — dispatch real pointer/mouse events to avoid React modal bug
            document.getElementById('wa-phone-btn').addEventListener('click', function() {
                // Try data-testid selectors WhatsApp Web uses
                var selectors = [
                    '[data-testid="link-device-phone-num-tab"]',
                    '[data-testid="link-device-phone-num-btn"]',
                    'a[href*="phone"]',
                    '[aria-label*="phone" i]',
                    '[data-testid*="phone"]'
                ];
                var target = null;
                for (var i = 0; i < selectors.length; i++) {
                    target = document.querySelector(selectors[i]);
                    if (target) break;
                }
                if (!target) {
                    // Text content fallback
                    document.querySelectorAll('a, button, [role="button"]').forEach(function(el) {
                        if (!target && el.textContent && el.textContent.toLowerCase().includes('phone')) {
                            target = el;
                        }
                    });
                }
                if (target) {
                    // Use dispatchEvent with real pointer events — avoids React synthetic event issues
                    ['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(evtName) {
                        target.dispatchEvent(new MouseEvent(evtName, {bubbles: true, cancelable: true, view: window}));
                    });
                }
            });

        })();
        """.trimIndent()
    }

    private fun loadWhatsAppWeb() {
        webView.loadUrl("https://web.whatsapp.com")
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
