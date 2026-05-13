package com.whatsapp.ios

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private lateinit var topBar: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var backBtn: TextView
    private lateinit var titleTv: TextView
    private lateinit var subtitleTv: TextView
    private lateinit var avatarTv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var phoneClickAttempts = 0
    private var pollingActive = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = Color.parseColor("#F0EBE3")
        window.navigationBarColor = Color.parseColor("#F0EBE3")

        // Light status bar icons
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#F0EBE3"))
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

        setupWebViewClients()
        webView.loadUrl("https://web.whatsapp.com")
    }

    // ══════════════════════════════════════════════════════════════════════
    // WEBVIEW
    // ══════════════════════════════════════════════════════════════════════
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).also {
            it.topMargin = dpToPx(56)
        }
        wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        wv.visibility = View.INVISIBLE

        wv.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            databaseEnabled      = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(true)
            builtInZoomControls  = false
            displayZoomControls  = false
            cacheMode            = WebSettings.LOAD_DEFAULT
            allowFileAccess      = true
            mediaPlaybackRequiresUserGesture = false
            @Suppress("DEPRECATION")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // ── This exact UA gives the mobile WhatsApp Web UI ──
            // Same as Chrome on Android — shows QR + phone login + chat
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        return wv
    }

    // ══════════════════════════════════════════════════════════════════════
    // WEBVIEW CLIENTS
    // ══════════════════════════════════════════════════════════════════════
    private fun setupWebViewClients() {
        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String?, fav: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = View.GONE
                phoneClickAttempts = 0

                // Inject CSS cleanup
                view.evaluateJavascript(getCSS(), null)

                // Auto-handle page specific actions
                handler.postDelayed({ handlePageActions(view, url) }, 1500)

                // Show webview, hide splash
                if (splashLayout.visibility == View.VISIBLE) {
                    handler.postDelayed({
                        splashLayout.animate().alpha(0f).setDuration(350).withEndAction {
                            splashLayout.visibility = View.GONE
                            webView.visibility = View.VISIBLE
                        }.start()
                    }, 600)
                }

                if (!pollingActive) { pollingActive = true; pollState(view) }
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                if (url == null) return false
                // Allow all WhatsApp domains
                if (url.startsWith("https://web.whatsapp.com") ||
                    url.startsWith("https://whatsapp.com")     ||
                    url.startsWith("https://www.whatsapp.com")) return false
                // Block external links
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progressBar.progress   = p
                progressBar.visibility = if (p == 100) View.GONE else View.VISIBLE
            }
            // Suppress ALL JS dialogs
            override fun onJsAlert(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean {
                r?.confirm(); return true
            }
            override fun onJsConfirm(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean {
                r?.confirm(); return true
            }
            override fun onJsPrompt(v: WebView?, u: String?, m: String?, d: String?, r: JsPromptResult?): Boolean {
                r?.confirm(""); return true
            }
            override fun onJsBeforeUnload(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean {
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

    // ══════════════════════════════════════════════════════════════════════
    // PAGE ACTION HANDLER
    // "Download WhatsApp" page → auto click "Continue to WhatsApp Web"
    // QR page → auto click "Log in with phone number"
    // ══════════════════════════════════════════════════════════════════════
    private fun handlePageActions(view: WebView, url: String?) {
        val js = """
            (function() {
                // Page 1: "Download WhatsApp" interstitial
                // Find "Continue to WhatsApp Web" button and click it
                var allBtns = document.querySelectorAll('button, a, [role="button"]');
                for (var i = 0; i < allBtns.length; i++) {
                    var txt = (allBtns[i].textContent || '').trim().toLowerCase();
                    if (txt.indexOf('continue') >= 0 && txt.indexOf('web') >= 0) {
                        allBtns[i].click();
                        return 'continue_clicked';
                    }
                }

                // Page 2: QR page — click "Log in with phone number"
                for (var j = 0; j < allBtns.length; j++) {
                    var t = (allBtns[j].textContent || '').trim().toLowerCase();
                    if (t.indexOf('phone') >= 0) {
                        allBtns[j].click();
                        return 'phone_clicked';
                    }
                }

                var sel = document.querySelector('[data-testid="link-device-phone-num-tab"]');
                if (sel) { sel.click(); return 'phone_sel_clicked'; }

                return 'nothing';
            })()
        """.trimIndent()

        view.evaluateJavascript(js) { result ->
            val r = result?.trim('"') ?: ""
            when {
                r == "nothing" && phoneClickAttempts < 10 -> {
                    phoneClickAttempts++
                    handler.postDelayed({ handlePageActions(view, url) }, 1000)
                }
                r.contains("phone") -> {
                    runOnUiThread { backBtn.visibility = View.VISIBLE }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAGE STATE POLLING → update native top bar
    // ══════════════════════════════════════════════════════════════════════
    private fun pollState(view: WebView) {
        if (!view.isAttachedToWindow) { pollingActive = false; return }
        val js = """
            (function() {
                var chatTitle = document.querySelector(
                    '#main header [data-testid="conversation-info-header-chat-title"],' +
                    '#main header span[dir="auto"]'
                );
                if (chatTitle && chatTitle.textContent.trim().length > 0)
                    return 'chat:' + chatTitle.textContent.trim();
                if (document.getElementById('side')) return 'list';
                if (document.querySelector('input[type="tel"]')) return 'phone';
                return 'login';
            })()
        """.trimIndent()

        view.evaluateJavascript(js) { res ->
            val r = res?.trim('"') ?: "login"
            when {
                r.startsWith("chat:") -> {
                    val name = r.removePrefix("chat:").take(22)
                    setChatTopBar(name, name.firstOrNull()?.toString() ?: "?")
                }
                r == "list"  -> setChatListTopBar()
                r == "phone" -> setPhoneTopBar()
                else         -> setDefaultTopBar()
            }
        }
        handler.postDelayed({ pollState(view) }, 800)
    }

    // ══════════════════════════════════════════════════════════════════════
    // TOP BAR STATE SETTERS
    // ══════════════════════════════════════════════════════════════════════
    private fun setDefaultTopBar() = runOnUiThread {
        backBtn.visibility    = View.GONE
        avatarTv.text         = "W"
        avatarTv.background   = circleDrawable("#25D366", dpToPx(36))
        titleTv.text          = "WhatsApp"
        titleTv.setTextColor(Color.parseColor("#25D366"))
        subtitleTv.visibility = View.GONE
    }

    private fun setPhoneTopBar() = runOnUiThread {
        backBtn.visibility    = View.VISIBLE
        avatarTv.text         = "W"
        avatarTv.background   = circleDrawable("#25D366", dpToPx(36))
        titleTv.text          = "WhatsApp"
        titleTv.setTextColor(Color.parseColor("#25D366"))
        subtitleTv.visibility = View.GONE
    }

    private fun setChatListTopBar() = runOnUiThread {
        backBtn.visibility    = View.GONE
        avatarTv.text         = "W"
        avatarTv.background   = circleDrawable("#25D366", dpToPx(36))
        titleTv.text          = "WhatsApp"
        titleTv.setTextColor(Color.parseColor("#25D366"))
        subtitleTv.visibility = View.GONE
    }

    private fun setChatTopBar(name: String, initial: String) = runOnUiThread {
        backBtn.visibility  = View.VISIBLE
        avatarTv.text       = initial.uppercase()
        avatarTv.background = circleDrawable("#06CF9C", dpToPx(36))
        titleTv.text        = name
        titleTv.setTextColor(Color.parseColor("#111B21"))
        subtitleTv.text       = "tap for info"
        subtitleTv.visibility = View.VISIBLE
    }

    // ══════════════════════════════════════════════════════════════════════
    // CSS — minimal cleanup, WhatsApp Web mobile renders correctly
    // ══════════════════════════════════════════════════════════════════════
    private fun getCSS(): String = """
        (function() {
            if (document.getElementById('_wa_css')) return;
            var s = document.createElement('style');
            s.id = '_wa_css';
            s.textContent =
                /* Push content below our native 56dp top bar */
                'html { padding-top: 56px !important; box-sizing:border-box; }' +
                /* Hide WhatsApp's own top bar — we show our native one */
                'header._aigs, ._aigt { display:none !important; }' +
                /* Scrollbar */
                '::-webkit-scrollbar { display:none !important; }';
            document.head.appendChild(s);
        })();
    """.trimIndent()

    // ══════════════════════════════════════════════════════════════════════
    // NATIVE TOP BAR  — matches WhatsApp mobile browser style
    // Light background, green WhatsApp logo text, back arrow
    // ══════════════════════════════════════════════════════════════════════
    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(56))
            setBackgroundColor(Color.parseColor("#F0EBE3"))
            elevation = 2f
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setPadding(dpToPx(4), 0, dpToPx(12), 0)
        }

        // Back arrow
        backBtn = TextView(this).apply {
            text      = "←"
            textSize  = 20f
            setTextColor(Color.parseColor("#111B21"))
            gravity   = Gravity.CENTER
            setPadding(dpToPx(10), 0, dpToPx(6), 0)
            visibility = View.GONE
            setOnClickListener {
                phoneClickAttempts = 0
                if (webView.canGoBack()) webView.goBack()
            }
        }

        // Avatar
        val avatarWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).also {
                it.marginStart = dpToPx(10)
                it.marginEnd   = dpToPx(10)
            }
        }
        avatarTv = TextView(this).apply {
            text       = "W"
            textSize   = 14f
            typeface   = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity    = Gravity.CENTER
            background = circleDrawable("#25D366", dpToPx(36))
            layoutParams = FrameLayout.LayoutParams(dpToPx(36), dpToPx(36))
        }
        avatarWrap.addView(avatarTv)

        // Title + subtitle
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        titleTv = TextView(this).apply {
            text     = "WhatsApp"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#25D366"))
        }
        subtitleTv = TextView(this).apply {
            text      = "tap for info"
            textSize  = 11f
            setTextColor(Color.parseColor("#667781"))
            visibility = View.GONE
        }
        col.addView(titleTv); col.addView(subtitleTv)

        // Right icons row
        val icons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        listOf("⟳", "⌨", "↓", "?").forEach { icon ->
            icons.addView(TextView(this).apply {
                text     = icon
                textSize = 16f
                setTextColor(Color.parseColor("#667781"))
                gravity  = Gravity.CENTER
                setPadding(dpToPx(6), 0, dpToPx(6), 0)
            })
        }

        row.addView(backBtn)
        row.addView(avatarWrap)
        row.addView(col)
        row.addView(icons)
        bar.addView(row)

        // Bottom border
        bar.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(1)).also {
                it.gravity = Gravity.BOTTOM
            }
            setBackgroundColor(Color.parseColor("#E0DDD8"))
        })
        return bar
    }

    // ══════════════════════════════════════════════════════════════════════
    // PROGRESS BAR
    // ══════════════════════════════════════════════════════════════════════
    private fun buildProgressBar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList
                .valueOf(Color.parseColor("#25D366"))
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(2)).also {
                it.topMargin = dpToPx(56)
            }
            visibility = View.GONE
        }

    // ══════════════════════════════════════════════════════════════════════
    // SPLASH SCREEN  — matches WhatsApp mobile style
    // ══════════════════════════════════════════════════════════════════════
    private fun buildSplashLayout(): FrameLayout {
        val splash = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#F0EBE3"))
        }

        // Center content
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL).also {
                it.bottomMargin = dpToPx(80)
            }
        }

        // WhatsApp logo circle
        col.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(80)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            addView(TextView(this@MainActivity).apply {
                text       = "W"
                textSize   = 36f
                typeface   = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity    = Gravity.CENTER
                background = circleDrawable("#25D366", dpToPx(80))
                layoutParams = FrameLayout.LayoutParams(dpToPx(80), dpToPx(80))
            })
        })

        col.addView(spacer(24))

        col.addView(TextView(this).apply {
            text     = "WhatsApp"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#25D366"))
            gravity  = Gravity.CENTER
        })

        col.addView(spacer(8))

        col.addView(TextView(this).apply {
            text     = "Simple. Reliable. Private."
            textSize = 13f
            setTextColor(Color.parseColor("#667781"))
            gravity  = Gravity.CENTER
        })

        splash.addView(col)

        // Bottom section
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).also {
                it.bottomMargin = dpToPx(36)
            }
        }
        bottom.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList
                .valueOf(Color.parseColor("#25D366"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), dpToPx(2)).also {
                it.gravity      = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dpToPx(14)
            }
        })
        bottom.addView(TextView(this).apply {
            text     = "from Meta"
            textSize = 12f
            setTextColor(Color.parseColor("#667781"))
            gravity  = Gravity.CENTER
        })
        splash.addView(bottom)
        return splash
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════
    private fun spacer(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dpToPx(dp))
    }

    private fun circleDrawable(hexColor: String, size: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hexColor))
            setSize(size, size)
        }

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
        handler.removeCallbacksAndMessages(null); webView.destroy(); super.onDestroy()
    }
}
