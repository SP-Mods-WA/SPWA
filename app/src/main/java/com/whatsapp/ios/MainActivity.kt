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
    private var pollingActive = false
    private var attempts = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = Color.parseColor("#F0EBE3")
        window.navigationBarColor = Color.parseColor("#F0EBE3")
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
        // Desktop UA + ?continueToWhatsApp=true = no interstitial, full WA Web
        webView.loadUrl("https://web.whatsapp.com/?continueToWhatsApp=true")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).also { it.topMargin = dpToPx(56) }
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
            // Desktop UA — full WhatsApp Web, no interstitial
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        return wv
    }

    private fun setupWebViewClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, fav: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = View.GONE
                attempts = 0
                // Inject all scripts
                view.evaluateJavascript(getMasterScript(), null)
                // Show webview
                if (splashLayout.visibility == View.VISIBLE) {
                    handler.postDelayed({
                        splashLayout.animate().alpha(0f).setDuration(350).withEndAction {
                            splashLayout.visibility = View.GONE
                            webView.visibility = View.VISIBLE
                        }.start()
                    }, 800)
                }
                if (!pollingActive) { pollingActive = true; pollState(view) }
            }
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                if (url == null) return false
                return !(url.startsWith("https://web.whatsapp.com") ||
                         url.startsWith("https://whatsapp.com")     ||
                         url.startsWith("https://www.whatsapp.com"))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progressBar.progress = p
                progressBar.visibility = if (p == 100) View.GONE else View.VISIBLE
            }
            override fun onJsAlert(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean { r?.confirm(); return true }
            override fun onJsConfirm(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean { r?.confirm(); return true }
            override fun onJsPrompt(v: WebView?, u: String?, m: String?, d: String?, r: JsPromptResult?): Boolean { r?.confirm(""); return true }
            override fun onJsBeforeUnload(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean { r?.confirm(); return true }
        }

        webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && webView.canGoBack()) {
                webView.goBack(); true
            } else false
        }
    }

    // ── Master JS — CSS vars from whatsapp-web-mod + phone login auto-click ──
    private fun getMasterScript(): String = """
        (function() {
            if (window.__waMod) return;
            window.__waMod = true;

            // ── 1. CSS variables (from whatsapp-web-mod project) ──────────────
            // These are WhatsApp Web's own internal CSS variable names
            var s = document.createElement('style');
            s.id = '__wa_mod_css';
            s.textContent =
                /* Push below native top bar */
                'html { padding-top: 56px !important; }' +
                /* Hide WA Web's own header */
                'header { display:none !important; }' +
                /* Hide left icon rail */
                '._aigs, ._aigv { display:none !important; }' +
                /* Hide Windows promo */
                '[data-testid="intro-desktop-app-promo"] { display:none !important; }' +
                /* Hide doodles bg */
                '#main > div:first-child { opacity:0 !important; }' +
                /* Scrollbar */
                '::-webkit-scrollbar { display:none !important; }';
            if (!document.getElementById('__wa_mod_css')) document.head.appendChild(s);

            // ── 2. Apply CSS vars using whatsapp-web-mod technique ────────────
            function applyTheme() {
                var app = document.querySelector('.app-wrapper-web');
                if (!app) return;

                // Dark theme colors (from whatsapp-web-mod default config)
                app.style.setProperty('--outgoing-background', '#005C4B');
                app.style.setProperty('--incoming-background', '#1F2C34');
                app.style.setProperty('--system-message-background', '#1F2C34');
                app.style.setProperty('--message-primary', '#E9EDEF');
                app.style.setProperty('--bubble-meta', '#E9EDEF');
                app.style.setProperty('--panel-header-background', '#1F2C34');
                app.style.setProperty('--app-background-stripe', '#111B21');
                app.style.setProperty('--background-default', '#111B21');
                app.style.setProperty('--rich-text-panel-background', '#1F2C34');
                app.style.setProperty('--compose-input-background', '#2A3942');
                app.style.setProperty('--search-input-background', '#2A3942');
                app.style.setProperty('--doodles-opacity', '0');
                app.style.setProperty('--startup-background-rgb', '-');

                // Force dark mode (from themer.mod.ts)
                if (!document.body.classList.contains('dark')) document.body.classList.add('dark');
                if (document.body.classList.contains('light')) document.body.classList.remove('light');

                // Chat list background
                var side = document.querySelector('#side');
                if (side) side.style.background = 'rgba(0,0,0,0.27)';

                // Main area
                var main = document.querySelector('#main');
                if (main) main.style.background = 'transparent';

                // App background
                app.style.background = '#111B21';
            }

            // ── 3. Auto-click phone login ──────────────────────────────────────
            function clickPhone() {
                var sel = document.querySelector('[data-testid="link-device-phone-num-tab"]');
                if (sel) { sel.click(); return true; }
                var all = document.querySelectorAll('button,a,[role="button"],[role="tab"]');
                for (var i = 0; i < all.length; i++) {
                    if ((all[i].textContent || '').toLowerCase().indexOf('phone') >= 0) {
                        all[i].click(); return true;
                    }
                }
                return false;
            }

            // ── 4. Hide Windows promo dynamically ─────────────────────────────
            new MutationObserver(function() {
                document.querySelectorAll('[data-testid="intro-desktop-app-promo"],a[href*="windows.whatsapp"]')
                    .forEach(function(e) { (e.closest('div') || e).style.display = 'none'; });
                applyTheme();
            }).observe(document.documentElement, { childList: true, subtree: true });

            // Apply immediately + retry phone click
            applyTheme();
            var tries = 0;
            var t = setInterval(function() {
                applyTheme();
                if (clickPhone() || tries++ > 15) clearInterval(t);
            }, 800);

        })();
    """.trimIndent()

    // Poll page state → update native top bar
    private fun pollState(view: WebView) {
        if (!view.isAttachedToWindow) { pollingActive = false; return }
        val js = """
            (function(){
                var ct = document.querySelector('#main header span[dir="auto"]');
                if (ct && ct.textContent.trim()) return 'chat:' + ct.textContent.trim();
                if (document.getElementById('side')) return 'list';
                if (document.querySelector('input[type="tel"]')) return 'phone';
                return 'login';
            })()
        """.trimIndent()
        view.evaluateJavascript(js) { res ->
            val r = res?.trim('"') ?: "login"
            when {
                r.startsWith("chat:") -> setChatTopBar(r.removePrefix("chat:").take(22))
                r == "list"  -> setChatListTopBar()
                r == "phone" -> runOnUiThread { backBtn.visibility = View.VISIBLE }
                else         -> setDefaultTopBar()
            }
        }
        handler.postDelayed({ pollState(view) }, 800)
    }

    // Top bar states
    private fun setDefaultTopBar() = runOnUiThread {
        backBtn.visibility = View.GONE
        avatarTv.text = "W"; avatarTv.background = circleDrawable("#25D366", dpToPx(36))
        titleTv.text = "WhatsApp"; titleTv.setTextColor(Color.parseColor("#25D366"))
        subtitleTv.visibility = View.GONE
    }
    private fun setChatListTopBar() = runOnUiThread {
        backBtn.visibility = View.GONE
        avatarTv.text = "W"; avatarTv.background = circleDrawable("#25D366", dpToPx(36))
        titleTv.text = "WhatsApp"; titleTv.setTextColor(Color.parseColor("#25D366"))
        subtitleTv.visibility = View.GONE
    }
    private fun setChatTopBar(name: String) = runOnUiThread {
        backBtn.visibility = View.VISIBLE
        avatarTv.text = name.firstOrNull()?.uppercase() ?: "?"
        avatarTv.background = circleDrawable("#06CF9C", dpToPx(36))
        titleTv.text = name; titleTv.setTextColor(Color.parseColor("#111B21"))
        subtitleTv.text = "tap for info"; subtitleTv.visibility = View.VISIBLE
    }

    // Native top bar
    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(56))
            setBackgroundColor(Color.parseColor("#F0EBE3")); elevation = 2f
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setPadding(dpToPx(4), 0, dpToPx(12), 0)
        }
        backBtn = TextView(this).apply {
            text = "←"; textSize = 20f; setTextColor(Color.parseColor("#111B21"))
            gravity = Gravity.CENTER; setPadding(dpToPx(10), 0, dpToPx(6), 0)
            visibility = View.GONE
            setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        }
        val avatarWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).also {
                it.marginStart = dpToPx(10); it.marginEnd = dpToPx(10)
            }
        }
        avatarTv = TextView(this).apply {
            text = "W"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = circleDrawable("#25D366", dpToPx(36))
            layoutParams = FrameLayout.LayoutParams(dpToPx(36), dpToPx(36))
        }
        avatarWrap.addView(avatarTv)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        titleTv = TextView(this).apply {
            text = "WhatsApp"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#25D366"))
        }
        subtitleTv = TextView(this).apply {
            text = "tap for info"; textSize = 11f
            setTextColor(Color.parseColor("#667781")); visibility = View.GONE
        }
        col.addView(titleTv); col.addView(subtitleTv)
        val icons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        listOf("⟳","⌨","↓","?").forEach { ic ->
            icons.addView(TextView(this).apply {
                text = ic; textSize = 16f; setTextColor(Color.parseColor("#667781"))
                gravity = Gravity.CENTER; setPadding(dpToPx(6), 0, dpToPx(6), 0)
            })
        }
        row.addView(backBtn); row.addView(avatarWrap); row.addView(col); row.addView(icons)
        bar.addView(row)
        bar.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(1)).also { it.gravity = Gravity.BOTTOM }
            setBackgroundColor(Color.parseColor("#E0DDD8"))
        })
        return bar
    }

    private fun buildProgressBar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(2)).also { it.topMargin = dpToPx(56) }
            visibility = View.GONE
        }

    private fun buildSplashLayout(): FrameLayout {
        val splash = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#F0EBE3"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL).also { it.bottomMargin = dpToPx(80) }
        }
        col.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(80)).also { it.gravity = Gravity.CENTER_HORIZONTAL }
            addView(TextView(this@MainActivity).apply {
                text = "W"; textSize = 36f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = circleDrawable("#25D366", dpToPx(80))
                layoutParams = FrameLayout.LayoutParams(dpToPx(80), dpToPx(80))
            })
        })
        col.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dpToPx(24)) })
        col.addView(TextView(this).apply {
            text = "WhatsApp"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#25D366")); gravity = Gravity.CENTER
        })
        col.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dpToPx(8)) })
        col.addView(TextView(this).apply {
            text = "Simple. Reliable. Private."
            textSize = 13f; setTextColor(Color.parseColor("#667781")); gravity = Gravity.CENTER
        })
        splash.addView(col)
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).also { it.bottomMargin = dpToPx(36) }
        }
        bottom.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), dpToPx(2)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dpToPx(14)
            }
        })
        bottom.addView(TextView(this).apply {
            text = "from Meta"; textSize = 12f
            setTextColor(Color.parseColor("#667781")); gravity = Gravity.CENTER
        })
        splash.addView(bottom)
        return splash
    }

    private fun circleDrawable(hex: String, size: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); setSize(size, size)
    }
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); webView.saveState(outState) }
    override fun onRestoreInstanceState(s: Bundle) { super.onRestoreInstanceState(s); webView.restoreState(s) }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); webView.destroy(); super.onDestroy() }
}
