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

    // ─── Android WhatsApp Web User Agent (matches screenshot - GBWhatsApp style)
    private val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

    // ─── Colors matching the screenshot exactly
    private val COLOR_GREEN        = "#25D366"   // WhatsApp green (header title)
    private val COLOR_HEADER_BG    = "#FFFFFF"   // White header background
    private val COLOR_BG           = "#F0F2F5"   // Chat list background
    private val COLOR_UNREAD_BADGE = "#25D366"   // Green badge
    private val COLOR_UNREAD_TIME  = "#25D366"   // Green time for unread
    private val COLOR_TEXT_PRIMARY = "#111B21"   // Dark text
    private val COLOR_TEXT_SEC     = "#667781"   // Secondary grey text
    private val COLOR_BORDER       = "#E9EDEF"   // Divider color
    private val COLOR_HIGHLIGHT    = "#F5F6F6"   // Selected chat highlight
    private val COLOR_FILTER_ACTIVE= "#D9FDD3"   // Active filter chip bg
    private val COLOR_ICON         = "#54656F"   // Icon grey

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ─── Edge-to-edge like WhatsApp Android
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor  = Color.parseColor(COLOR_HEADER_BG)
        window.navigationBarColor = Color.parseColor(COLOR_BG)

        // Light status bar icons (dark icons on white bg)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        prefs = getSharedPreferences("gb_prefs", Context.MODE_PRIVATE)
        requestPerms()
        buildUI()

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else loadWhatsApp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(COLOR_BG))
        }

        // ─── SwipeRefresh
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor(COLOR_GREEN))
            setProgressBackgroundColorSchemeColor(Color.WHITE)
            setOnRefreshListener { webView.reload() }
        }

        // ─── WebView - loads web.whatsapp.com with Android UA
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                databaseEnabled                  = true
                userAgentString                  = USER_AGENT
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess                  = true
                allowContentAccess               = true
                setSupportZoom(true)
                builtInZoomControls              = true
                displayZoomControls              = false
                useWideViewPort                  = true
                loadWithOverviewMode             = true
                mixedContentMode                 = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode                        = WebSettings.LOAD_DEFAULT
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient   = WaWebViewClient()
            webChromeClient = WaChromeClient()
        }

        swipeRefresh.addView(webView)
        root.addView(swipeRefresh, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // ─── Loading screen (Android WhatsApp splash)
        loadingView = buildLoadingView()
        root.addView(loadingView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // ─── Error view
        errorView = buildErrorView()
        errorView.visibility = View.GONE
        root.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // ─── GB Settings FAB (top-right, matches screenshot ⋮ menu area)
        val gbBtn = buildGBButton()
        root.addView(gbBtn, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, 36.dp,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin   = 52.dp
            rightMargin = 12.dp
        })

        setContentView(root)
    }

    // ─────────────────────────────────────────────────────────────
    // Loading Screen  — Android WhatsApp green splash
    // ─────────────────────────────────────────────────────────────
    private fun buildLoadingView(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(COLOR_GREEN))

            // Centre: WhatsApp logo + name
            val centre = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
            }
            // WhatsApp icon circle
            val iconWrapper = FrameLayout(this@MainActivity).apply {
                val size = 80.dp
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape       = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
            }
            val icon = TextView(this@MainActivity).apply {
                text     = "💬"
                textSize = 36f
                gravity  = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            iconWrapper.addView(icon)
            centre.addView(iconWrapper)

            centre.addView(TextView(this@MainActivity).apply {
                text      = "WhatsApp"
                textSize  = 26f
                typeface  = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity   = Gravity.CENTER
                setPadding(0, 20.dp, 0, 0)
            })

            addView(centre, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))

            // Bottom: "from Meta"
            val metaLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                setPadding(0, 0, 0, 48.dp)
            }
            metaLayout.addView(TextView(this@MainActivity).apply {
                text      = "from"
                textSize  = 13f
                setTextColor(Color.argb(200, 255, 255, 255))
                gravity   = Gravity.CENTER
            })
            metaLayout.addView(TextView(this@MainActivity).apply {
                text      = "Meta"
                textSize  = 15f
                typeface  = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity   = Gravity.CENTER
            })
            addView(metaLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Error View  — clean Android style
    // ─────────────────────────────────────────────────────────────
    private fun buildErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setBackgroundColor(Color.parseColor(COLOR_BG))
            setPadding(48.dp, 48.dp, 48.dp, 48.dp)

            addView(TextView(this@MainActivity).apply {
                text     = "📵"
                textSize = 56f
                gravity  = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text      = "No Internet Connection"
                textSize  = 20f
                typeface  = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY))
                gravity   = Gravity.CENTER
                setPadding(0, 20.dp, 0, 8.dp)
            })
            addView(TextView(this@MainActivity).apply {
                text      = "Turn on Wi-Fi or Mobile Data and try again."
                textSize  = 14f
                setTextColor(Color.parseColor(COLOR_TEXT_SEC))
                gravity   = Gravity.CENTER
                setPadding(0, 0, 0, 32.dp)
            })
            addView(Button(this@MainActivity).apply {
                text = "Retry"
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor(COLOR_GREEN))
                    cornerRadius = 24f.dp
                }
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(40.dp, 0, 40.dp, 0)
                setOnClickListener {
                    errorView.visibility = View.GONE
                    loadWhatsApp()
                }
            })
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GB Settings Button  — pill style, top-right
    // ─────────────────────────────────────────────────────────────
    private fun buildGBButton(): TextView {
        return TextView(this).apply {
            text      = "⚡ GB"
            textSize  = 11f
            setTextColor(Color.WHITE)
            gravity   = Gravity.CENTER
            setPadding(14.dp, 0, 14.dp, 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_GREEN))
                cornerRadius = 18f.dp
            }
            elevation = 4f.dp.toFloat()
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // JS Injection — injects Android-native WhatsApp look + GB features
    // ─────────────────────────────────────────────────────────────
    private fun injectFeatures() {
        val antiDelete    = prefs.getBoolean("anti_delete",  true)
        val hideBlueCheck = prefs.getBoolean("hide_blue",    false)

        // Inject CSS to make web.whatsapp.com look like the Android app screenshot
        val css = """
            /* Hide WhatsApp Web desktop chrome */
            ._aigs, ._aigw { display:none!important; }

            /* Header: white bg, green title */
            header, [data-testid="chatlist-header"] {
                background:#FFFFFF!important;
                box-shadow: 0 1px 3px rgba(0,0,0,0.12)!important;
            }

            /* Chat list background */
            #pane-side, [data-testid="chat-list"] {
                background:#F0F2F5!important;
            }

            /* Chat list item */
            [data-testid="cell-frame-container"] {
                background:#FFFFFF!important;
                border-bottom:1px solid #E9EDEF!important;
            }
            [data-testid="cell-frame-container"]:active {
                background:#F5F6F6!important;
            }

            /* Unread badge — green */
            [data-testid="icon-unread-count"] span,
            .x1sxyh0 .x6s0dn4 span {
                background:#25D366!important;
                color:#fff!important;
                font-size:11px!important;
                min-width:20px!important;
                height:20px!important;
                border-radius:10px!important;
            }

            /* Search bar */
            [data-testid="search-bar-input-container"] {
                background:#F0F2F5!important;
                border-radius:8px!important;
                margin:8px 12px!important;
            }

            /* Filter chips (All / Unread / Favorites / Groups) */
            [data-testid="filter-tab-active"] {
                background:#D9FDD3!important;
                color:#25D366!important;
                border:none!important;
                border-radius:16px!important;
            }

            /* Scrollbar */
            ::-webkit-scrollbar { width:3px; }
            ::-webkit-scrollbar-thumb { background:#25D366; border-radius:3px; }

            /* Double-tick colour */
            [data-testid="msg-dblcheck"] { color:#53BDEB!important; }
        """.trimIndent()

        val script = buildString {
            // Apply CSS
            append("""
                var st=document.createElement('style');
                st.id='gb-android-style';
                st.textContent=`$css`;
                document.head.appendChild(st);
            """.trimIndent())

            // Anti-delete
            if (antiDelete) append("""
                window._gbAntiDelete=true;
                document.addEventListener('DOMSubtreeModified',function(){
                    document.querySelectorAll('[data-testid="recalled-msg"]').forEach(function(e){
                        e.style.display='none';
                    });
                });
            """.trimIndent())

            // Hide blue ticks
            if (hideBlueCheck) append("""
                var bs=document.createElement('style');
                bs.textContent='[data-testid="msg-dblcheck"]{filter:grayscale(1)!important;}';
                document.head.appendChild(bs);
            """.trimIndent())
        }

        webView.evaluateJavascript("(function(){$script})();", null)
    }

    // ─────────────────────────────────────────────────────────────
    // Load
    // ─────────────────────────────────────────────────────────────
    private fun loadWhatsApp() {
        if (!isOnline()) { showError(); return }
        errorView.visibility   = View.GONE
        webView.visibility     = View.VISIBLE
        webView.loadUrl(URL)
    }

    // ─────────────────────────────────────────────────────────────
    // WebViewClient
    // ─────────────────────────────────────────────────────────────
    inner class WaWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            loadingView.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            loadingView.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            // Inject after DOM settles
            webView.postDelayed({ injectFeatures() }, 1500)
        }

        override fun onReceivedError(
            view: WebView, req: WebResourceRequest, err: WebResourceError
        ) {
            if (req.isForMainFrame) showError()
        }

        override fun shouldOverrideUrlLoading(
            view: WebView, req: WebResourceRequest
        ): Boolean {
            val url = req.url.toString()
            return !url.contains("whatsapp.com") && !url.contains("wa.me")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ChromeClient
    // ─────────────────────────────────────────────────────────────
    inner class WaChromeClient : WebChromeClient() {

        override fun onPermissionRequest(req: PermissionRequest) =
            req.grant(req.resources)

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

        fun getFileCb()  = fileCallback
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

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private fun showError() {
        webView.visibility     = View.GONE
        loadingView.visibility = View.GONE
        errorView.visibility   = View.VISIBLE
    }

    private fun isOnline(): Boolean {
        val cm   = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun requestPerms() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (perms.isNotEmpty())
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    private val Float.dp get() = (this * resources.displayMetrics.density)
    private val Int.dp   get() = (this * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        webView.saveState(out)
    }

    override fun onResume()  { super.onResume();  webView.onResume();  injectFeatures() }
    override fun onPause()   { super.onPause();   webView.onPause() }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
