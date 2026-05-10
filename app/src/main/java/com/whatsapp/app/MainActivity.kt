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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout

    private val PERMISSION_REQUEST = 100
    private val WHATSAPP_URL = "https://web.whatsapp.com"

    private val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.parseColor("#075E54")
        window.navigationBarColor = Color.TRANSPARENT

        setupUI()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        requestPermissions()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadWhatsApp()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

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
            // Pull-to-refresh disable — overscroll effect ඉවත් කරනවා
            overScrollMode = View.OVER_SCROLL_NEVER
            webViewClient   = WhatsAppWebViewClient()
            webChromeClient = WhatsAppChromeClient()
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }

        errorView = buildErrorView()

        root.addView(webView, FrameLayout.LayoutParams(
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

    private fun loadWhatsApp() {
        if (!isNetworkAvailable()) { showError(); return }
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(WHATSAPP_URL)
    }

    private fun showError() {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    inner class WhatsAppWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = View.GONE
            CookieManager.getInstance().flush()

            // Mobile screen fit + scrollbar hide + overscroll fix
            view.evaluateJavascript("""
                (function(){
                    // Viewport — phone screen width use කරනවා (desktop render, mobile scale)
                    var vw = window.screen.width;
                    var meta = document.querySelector('meta[name=viewport]');
                    if(meta){
                        meta.setAttribute('content',
                            'width=1024, initial-scale=' + (vw/1024).toFixed(3) +
                            ', maximum-scale=3.0, user-scalable=yes');
                    }
                    // CSS fixes
                    var s = document.createElement('style');
                    s.innerHTML =
                        '::-webkit-scrollbar{display:none!important}' +
                        'body{overflow-x:hidden!important;overscroll-behavior:none!important}' +
                        '*{-webkit-tap-highlight-color:transparent!important}';
                    document.head.appendChild(s);
                    // Body overscroll none
                    document.body.style.overscrollBehavior = 'none';
                })();
            """.trimIndent(), null)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showError()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return !(url.contains("whatsapp.com") || url.contains("wa.me"))
        }
    }

    inner class WhatsAppChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            if (newProgress == 100) progressBar.visibility = View.GONE
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        private var fileCallback: ValueCallback<Array<android.net.Uri>>? = null

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<android.net.Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileCallback = filePathCallback
            return try {
                startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST)
                true
            } catch (e: Exception) {
                fileCallback = null
                false
            }
        }

        fun getFileCallback() = fileCallback
        fun clearFileCallback() { fileCallback = null }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val client = webView.webChromeClient as? WhatsAppChromeClient
            client?.getFileCallback()?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            client?.clearFileCallback()
        }
    }

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
        if (denied.isNotEmpty())
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERMISSION_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume()  { super.onResume();  webView.onResume() }
    override fun onPause()   { super.onPause();   webView.onPause(); CookieManager.getInstance().flush() }
    override fun onDestroy() { CookieManager.getInstance().flush(); webView.destroy(); super.onDestroy() }

    companion object { private const val FILE_CHOOSER_REQUEST = 1001 }
}
