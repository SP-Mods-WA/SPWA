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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor  = Color.parseColor("#075E54")
        window.navigationBarColor = Color.parseColor("#075E54")

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        // WebView — full screen, hardware layer fixes blank QR canvas
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            visibility = View.INVISIBLE
        }

        topBar      = buildTopBar()
        progressBar = buildProgressBar()
        splashLayout = buildSplashLayout()

        rootLayout.addView(webView)
        rootLayout.addView(topBar)
        rootLayout.addView(progressBar)
        rootLayout.addView(splashLayout)
        setContentView(rootLayout)

        setupWebView()
        webView.loadUrl("https://web.whatsapp.com")
    }

    // ─────────────────────────────────────────────────────────────────────
    // TOP BAR (native Android view — overlays WebView, never blocked by CSP)
    // ─────────────────────────────────────────────────────────────────────
    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            gravity = Gravity.CENTER_VERTICAL
            elevation = 12f
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(56))
        }

        backBtn = TextView(this).apply {
            text = "  ←  "
            textSize = 22f
            setTextColor(Color.WHITE)
            visibility = View.GONE
            setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        }

        val logo = TextView(this).apply {
            text = "💬 "; textSize = 20f
            setPadding(dpToPx(14), 0, 0, 0)
        }

        val title = TextView(this).apply {
            text = "WhatsApp"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        bar.addView(backBtn)
        bar.addView(logo)
        bar.addView(title)
        return bar
    }

    private fun buildProgressBar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            val lp = FrameLayout.LayoutParams(MATCH, dpToPx(3))
            lp.topMargin = dpToPx(56)
            layoutParams = lp
            visibility = View.GONE
        }

    private fun buildSplashLayout(): FrameLayout {
        val splash = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#075E54"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER)
        }
        col.addView(TextView(this).apply { text = "💬"; textSize = 70f; gravity = Gravity.CENTER })
        col.addView(TextView(this).apply {
            text = "WhatsApp"; textSize = 26f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, dpToPx(10), 0, dpToPx(28))
        })
        col.addView(ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        })
        splash.addView(col)
        return splash
    }

    // ─────────────────────────────────────────────────────────────────────
    // WEBVIEW SETUP
    // ─────────────────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            databaseEnabled      = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(false)
            builtInZoomControls  = false
            cacheMode            = WebSettings.LOAD_DEFAULT
            allowFileAccess      = true
            @Suppress("DEPRECATION")
            mixedContentMode     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString      =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, fav: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = View.GONE
                // Inject minimal CSS only — push content below native top bar
                view.evaluateJavascript(cssOnlyInjection(), null)
                // Show webview, hide splash
                if (splashLayout.visibility == View.VISIBLE) {
                    handler.postDelayed({
                        splashLayout.animate().alpha(0f).setDuration(350).withEndAction {
                            splashLayout.visibility = View.GONE
                            webView.visibility = View.VISIBLE
                        }.start()
                    }, 700)
                }
                startPollingPageType()
            }
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                if (url != null &&
                    (url.startsWith("https://web.whatsapp.com") ||
                     url.startsWith("https://whatsapp.com"))) return false
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progressBar.visibility = if (p == 100) View.GONE else View.VISIBLE
                progressBar.progress = p
            }

            // ════════════════════════════════════════════════════════
            // THE FIX: Kotlin-side suppression of ALL JS dialogs.
            // WhatsApp Web calls native window.alert() (not a React
            // modal) when phone-number button is tapped without QR scan.
            // onJsAlert intercepts BEFORE the system dialog is shown.
            // ════════════════════════════════════════════════════════
            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean { result?.confirm(); return true }

            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean { result?.confirm(); return true }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?,
                default: String?, result: JsPromptResult?
            ): Boolean { result?.confirm(""); return true }

            override fun onJsBeforeUnload(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean { result?.confirm(); return true }
        }

        webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
                && webView.canGoBack()) {
                webView.goBack(); true
            } else false
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CSS ONLY — no JS logic, no alert override (handled in Kotlin above)
    // ─────────────────────────────────────────────────────────────────────
    private fun cssOnlyInjection(): String = """
        (function() {
            if (document.getElementById('_wa_native_css')) return;
            var s = document.createElement('style');
            s.id = '_wa_native_css';
            s.textContent =
                /* Space for our native 56dp top bar */
                'html { margin-top: 56px !important; }' +
                /* Hide WhatsApp Web desktop top header */
                'header, [data-testid="conversation-header"] { display:none !important; }' +
                /* Hide "Download for Windows" promo link */
                'a[href*="windows"], [class*="download-btn"] { display:none !important; }';
            document.head.appendChild(s);
        })();
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────────
    // POLL PAGE TYPE — show/hide back button on native top bar
    // ─────────────────────────────────────────────────────────────────────
    private var pollingActive = false

    private fun startPollingPageType() {
        if (pollingActive) return
        pollingActive = true
        pollOnce()
    }

    private fun pollOnce() {
        if (!webView.isAttachedToWindow) { pollingActive = false; return }
        webView.evaluateJavascript("""
            (!!document.querySelector('input[type="tel"],[data-testid*="phone"]')).toString()
        """.trimIndent()) { res ->
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

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); webView.saveState(outState) }
    override fun onRestoreInstanceState(s: Bundle)     { super.onRestoreInstanceState(s); webView.restoreState(s) }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onDestroy()     { handler.removeCallbacksAndMessages(null); webView.destroy(); super.onDestroy() }
}
