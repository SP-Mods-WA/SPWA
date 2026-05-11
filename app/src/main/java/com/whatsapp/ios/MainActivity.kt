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
import android.os.Handler
import android.os.Looper
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
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout

    private val URL = "https://web.whatsapp.com"

    // Android Mobile User Agent
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set status bar color
        window.statusBarColor = Color.parseColor("#075E54")
        
        requestPermissions()
        setupUI()
        
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                loadWhatsApp()
            }, 500)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupUI() {
        val root = RelativeLayout(this)
        root.setBackgroundColor(Color.parseColor("#075E54"))

        // Setup SwipeRefresh
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#25D366"))
            setOnRefreshListener {
                if (isOnline()) {
                    webView.reload()
                } else {
                    isRefreshing = false
                    showError()
                }
            }
        }

        // Setup WebView
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
                loadWithOverviewMode = true
            }
            
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = WhatsAppWebClient()
            webChromeClient = WhatsAppChromeClient()
            visibility = View.GONE
        }

        swipeRefresh.addView(webView)
        root.addView(swipeRefresh, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ))

        // Loading View
        loadingContainer = createLoadingView()
        root.addView(loadingContainer, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ))

        // Error View
        errorContainer = createErrorView()
        errorContainer.visibility = View.GONE
        root.addView(errorContainer, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    private fun createLoadingView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#075E54"))
            
            addView(TextView(context).apply {
                text = "💬"
                textSize = 80f
                gravity = Gravity.CENTER
            })
            
            addView(TextView(context).apply {
                text = "WhatsApp"
                textSize = 28f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(16), 0, 0)
            })
            
            addView(ProgressBar(context).apply {
                visibility = View.VISIBLE
                setPadding(0, dpToPx(32), 0, 0)
            })
            
            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            footer.addView(TextView(context).apply {
                text = "from Meta"
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(80), 0, dpToPx(40))
            })
            addView(footer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            })
        }
    }

    private fun createErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dpToPx(32), dpToPx(32), dpToPx(32), dpToPx(32))
            
            addView(TextView(context).apply {
                text = "🌐"
                textSize = 64f
                gravity = Gravity.CENTER
            })
            
            addView(TextView(context).apply {
                text = "Connection Error"
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(20), 0, dpToPx(8))
            })
            
            addView(TextView(context).apply {
                text = "Please check your internet connection"
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dpToPx(32))
            })
            
            addView(Button(context).apply {
                text = "RETRY"
                setBackgroundColor(Color.parseColor("#25D366"))
                setTextColor(Color.WHITE)
                textSize = 14f
                setOnClickListener {
                    errorContainer.visibility = View.GONE
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
        
        loadingContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        
        // Clear cache and load
        webView.clearCache(true)
        webView.loadUrl(URL)
    }

    private fun showError() {
        loadingContainer.visibility = View.GONE
        webView.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false
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
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needPermissions.toTypedArray(), 100)
        }
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
            // Keep loading visible
        }

        override fun onPageFinished(view: WebView, url: String) {
            loadingContainer.visibility = View.GONE
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
            
            // Allow WhatsApp related URLs
            if (url.contains("whatsapp.com") || url.contains("wa.me")) {
                return false
            }
            
            // Open external links in browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return true
        }
    }

    // WebChromeClient
    inner class WhatsAppChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }
        
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (newProgress == 100) {
                loadingContainer.visibility = View.GONE
            }
        }
    }
}
