package com.whatsapp.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var splashView: FrameLayout
    private lateinit var splashStatusText: TextView
    private lateinit var splashDots: TextView

    // Native QR overlay (drawn on top of WebView)
    private var qrOverlay: FrameLayout? = null
    private lateinit var rootLayout: FrameLayout

    private var pageLoaded = false
    private val handler = Handler(Looper.getMainLooper())

    private val PERMISSION_REQUEST = 100
    private val WHATSAPP_URL = "https://web.whatsapp.com"
    private val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

    private var dotCount = 0
    private val dotsRunnable = object : Runnable {
        override fun run() {
            dotCount = (dotCount + 1) % 4
            splashDots.text = ".".repeat(dotCount)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.parseColor("#075E54")
        window.navigationBarColor = Color.parseColor("#075E54")

        setupUI()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        requestPermissions()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            hideSplash()
        } else {
            loadWhatsApp()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupUI() {
        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled                     = true
                domStorageEnabled                     = true
                databaseEnabled                       = true
                userAgentString                       = USER_AGENT
                mediaPlaybackRequiresUserGesture      = false
                allowFileAccess                       = true
                allowContentAccess                    = true
                setSupportZoom(true)
                builtInZoomControls                   = true
                displayZoomControls                   = false
                useWideViewPort                       = true
                loadWithOverviewMode                  = true
                mixedContentMode                      = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode                             = WebSettings.LOAD_DEFAULT
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode  = View.OVER_SCROLL_NEVER
            webViewClient   = WhatsAppWebViewClient()
            webChromeClient = WhatsAppChromeClient()
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
        }

        errorView   = buildErrorView()
        splashView  = buildSplashView()

        rootLayout.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(progressBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 8))
        rootLayout.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(splashView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(rootLayout)
    }

    // ── Native Android QR overlay ───────────────────────────────────────────
    // WhatsApp Web JS block කරනවා නිසා JS inject වෙන්නේ නැහැ.
    // ඒ නිසා WebView scroll position detect කරලා QR page එකට
    // native Android view overlay කරනවා.
    private fun showQROverlay() {
        if (qrOverlay != null) return

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        val scrollable = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.WHITE)
        }

        // ── Green header ──────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#075E54"))
            setPadding(0, dp(52), 0, dp(24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        header.addView(TextView(this).apply {
            text = "WhatsApp"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        container.addView(header)

        // ── Body content ──────────────────────────────────────────
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        body.addView(TextView(this).apply {
            text = "Log in to WhatsApp"
            textSize = 22f
            setTextColor(Color.parseColor("#111111"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.bottomMargin = dp(8)
            }
        })

        body.addView(TextView(this).apply {
            text = "Open WhatsApp on your phone\nand scan the QR code"
            textSize = 14f
            setTextColor(Color.parseColor("#667781"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.bottomMargin = dp(28)
            }
        })

        // QR placeholder card — WebView QR area visible කරන window
        // (transparent hole strategy — WebView behind, overlay above except hole)
        // Simple approach: just show instructions + phone number button
        // and let WebView QR show through a transparent region

        // QR area — transparent so WebView QR shows through
        val qrCard = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(256), dp(256)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(24)
            }
        }
        body.addView(qrCard)
        container.addView(body)

        // ── Phone number button ───────────────────────────────────
        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), 0, dp(24), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val phoneBtn = TextView(this).apply {
            text = "📱  Link with phone number"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#25D366"))
                cornerRadius = dp(28).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.bottomMargin = dp(16)
            }
            // Click forward to WebView's phone number button via JS
            setOnClickListener {
                webView.evaluateJavascript(
                    "document.querySelector('[data-testid=\"link-device-phone-number-method-button\"]')?.click();",
                    null
                )
                // Remove overlay so WebView phone login page shows
                removeQROverlay()
            }
        }
        btnContainer.addView(phoneBtn)

        btnContainer.addView(TextView(this).apply {
            text = "🔒  End-to-end encrypted"
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.bottomMargin = dp(32)
            }
        })
        container.addView(btnContainer)

        scrollable.addView(container, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlay.addView(scrollable, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        qrOverlay = overlay
        rootLayout.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun removeQROverlay() {
        qrOverlay?.let {
            rootLayout.removeView(it)
            qrOverlay = null
        }
    }

    // Detect QR page vs chat page from URL/title
    private fun isQRPage(url: String): Boolean {
        return url.contains("web.whatsapp.com") &&
               !url.contains("/app") &&
               url == "https://web.whatsapp.com/" || url == "https://web.whatsapp.com"
    }

    // ── Splash ─────────────────────────────────────────────────────────────

    private fun buildSplashView(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#075E54"))

            val center = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER

                addView(FrameLayout(this@MainActivity).apply {
                    val size = dp(90)
                    layoutParams = LinearLayout.LayoutParams(size, size).also {
                        it.gravity = Gravity.CENTER_HORIZONTAL
                        it.bottomMargin = dp(20)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#128C7E"))
                    }
                    addView(TextView(this@MainActivity).apply {
                        text = "✉"
                        textSize = 40f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT)
                    })
                })

                addView(TextView(this@MainActivity).apply {
                    text = "WhatsApp"
                    textSize = 30f
                    setTextColor(Color.WHITE)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(8))
                })

                splashStatusText = TextView(this@MainActivity).apply {
                    text = "Loading your chats"
                    textSize = 15f
                    setTextColor(Color.parseColor("#B2DFDB"))
                    gravity = Gravity.CENTER
                }
                addView(splashStatusText)

                splashDots = TextView(this@MainActivity).apply {
                    text = ""
                    textSize = 15f
                    setTextColor(Color.parseColor("#B2DFDB"))
                    gravity = Gravity.CENTER
                }
                addView(splashDots)
            }

            addView(center, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT).also { it.gravity = Gravity.CENTER })

            addView(TextView(this@MainActivity).apply {
                text = "🔒  End-to-end encrypted"
                textSize = 12f
                setTextColor(Color.parseColor("#80CBC4"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(48))
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            })
        }
    }

    private fun hideSplash() {
        handler.removeCallbacks(dotsRunnable)
        if (splashView.visibility == View.GONE) return
        splashView.animate().alpha(0f).setDuration(400)
            .withEndAction { splashView.visibility = View.GONE; splashView.alpha = 1f }.start()
    }

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#075E54"))
            visibility = View.GONE
            setPadding(dp(32), dp(32), dp(32), dp(32))

            addView(TextView(this@MainActivity).apply {
                text = "📵"; textSize = 64f; gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "Internet සම්බන්ධතාව නැත"
                textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(8))
            })
            addView(TextView(this@MainActivity).apply {
                text = "WiFi හෝ Mobile Data on කරන්න"
                textSize = 13f; setTextColor(Color.parseColor("#B2DFDB")); gravity = Gravity.CENTER
            })
            addView(Button(this@MainActivity).apply {
                text = "නැවත උත්සාහ කරන්න"
                setBackgroundColor(Color.parseColor("#25D366")); setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(24); it.gravity = Gravity.CENTER_HORIZONTAL }
                setOnClickListener { errorView.visibility = View.GONE; loadWhatsApp() }
            })
        }
    }

    private fun loadWhatsApp() {
        if (!isNetworkAvailable()) { showError(); return }
        errorView.visibility = View.GONE
        splashStatusText.text = "Loading your chats"
        handler.post(dotsRunnable)
        webView.loadUrl(WHATSAPP_URL)
    }

    private fun showError() {
        handler.removeCallbacks(dotsRunnable)
        splashView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── WebView Clients ─────────────────────────────────────────────────────

    inner class WhatsAppWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = View.GONE
            CookieManager.getInstance().flush()

            // Base CSS inject — viewport + scrollbar
            view.evaluateJavascript("""
                (function(){
                    var m=document.querySelector('meta[name=viewport]');
                    if(m) m.setAttribute('content',
                        'width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no');
                    if(!document.getElementById('wa-base')){
                        var s=document.createElement('style');
                        s.id='wa-base';
                        s.textContent='::-webkit-scrollbar{display:none!important}'+
                        'body{overflow-x:hidden!important;overscroll-behavior:none!important;}'+
                        '*{-webkit-tap-highlight-color:transparent!important}';
                        document.head.appendChild(s);
                    }
                })();
            """.trimIndent(), null)

            // QR page detect — show native overlay
            handler.postDelayed({
                view.evaluateJavascript("""
                    (function(){
                        var qr = document.querySelector('canvas[aria-label="Scan me!"]')
                            || document.querySelector('[data-testid="qrcode"] canvas');
                        var phone = document.querySelector('[data-testid="link-device-phone-number-method-button"]');
                        return (qr || phone) ? 'qr' : 'chat';
                    })();
                """.trimIndent()) { result ->
                    if (result?.contains("qr") == true) {
                        showQROverlay()
                    } else {
                        removeQROverlay()
                    }
                }
            }, 800)

            if (!pageLoaded) {
                pageLoaded = true
                handler.postDelayed({ hideSplash() }, 1200)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showError()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            // Remove QR overlay when navigating away from QR page
            if (!url.contains("web.whatsapp.com")) return true
            removeQROverlay()
            return false
        }
    }

    inner class WhatsAppChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            if (newProgress == 100) progressBar.visibility = View.GONE
        }
        override fun onPermissionRequest(request: PermissionRequest) { request.grant(request.resources) }

        private var fileCallback: ValueCallback<Array<android.net.Uri>>? = null
        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<android.net.Uri>>, fileChooserParams: FileChooserParams): Boolean {
            fileCallback = filePathCallback
            return try { startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST); true }
            catch (e: Exception) { fileCallback = null; false }
        }
        fun getFileCallback() = fileCallback
        fun clearFileCallback() { fileCallback = null }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val c = webView.webChromeClient as? WhatsAppChromeClient
            c?.getFileCallback()?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            c?.clearFileCallback()
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val denied = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (denied.isNotEmpty()) ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERMISSION_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); webView.saveState(outState) }
    override fun onResume()  { super.onResume();  webView.onResume() }
    override fun onPause()   { super.onPause();   webView.onPause(); CookieManager.getInstance().flush() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); CookieManager.getInstance().flush(); webView.destroy(); super.onDestroy() }

    companion object { private const val FILE_CHOOSER_REQUEST = 1001 }
}
