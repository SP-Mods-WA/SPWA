package com.whatsapp.ios

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingView: FrameLayout
    private lateinit var errorView: LinearLayout

    private val URL = "https://web.whatsapp.com"

    // Mobile User Agent (iPhone style - mobile view)
    private val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/17.4 Mobile/15E148 Safari/604.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.statusBarColor = Color.parseColor("#075E54")
        window.navigationBarColor = Color.parseColor("#111B12")

        requestPermissions()
        setupUI()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadWhatsApp()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#075E54"))
        }

        // SwipeRefresh
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#25D366"))
            setOnRefreshListener { refreshWebView() }
        }

        // WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = USER_AGENT
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = WhatsAppWebClient()
            webChromeClient = WhatsAppChromeClient()
        }

        swipeRefresh.addView(webView)
        root.addView(swipeRefresh, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Loading View
        loadingView = createLoadingView()
        root.addView(loadingView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Error View
        errorView = createErrorView()
        errorView.visibility = View.GONE
        root.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    private fun createLoadingView(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#075E54"))
            
            val container = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            
            container.addView(TextView(this@MainActivity).apply {
                text = "💬"
                textSize = 80f
                gravity = Gravity.CENTER
            })
            
            container.addView(TextView(this@MainActivity).apply {
                text = "WhatsApp"
                textSize = 28f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(16), 0, 0)
            })
            
            addView(container, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            
            val footer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dpToPx(40))
            }
            footer.addView(TextView(this@MainActivity).apply {
                text = "from"
                textSize = 12f
                setTextColor(Color.WHITE.withAlpha(180))
                gravity = Gravity.CENTER
            })
            footer.addView(TextView(this@MainActivity).apply {
                text = "Meta"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(footer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }
    }

    private fun createErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(dpToPx(48), dpToPx(48), dpToPx(48), dpToPx(48))
            
            addView(TextView(this@MainActivity).apply {
                text = "📵"
                textSize = 64f
                gravity = Gravity.CENTER
            })
            
            addView(TextView(this@MainActivity).apply {
                text = "No Internet Connection"
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(20), 0, dpToPx(8))
            })
            
            addView(TextView(this@MainActivity).apply {
                text = "Turn on Wi-Fi or Mobile Data and try again"
                textSize = 14f
                setTextColor(Color.parseColor("#8E8E93"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dpToPx(32))
            })
            
            addView(Button(this@MainActivity).apply {
                text = "Try Again"
                setBackgroundColor(Color.parseColor("#25D366"))
                setTextColor(Color.WHITE)
                textSize = 16f
                setOnClickListener {
                    errorView.visibility = View.GONE
                    loadWhatsApp()
                }
            })
        }
    }

    private fun loadWhatsApp() {
        if (!isOnline()) {
            showError()
            return
        }
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(URL)
    }

    private fun refreshWebView() {
        if (isOnline()) {
            webView.reload()
        } else {
            swipeRefresh.isRefreshing = false
            showError()
        }
    }

    private fun showError() {
        webView.visibility = View.GONE
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        if (swipeRefresh.isRefreshing) {
            swipeRefresh.isRefreshing = false
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val netInfo = cm.activeNetworkInfo ?: return false
            return netInfo.isConnected
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val needPerms = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needPerms.toTypedArray(), 100)
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        webView.saveState(out)
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

    // WebViewClient
    inner class WhatsAppWebClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            loadingView.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            loadingView.visibility = View.GONE
            swipeRefresh.isRefreshing = false
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                showError()
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return if (url.contains("whatsapp.com") || url.contains("wa.me")) {
                false
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (_: Exception) { }
                true
            }
        }
    }

    // WebChromeClient
    inner class WhatsAppChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }
    }
}
