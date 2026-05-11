package com.whatsapp.ios

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingView: FrameLayout
    private lateinit var errorView: LinearLayout
    private lateinit var prefs: SharedPreferences

    private val URL = "https://web.whatsapp.com"

    // iOS iPhone 15 Pro user agent
    private val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
        "Version/17.4 Mobile/15E148 Safari/604.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.parseColor("#34C759")   // iOS green
        window.navigationBarColor = Color.parseColor("#F2F2F7")

        prefs = getSharedPreferences("gb_prefs", Context.MODE_PRIVATE)
        requestPerms()
        buildUI()

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else loadWhatsApp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#34C759"))
        }

        // SwipeRefresh
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#34C759"))
            setOnRefreshListener { webView.reload() }
        }

        // WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled               = true
                domStorageEnabled               = true
                databaseEnabled                 = true
                userAgentString                 = USER_AGENT
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess                 = true
                allowContentAccess              = true
                setSupportZoom(false)
                displayZoomControls             = false
                useWideViewPort                 = true
                loadWithOverviewMode            = true
                mixedContentMode                = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode                       = WebSettings.LOAD_DEFAULT
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient  = WaWebViewClient()
            webChromeClient = WaChromeClient()
        }

        swipeRefresh.addView(webView)
        root.addView(swipeRefresh, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Loading screen - iOS style splash
        loadingView = buildLoadingView()
        root.addView(loadingView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Error view
        errorView = buildErrorView()
        errorView.visibility = View.GONE
        root.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // GB Menu button (floating)
        val gbBtn = buildGBButton()
        root.addView(gbBtn, FrameLayout.LayoutParams(
            160.dp, 40.dp, Gravity.TOP or Gravity.END
        ).apply { topMargin = 56.dp; rightMargin = 8.dp })

        setContentView(root)
    }

    private fun buildLoadingView(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#34C759"))
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            col.addView(TextView(this@MainActivity).apply {
                text = "💬"
                textSize = 72f
                gravity = Gravity.CENTER
            })
            col.addView(TextView(this@MainActivity).apply {
                text = "WhatsApp"
                textSize = 24f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 16.dp, 0, 0)
            })
            addView(col, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            // "from Meta" bottom
            val meta = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 40.dp)
            }
            meta.addView(TextView(this@MainActivity).apply {
                text = "from"
                textSize = 13f
                setTextColor(Color.WHITE.withAlpha(180))
                gravity = Gravity.CENTER
            })
            meta.addView(TextView(this@MainActivity).apply {
                text = "Meta"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(meta, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }
    }

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(48.dp, 48.dp, 48.dp, 48.dp)

            addView(TextView(this@MainActivity).apply {
                text = "📵"
                textSize = 56f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "No Internet Connection"
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                setPadding(0, 20.dp, 0, 8.dp)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Turn on Wi-Fi or Cellular Data and try again."
                textSize = 14f
                setTextColor(Color.parseColor("#8E8E93"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32.dp)
            })
            addView(Button(this@MainActivity).apply {
                text = "Try Again"
                setBackgroundColor(Color.parseColor("#007AFF"))
                setTextColor(Color.WHITE)
                setPadding(40.dp, 12.dp, 40.dp, 12.dp)
                textSize = 16f
                setOnClickListener {
                    errorView.visibility = View.GONE
                    loadWhatsApp()
                }
            })
        }
    }

    private fun buildGBButton(): TextView {
        return TextView(this).apply {
            text = "⚡ GB Settings"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#34C759"))
            gravity = Gravity.CENTER
            setPadding(12.dp, 6.dp, 12.dp, 6.dp)
            // Rounded
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#34C759"))
                cornerRadius = 20f
            }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }

    // ── Load WhatsApp ──────────────────────────────────────────
    private fun loadWhatsApp() {
        if (!isOnline()) { showError(); return }
        errorView.visibility = View.GONE
        webView.visibility   = View.VISIBLE
        webView.loadUrl(URL)
    }

    // ── Inject GB features after page load ────────────────────
    private fun injectFeatures() {
        val script = GBFeatures.buildInjectionScript(
            antiDelete    = prefs.getBoolean("anti_delete", true),
            iosTheme      = prefs.getBoolean("ios_theme",   true),
            hideBlueCheck = prefs.getBoolean("hide_blue",   false),
        )
        webView.evaluateJavascript("(function(){$script})();", null)
    }

    // ── WebViewClient ──────────────────────────────────────────
    inner class WaWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            loadingView.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            loadingView.visibility = View.GONE
            swipeRefresh.isRefreshing = false

            // Inject GB features
            webView.postDelayed({ injectFeatures() }, 1500)
        }

        override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
            if (req.isForMainFrame) showError()
        }

        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            val url = req.url.toString()
            return !url.contains("whatsapp.com") && !url.contains("wa.me")
        }
    }

    // ── ChromeClient ───────────────────────────────────────────
    inner class WaChromeClient : WebChromeClient() {
        override fun onPermissionRequest(req: PermissionRequest) = req.grant(req.resources)

        private var fileCallback: ValueCallback<Array<android.net.Uri>>? = null

        override fun onShowFileChooser(
            wv: WebView,
            cb: ValueCallback<Array<android.net.Uri>>,
            params: FileChooserParams
        ): Boolean {
            fileCallback = cb
            return try {
                startActivityForResult(params.createIntent(), 1001)
                true
            } catch (e: Exception) {
                fileCallback = null; false
            }
        }
        fun getFileCb() = fileCallback
        fun clearFileCb() { fileCallback = null }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1001) {
            val cb = (webView.webChromeClient as? WaChromeClient)?.getFileCb()
            cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
            (webView.webChromeClient as? WaChromeClient)?.clearFileCb()
        }
    }

    // ── Helpers ────────────────────────────────────────────────
    private fun showError() {
        webView.visibility   = View.GONE
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun requestPerms() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    private fun Color.withAlpha(a: Int) = Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); webView.saveState(out) }
    override fun onResume()  { super.onResume();  webView.onResume();  injectFeatures() }
    override fun onPause()   { super.onPause();   webView.onPause() }
    override fun onDestroy() { webView.destroy();  super.onDestroy() }
}
