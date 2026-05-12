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
    private var phoneClickAttempts = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor     = Color.parseColor("#075E54")
        window.navigationBarColor = Color.parseColor("#075E54")

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#075E54"))
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

    // ─── BUILD WEBVIEW ────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
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
            // Desktop UA — required for WhatsApp Web to load properly
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        return wv
    }

    // ─── WEBVIEW CLIENTS ──────────────────────────────────────────────────
    private fun setupWebViewClients() {
        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String?, fav: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = View.GONE
                phoneClickAttempts = 0

                // Step 1: inject CSS to style the page
                view.evaluateJavascript(getCss(), null)

                // Step 2: after short delay, auto-click "Link with phone number"
                handler.postDelayed({ tryClickPhoneLogin(view) }, 2000)

                // Show webview, hide splash
                if (splashLayout.visibility == View.VISIBLE) {
                    handler.postDelayed({
                        splashLayout.animate().alpha(0f).setDuration(400).withEndAction {
                            splashLayout.visibility = View.GONE
                            webView.visibility = View.VISIBLE
                        }.start()
                    }, 500)
                }
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
                progressBar.progress   = p
                progressBar.visibility = if (p == 100) View.GONE else View.VISIBLE
            }

            // Suppress ALL JS dialogs — WhatsApp Web shows alert() on phone button
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

    // ─── AUTO CLICK PHONE LOGIN ───────────────────────────────────────────
    // Tries multiple selectors WhatsApp Web uses for the phone number button.
    // Retries up to 10 times (every 1s) in case React hasn't rendered it yet.
    private fun tryClickPhoneLogin(view: WebView) {
        if (phoneClickAttempts >= 10) return
        phoneClickAttempts++

        val js = """
            (function() {
                // All known selectors for WhatsApp Web phone login button
                var selectors = [
                    '[data-testid="link-device-phone-num-tab"]',
                    '[data-testid="link-device-phone-num-btn"]',
                    'button[class*="phone"]',
                    'a[href*="phone"]'
                ];

                // Also search by text content
                var allClickable = document.querySelectorAll('button, a, [role="button"], [role="tab"]');
                for (var i = 0; i < allClickable.length; i++) {
                    var el = allClickable[i];
                    var txt = (el.textContent || el.innerText || '').trim().toLowerCase();
                    if (txt.indexOf('phone') !== -1 || txt.indexOf('phone number') !== -1) {
                        selectors.push('#' + el.id);
                        el.click();
                        return 'clicked_by_text:' + txt.substring(0, 30);
                    }
                }

                for (var j = 0; j < selectors.length; j++) {
                    var btn = document.querySelector(selectors[j]);
                    if (btn) {
                        btn.click();
                        return 'clicked_by_selector:' + selectors[j];
                    }
                }
                return 'not_found';
            })()
        """.trimIndent()

        view.evaluateJavascript(js) { result ->
            val r = result?.trim('"') ?: "not_found"
            if (r == "not_found" && phoneClickAttempts < 10) {
                // Button not found yet — retry after 1 second
                handler.postDelayed({ tryClickPhoneLogin(view) }, 1000)
            }
            // If clicked successfully, update back button visibility
            if (r.startsWith("clicked")) {
                runOnUiThread {
                    backBtn.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun getCss(): String = """
        (function() {
            if (document.getElementById('_wa_css')) return;

            /* ── Viewport: make desktop page fit mobile screen ── */
            var vp = document.querySelector('meta[name=viewport]');
            if (!vp) { vp = document.createElement('meta'); vp.name='viewport'; document.head.appendChild(vp); }
            vp.content = 'width=device-width, initial-scale=0.82, maximum-scale=2.0';

            var s = document.createElement('style');
            s.id = '_wa_css';
            s.textContent = [
                /* Push below native 60dp top bar */
                'html { padding-top: 60px !important; background:#ECE5DD !important; }',
                'body { background:#ECE5DD !important; }',

                /* Hide WhatsApp Web's own header */
                'header, [data-testid="conversation-header"] { display:none !important; }',

                /* Hide Windows promo banner */
                '[data-testid="intro-desktop-app-promo"], a[href*="windows.whatsapp"] { display:none !important; }',

                /* ── Style the login landing card ── */
                /* Center the main login card on screen */
                '[data-testid="intro-md-beta-logo-dark"], [data-testid="intro-md-beta-logo-light"] { display:none !important; }',

                /* Make the QR/phone card look like a native card */
                '.landing-wrapper, [class*="landing"] {',
                '  display:flex !important; flex-direction:column !important;',
                '  align-items:center !important; justify-content:center !important;',
                '  min-height:calc(100vh - 60px) !important; padding:16px !important;',
                '}',

                /* Card styling */
                '[class*="intro-"] {',
                '  background:white !important; border-radius:20px !important;',
                '  box-shadow: 0 2px 20px rgba(0,0,0,0.10) !important;',
                '  padding: 28px 20px !important; width:100% !important;',
                '  max-width:380px !important;',
                '}',

                /* Phone number button — make it look like a proper CTA */
                '[data-testid="link-device-phone-num-tab"], a[class*="phone"] {',
                '  background: #25D366 !important; color: white !important;',
                '  border-radius: 28px !important; padding: 14px 28px !important;',
                '  font-size: 15px !important; font-weight: 600 !important;',
                '  display: inline-block !important; text-decoration: none !important;',
                '  border: none !important; cursor: pointer !important;',
                '  box-shadow: 0 4px 14px rgba(37,211,102,0.35) !important;',
                '}',

                /* Phone number input field */
                'input[type="tel"], input[type="text"], input[type="number"] {',
                '  border: 2px solid #25D366 !important; border-radius: 10px !important;',
                '  padding: 12px 16px !important; font-size: 16px !important;',
                '  width: 100% !important; outline: none !important;',
                '}',

                /* Submit/Next button */
                'button[type="submit"], [data-testid*="submit"], [data-testid*="next"] {',
                '  background: #075E54 !important; color: white !important;',
                '  border-radius: 28px !important; padding: 14px !important;',
                '  width: 100% !important; font-size: 15px !important;',
                '  font-weight: 600 !important; border: none !important;',
                '  margin-top: 16px !important; cursor: pointer !important;',
                '}',

                /* Country picker */
                'select, [class*="country"] {',
                '  border: 2px solid #e0e0e0 !important; border-radius: 10px !important;',
                '  padding: 12px !important; font-size: 15px !important;',
                '}',

                /* Scrollbar hide */
                '::-webkit-scrollbar { display: none !important; }'
            ].join(' ');
            document.head.appendChild(s);

            /* Hide Windows promo dynamically */
            new MutationObserver(function() {
                document.querySelectorAll(
                    '[data-testid="intro-desktop-app-promo"], a[href*="windows.whatsapp"]'
                ).forEach(function(el) {
                    (el.closest('div') || el).style.display = 'none';
                });
            }).observe(document.documentElement, { childList:true, subtree:true });
        })();
    """.trimIndent()

    // ─── NATIVE UI ────────────────────────────────────────────────────────
    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            gravity      = Gravity.CENTER_VERTICAL
            elevation    = 16f
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(60))
        }

        backBtn = TextView(this).apply {
            text       = "‹"
            textSize   = 32f
            setTextColor(Color.WHITE)
            gravity    = Gravity.CENTER
            setPadding(dpToPx(18), 0, dpToPx(4), dpToPx(2))
            visibility = View.GONE
            setOnClickListener {
                phoneClickAttempts = 0
                if (webView.canGoBack()) webView.goBack()
                backBtn.visibility = View.GONE
            }
        }

        // WhatsApp circle icon
        val iconWrapper = FrameLayout(this).apply {
            setPadding(dpToPx(16), 0, dpToPx(10), 0)
            layoutParams = LinearLayout.LayoutParams(dpToPx(66), MATCH)
        }
        val iconBg = TextView(this).apply {
            text      = "W"
            textSize  = 17f
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#075E54"))
            gravity   = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.WHITE)
                setSize(dpToPx(38), dpToPx(38))
            }
            layoutParams = FrameLayout.LayoutParams(dpToPx(38), dpToPx(38), Gravity.CENTER)
        }
        iconWrapper.addView(iconBg)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
        }
        val titleTv = TextView(this).apply {
            text     = "WhatsApp"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitleTv = TextView(this).apply {
            text     = "End-to-end encrypted"
            textSize = 11f
            setTextColor(Color.parseColor("#B2DFDB"))
        }
        textCol.addView(titleTv)
        textCol.addView(subtitleTv)

        // Three-dot menu icon (decorative)
        val menuBtn = TextView(this).apply {
            text     = "⋮"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity  = Gravity.CENTER
            setPadding(0, 0, dpToPx(16), 0)
        }

        bar.addView(backBtn)
        bar.addView(iconWrapper)
        bar.addView(textCol)
        bar.addView(menuBtn)
        return bar
    }

    private fun buildProgressBar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#25D366"))
            layoutParams = FrameLayout.LayoutParams(MATCH, dpToPx(3)).also {
                it.topMargin = dpToPx(60)
            }
            visibility = View.GONE
        }

    private fun buildSplashLayout(): FrameLayout {
        // Full-screen splash — WhatsApp official green with centered branding
        val splash = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#075E54"))
        }

        // Center column
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL).also {
                it.bottomMargin = dpToPx(80)
            }
        }

        // Big circular icon
        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(100), dpToPx(100)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        val iconCircle = TextView(this).apply {
            text      = "W"
            textSize  = 42f
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#075E54"))
            gravity   = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.WHITE)
                setSize(dpToPx(100), dpToPx(100))
            }
            layoutParams = FrameLayout.LayoutParams(dpToPx(100), dpToPx(100))
        }
        iconFrame.addView(iconCircle)
        col.addView(iconFrame)

        // App name
        col.addView(TextView(this).apply {
            text     = "WhatsApp"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity  = Gravity.CENTER
            setPadding(0, dpToPx(20), 0, dpToPx(8))
        })

        // Tagline
        col.addView(TextView(this).apply {
            text     = "Simple. Reliable. Private."
            textSize = 14f
            setTextColor(Color.parseColor("#B2DFDB"))
            gravity  = Gravity.CENTER
        })

        splash.addView(col)

        // Bottom section — "from Meta" + loading bar
        val bottomCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).also {
                it.bottomMargin = dpToPx(48)
            }
        }

        // Thin loading bar (WhatsApp style)
        val loadBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList
                .valueOf(Color.parseColor("#25D366"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(160), dpToPx(3)).also {
                it.gravity     = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dpToPx(20)
            }
        }

        val fromMeta = TextView(this).apply {
            text     = "from Meta"
            textSize = 13f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity  = Gravity.CENTER
        }

        bottomCol.addView(loadBar)
        bottomCol.addView(fromMeta)
        splash.addView(bottomCol)

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
        if (webView.canGoBack()) {
            webView.goBack()
            backBtn.visibility = View.GONE
        } else super.onBackPressed()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
