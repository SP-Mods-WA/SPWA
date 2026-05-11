package com.whatsapp.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
            overScrollMode  = View.OVER_SCROLL_NEVER
            webViewClient   = WhatsAppWebViewClient()
            webChromeClient = WhatsAppChromeClient()
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }

        errorView = buildErrorView()
        splashView = buildSplashView()

        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(progressBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 8))
        root.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(splashView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun buildSplashView(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#075E54"))

            val centerLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER

                val iconBg = FrameLayout(this@MainActivity).apply {
                    val size = dpToPx(90)
                    layoutParams = LinearLayout.LayoutParams(size, size).also {
                        it.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        it.bottomMargin = dpToPx(20)
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(Color.parseColor("#128C7E"))
                    }
                    addView(TextView(this@MainActivity).apply {
                        text = "✉"
                        textSize = 40f
                        setTextColor(Color.WHITE)
                        gravity = android.view.Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT)
                    })
                }
                addView(iconBg)

                addView(TextView(this@MainActivity).apply {
                    text = "WhatsApp"
                    textSize = 30f
                    setTextColor(Color.WHITE)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(8))
                })

                splashStatusText = TextView(this@MainActivity).apply {
                    text = "Loading your chats"
                    textSize = 15f
                    setTextColor(Color.parseColor("#B2DFDB"))
                    gravity = android.view.Gravity.CENTER
                }
                addView(splashStatusText)

                val dotsRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
                splashDots = TextView(this@MainActivity).apply {
                    text = ""
                    textSize = 15f
                    setTextColor(Color.parseColor("#B2DFDB"))
                    gravity = android.view.Gravity.CENTER
                }
                dotsRow.addView(splashDots)
                addView(dotsRow)
            }

            addView(centerLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT).also {
                it.gravity = android.view.Gravity.CENTER
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, dpToPx(48))
                addView(TextView(this@MainActivity).apply {
                    text = "🔒  End-to-end encrypted"
                    textSize = 12f
                    setTextColor(Color.parseColor("#80CBC4"))
                    gravity = android.view.Gravity.CENTER
                })
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            })
        }
    }

    private fun hideSplash() {
        handler.removeCallbacks(dotsRunnable)
        if (splashView.visibility == View.GONE) return
        splashView.animate()
            .alpha(0f).setDuration(400)
            .withEndAction { splashView.visibility = View.GONE; splashView.alpha = 1f }
            .start()
    }

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#075E54"))
            visibility = View.GONE
            setPadding(dpToPx(32), dpToPx(32), dpToPx(32), dpToPx(32))

            addView(TextView(this@MainActivity).apply {
                text = "📵"; textSize = 64f
                gravity = android.view.Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "Internet සම්බන්ධතාව නැත"
                textSize = 18f; setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setPadding(0, dpToPx(16), 0, dpToPx(8))
            })
            addView(TextView(this@MainActivity).apply {
                text = "WiFi හෝ Mobile Data on කරන්න"
                textSize = 13f; setTextColor(Color.parseColor("#B2DFDB"))
                gravity = android.view.Gravity.CENTER
            })
            addView(android.widget.Button(this@MainActivity).apply {
                text = "නැවත උත්සාහ කරන්න"
                setBackgroundColor(Color.parseColor("#25D366"))
                setTextColor(Color.WHITE)
                setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpToPx(24); it.gravity = android.view.Gravity.CENTER_HORIZONTAL }
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

    inner class WhatsAppWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = View.GONE
            CookieManager.getInstance().flush()
            injectUI(view)
            if (!pageLoaded) {
                pageLoaded = true
                handler.postDelayed({ hideSplash() }, 1200)
            }
        }

        private fun injectUI(view: WebView) {
            val js = """
                (function tryInject() {
                    var meta = document.querySelector('meta[name=viewport]');
                    if(meta) meta.setAttribute('content',
                        'width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no');

                    if(!document.getElementById('wa-base-style')) {
                        var s = document.createElement('style');
                        s.id = 'wa-base-style';
                        s.textContent =
                            '::-webkit-scrollbar{display:none!important}' +
                            'body{overflow-x:hidden!important;overscroll-behavior:none!important;margin:0!important;padding:0!important;}' +
                            '*{-webkit-tap-highlight-color:transparent!important;box-sizing:border-box!important}';
                        document.head.appendChild(s);
                    }

                    if(document.getElementById('wa-native-ui')) return;

                    var qrCanvas = document.querySelector('canvas[aria-label="Scan me!"]')
                        || document.querySelector('[data-testid="qrcode"] canvas');
                    var phoneBtn = document.querySelector('[data-testid="link-device-phone-number-method-button"]');

                    if(!qrCanvas && !phoneBtn) { setTimeout(tryInject, 600); return; }

                    var ui = document.createElement('div');
                    ui.id = 'wa-native-ui';
                    Object.assign(ui.style, {
                        position:'fixed', top:'0', left:'0', width:'100vw', height:'100vh',
                        background:'#fff', display:'flex', flexDirection:'column',
                        alignItems:'center', zIndex:'99999', overflowY:'auto', overflowX:'hidden'
                    });

                    var header = document.createElement('div');
                    Object.assign(header.style, {
                        width:'100%', background:'#075E54',
                        padding:'48px 20px 20px', textAlign:'center', flexShrink:'0'
                    });
                    header.innerHTML = '<span style="color:#fff;font-size:22px;font-weight:700;">WhatsApp</span>';
                    ui.appendChild(header);

                    var content = document.createElement('div');
                    Object.assign(content.style, {
                        display:'flex', flexDirection:'column', alignItems:'center',
                        padding:'28px 24px 0', width:'100%'
                    });

                    var h1 = document.createElement('div');
                    Object.assign(h1.style, { fontSize:'20px', fontWeight:'700', color:'#111', marginBottom:'8px', textAlign:'center' });
                    h1.textContent = 'Log in to WhatsApp';
                    content.appendChild(h1);

                    var sub = document.createElement('div');
                    Object.assign(sub.style, { fontSize:'14px', color:'#667781', textAlign:'center', marginBottom:'24px', lineHeight:'1.5' });
                    sub.textContent = 'Scan the QR code with your phone';
                    content.appendChild(sub);

                    if(qrCanvas) {
                        var qrWrap = document.createElement('div');
                        qrWrap.id = 'wa-qr-box';
                        Object.assign(qrWrap.style, {
                            background:'#fff', borderRadius:'20px', padding:'16px',
                            marginBottom:'24px', boxShadow:'0 2px 20px rgba(0,0,0,0.12)'
                        });
                        Object.assign(qrCanvas.style, { width:'220px', height:'220px', display:'block', borderRadius:'8px' });
                        qrWrap.appendChild(qrCanvas);
                        content.appendChild(qrWrap);
                    }

                    ui.appendChild(content);

                    var btn = document.createElement('button');
                    Object.assign(btn.style, {
                        width:'calc(100% - 48px)', maxWidth:'380px', padding:'16px 24px',
                        background:'#25D366', color:'#fff', border:'none', borderRadius:'28px',
                        fontSize:'16px', fontWeight:'700', cursor:'pointer',
                        margin:'0 24px 16px', display:'block', textAlign:'center'
                    });
                    btn.textContent = 'Link with phone number';
                    if(phoneBtn) btn.onclick = function(){ phoneBtn.click(); };
                    ui.appendChild(btn);

                    var footer = document.createElement('div');
                    Object.assign(footer.style, { color:'#aaa', fontSize:'12px', textAlign:'center', padding:'8px 0 36px' });
                    footer.textContent = 'End-to-end encrypted';
                    ui.appendChild(footer);

                    document.body.appendChild(ui);

                    setInterval(function(){
                        var box = document.getElementById('wa-qr-box');
                        if(!box) return;
                        var newCanvas = document.querySelector('canvas[aria-label="Scan me!"]');
                        if(newCanvas && !box.contains(newCanvas)){
                            box.innerHTML = '';
                            Object.assign(newCanvas.style, { width:'220px', height:'220px', display:'block', borderRadius:'8px' });
                            box.appendChild(newCanvas);
                        }
                    }, 1000);
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
            handler.postDelayed({ view.evaluateJavascript(js, null) }, 1500)
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
            } catch (e: Exception) { fileCallback = null; false }
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
                WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            client?.clearFileCallback()
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO)
        val denied = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
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
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().flush()
        webView.destroy()
        super.onDestroy()
    }

    companion object { private const val FILE_CHOOSER_REQUEST = 1001 }
}
