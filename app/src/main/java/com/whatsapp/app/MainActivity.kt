package com.whatsapp.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout

    private val PERMISSION_REQUEST = 100
    private val WHATSAPP_URL = "https://web.whatsapp.com"

    // Desktop Chrome user agent — WhatsApp Web properly load වෙනවා
    private val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.parseColor("#075E54")
        window.navigationBarColor = Color.TRANSPARENT

        setupUI()
        requestPermissions()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadWhatsApp()
        }
    }

    // ── UI Setup ───────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#075E54"))
        }

        // SwipeRefreshLayout
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#25D366"))
            setProgressBackgroundColorSchemeColor(Color.WHITE)
            setOnRefreshListener { webView.reload() }
        }

        // WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled          = true
                domStorageEnabled          = true
                databaseEnabled            = true
                userAgentString            = USER_AGENT
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess            = true
                allowContentAccess         = true
                setSupportZoom(false)
                displayZoomControls        = false
                useWideViewPort            = true
                loadWithOverviewMode       = true
                mixedContentMode           = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode                  = WebSettings.LOAD_DEFAULT
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient  = WhatsAppWebViewClient()
            webChromeClient = WhatsAppChromeClient()
        }

        swipeRefresh.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressDrawable = resources.getDrawable(android.R.drawable.progress_horizontal, theme)
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Error view
        errorView = buildErrorView()

        root.addView(swipeRefresh, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(progressBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 8
        ))
        root.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            visibility = View.GONE
            setPadding(48, 48, 48, 48)

            addView(TextView(this@MainActivity).apply {
                text = "📵"
                textSize = 64f
                gravity = android.view.Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "Internet සම්බන්ධතාව නැත"
                textSize = 18f
                setTextColor(Color.parseColor("#333333"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 8)
            })
            addView(TextView(this@MainActivity).apply {
                text = "WiFi හෝ Mobile Data on කරන්න"
                textSize = 13f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
            })

            // Retry button
            addView(android.widget.Button(this@MainActivity).apply {
                text = "නැවත උත්සාහ කරන්න"
                setBackgroundColor(Color.parseColor("#25D366"))
                setTextColor(Color.WHITE)
                setPadding(48, 24, 48, 24)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 32
                layoutParams = lp
                setOnClickListener {
                    errorView.visibility = View.GONE
                    loadWhatsApp()
                }
            })
        }
    }

    // ── Load WhatsApp ──────────────────────────────────────────
    private fun loadWhatsApp() {
        if (!isNetworkAvailable()) {
            showError()
            return
        }
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(WHATSAPP_URL)
    }

    private fun showError() {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false
    }

    // ── Network check ──────────────────────────────────────────
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── WebViewClient ──────────────────────────────────────────
    inner class WhatsAppWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false

            // Inject CSS — mobile screen fit + hide desktop elements
            val css = """
                * { -webkit-tap-highlight-color: transparent !important; }
                ::-webkit-scrollbar { display: none !important; }
                ._aly_  { display: none !important; }
                ._9tJc_ { display: none !important; }
            """.trimIndent().replace("\n", " ")

            view.evaluateJavascript(
                """(function(){
                    var s = document.createElement('style');
                    s.innerHTML = '$css';
                    document.head.appendChild(s);
                })();""", null
            )
        }

        override fun onReceivedError(
            view: WebView, request: WebResourceRequest, error: WebResourceError
        ) {
            if (request.isForMainFrame) showError()
        }

        // Only allow WhatsApp domains
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return if (url.contains("whatsapp.com") || url.contains("wa.me")) {
                false // let WebView handle it
            } else {
                true  // block other URLs
            }
        }
    }

    // ── ChromeClient — camera, mic, file upload ────────────────
    inner class WhatsAppChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            if (newProgress == 100) progressBar.visibility = View.GONE
        }

        // Camera / mic permission
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        // File chooser (media upload)
        private var fileCallback: ValueCallback<Array<android.net.Uri>>? = null

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<android.net.Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileCallback = filePathCallback
            val intent = fileChooserParams.createIntent()
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST)
            } catch (e: Exception) {
                fileCallback = null
                return false
            }
            return true
        }

        fun getFileCallback() = fileCallback
        fun clearFileCallback() { fileCallback = null }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val client = webView.webChromeClient as? WhatsAppChromeClient
            val callback = client?.getFileCallback()
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            client?.clearFileCallback()
        }
    }

    // ── Permissions ────────────────────────────────────────────
    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        val denied = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    // ── Back button ────────────────────────────────────────────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    // ── Save state ─────────────────────────────────────────────
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST = 1001
    }
}
