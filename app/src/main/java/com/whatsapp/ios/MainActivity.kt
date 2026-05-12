package com.whatsapp.ios

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout
    private lateinit var splashLayout: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var backBtn: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var isOnPhonePage = false
    private var pollingActive = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor     = Color.parseColor("#075E54")
        window.navigationBarColor = Color.parseColor("#075E54")

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#ECE5DD"))
        }

        webView      = buildWebView()
        topBar       = buildTopBar()
        progressBar  = buildProgressBar()
        splashLayout = buildSplashLayout()

        rootLayout.addView(webView)
        rootLayout.addView(topBar)
        rootLayout.addView(progressBar)
        rootLayout.addView(splashLayout)
        setContentView(rootLayout)

        setupWebView()
        webView.loadUrl("https://web.whatsapp.com")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        wv.visibility = View.INVISIBLE

        wv.settings.apply {
            javaScriptEnabled     = true
            domStorageEnabled     = true
            databaseEnabled       = true
            loadWithOverviewMode  = true
            useWideViewPort       = true
            setSupportZoom(true)
            builtInZoomControls   = false
            displayZoomControls   = false
            cacheMode             = WebSettings.LOAD_DEFAULT
            allowFileAccess       = true
            mediaPlaybackRequiresUserGesture = false
            @Suppress("DEPRECATION")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        return wv
    }

    // ─── WEBVIEW CLIENT + CHROME CLIENT ──────────────────────────────────
    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String?, fav: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = View.GONE

                // Inject minimal CSS — hide WhatsApp Web's own header (we have native one)
                // and push content below our 56dp native top bar
                view.evaluateJavascript(cssInjection(), null)

                // Reveal webview, fade splash
                if (splashLayout.visibility == View.VISIBLE) {
                    handler.postDelayed({
                        splashLayout.animate().alpha(0f).setDuration(400).withEndAction {
                            splashLayout.visibility = View.GONE
                            webView.visibility = View.VISIBLE
                        }.start()
                    }, 600)
                }

                startPolling()
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                // Keep all WhatsApp-related navigation inside our WebView
                if (url == null) return false
                return !(url.startsWith("https://web.whatsapp.com") ||
                         url.startsWith("https://whatsapp.com")     ||
                         url.startsWith("https://www.whatsapp.com"))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progressBar.progress   = p
                progressBar.visibility = if (p == 100) View.GONE else View.VISIBLE
            }

            // ════════════════════════════════════════════════════════════
            // Suppress ALL native JS dialogs (alert / confirm / prompt).
            // WhatsApp Web shows alert("Please scan QR…") when phone
            // button is tapped before scanning — this kills it silently.
            // result.confirm() MUST be called to unblock the JS thread.
            // ════════════════════════════════════════════════════════════
            override fun onJsAlert(v: WebView?, url: String?, msg: String?, r: JsResult?): Boolean {
                r?.confirm(); return true
            }
            override fun onJsConfirm(v: WebView?, url: String?, msg: String?, r: JsResult?): Boolean {
                r?.confirm(); return true
            }
            override fun onJsPrompt(v: WebView?, url: String?, msg: String?, d: String?, r: JsPromptResult?): Boolean {
                r?.confirm(""); return true
            }
            override fun onJsBeforeUnload(v: WebView?, url: String?, msg: String?, r: JsResult?): Boolean {
                r?.confirm(); return true
            }
        }

        webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK &&
                event.action == KeyEvent.ACTION_UP &&
                webView.canGoBack()) {
                webView.goBack(); true
            } else false
        }
    }

    // ─── CSS INJECTION ────────────────────────────────────────────────────
    private fun cssInjection(): String = """
        (function() {
            if (document.getElementById('_wa_css')) return;
            var s = document.createElement('style');
            s.id = '_wa_css';
            s.textContent =
                'html { padding-top: 56px !important; box-sizing: border-box; }' +
                /* Hide WhatsApp Web top app-bar (we show our own native one) */
                'header { display: none !important; }' +
                /* Hide "Download for Windows" promo banner */
                '[data-testid="intro-desktop-app-promo"],' +
                '[class*="landing-header"],' +
                'a[href*="windows.whatsapp"],' +
                'a[href*="desktop"] { display: none !important; }';
            document.head.appendChild(s);

            /* Dynamically hide Windows promo if injected later by React */
            new MutationObserver(function() {
                ['a[href*="windows.whatsapp"]','a[href*="desktop"]',
                 '[data-testid="intro-desktop-app-promo"]'].forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        var card = el.closest('div') || el;
                        card.style.display = 'none';
                    });
                });
            }).observe(document.body || document.documentElement,
                       { childList: true, subtree: true });
        })();
    """.trimIndent()

    // ─── PAGE TYPE POLLING — back button visibility ───────────────────────
    private fun startPolling() {
        if (pollingActive) return
        pollingActive = true
        pollOnce()
    }

    private fun pollOnce() {
        if (!webView.isAttachedToWindow) { pollingActive = false; return }
        webView.evaluateJavascript(
            "(!!document.querySelector('input[type=\"tel\"],[data-testid*=\"phone\"]')).toString()"
        ) { res ->
            val onPhone = res?.trim('"') == "true"
            runOnUiThread {
                if (onPhone != isOnPhonePage) {
                    isOnPhonePage = onPhone
                    backBtn.visibility = if (onPhone) View.VISIBLE else View.GONE
                }
            }
        }
        handler.postDelayed({ pollOnce() }, 900)
    }

    // ─── NATIVE UI BUILDERS ───────────────────────────────────────────────
    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            gravity     = Gravity.CENTER_VERTICAL
            elevation   = 12f
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(56))
        }
        backBtn = TextView(this).apply {
            text = "  ←  "; textSize = 22f; setTextColor(Color.WHITE)
            visibility = View.GONE
            setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        }
        val logo = TextView(this).apply {
            text = "💬 "; textSize = 20f; setPadding(dpToPx(14), 0, 0, 0)
        }
        val title = TextView(this).apply {
            text = "WhatsApp"; textSize = 19f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        bar.addView(backBtn); bar.addView(logo); bar.addView(title)
        return bar
    }

    private fun buildProgressBar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(3)).also {
                it.topMargin = dpToPx(56)
            }
            visibility = View.GONE
        }

    private fun buildSplashLayout(): FrameLayout {
        val splash = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#075E54"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER)
        }
        col.addView(TextView(this).apply {
            text = "💬"; textSize = 72f; gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = "WhatsApp"; textSize = 26f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, dpToPx(10), 0, dpToPx(28))
        })
        col.addView(ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList
                .valueOf(Color.parseColor("#25D366"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
                .also { it.gravity = Gravity.CENTER_HORIZONTAL }
        })
        splash.addView(col)
        return splash
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); webView.saveState(outState)
    }
    override fun onRestoreInstanceState(s: Bundle) {
        super.onRestoreInstanceState(s); webView.restoreState(s)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
