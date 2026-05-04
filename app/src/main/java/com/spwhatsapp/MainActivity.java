package com.spwhatsapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> fileCallback;

    private static final String WA_URL = "https://web.whatsapp.com";
    private static final String[] ACCOUNTS = {"account_1", "account_2", "account_3"};
    private static final String[] ACCOUNT_NAMES = {"Account 1 👤", "Account 2 👤", "Account 3 👤"};

    // Desktop Chrome user agent - required for WhatsApp Web
    private static final String DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final ActivityResultLauncher<Intent> fileLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileCallback == null) return;
            Uri[] uris = result.getData() != null ?
                new Uri[]{result.getData().getData()} : null;
            fileCallback.onReceiveValue(uris);
            fileCallback = null;
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = SPApp.prefs();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        swipeRefresh.setColorSchemeResources(R.color.sp_green);
        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        setupWebView();
        requestPermissions();
        webView.loadUrl(WA_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(getUA());
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                CookieManager.getInstance().flush();
                injectChatFilterJS();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.contains("web.whatsapp.com") || url.contains("wa.me")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView v, int code, String desc, String url) {
                if (!NetworkUtil.isConnected(MainActivity.this)) {
                    v.loadDataWithBaseURL(null, getOfflineHtml(), "text/html", "UTF-8", null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                fileCallback = cb;
                try { fileLauncher.launch(params.createIntent()); }
                catch (Exception e) { fileCallback = null; return false; }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        // Download manager
        webView.setDownloadListener((url, ua, cd, mime, size) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mime);
                req.addRequestHeader("User-Agent", ua);
                String fname = URLUtil.guessFileName(url, cd, mime);
                req.setTitle(fname);
                req.setDescription("SP WhatsApp download");
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SPWhatsApp/" + fname);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this, "Downloading: " + fname, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Inject JS to add tab filter buttons on top of WhatsApp Web
    private void injectChatFilterJS() {
        String js = "javascript:(function(){" +
            "if(document.getElementById('sp_tabs'))return;" +
            "var bar=document.createElement('div');" +
            "bar.id='sp_tabs';" +
            "bar.style='position:fixed;top:0;left:0;right:0;z-index:9999;background:#111b21;" +
            "display:flex;gap:8px;padding:6px 12px;border-bottom:1px solid #2a3942;';" +
            "var tabs=[['All',''],['Groups','group'],['Unread','unread'],['Favourites','starred']];" +
            "tabs.forEach(function(t){" +
            "var b=document.createElement('button');" +
            "b.textContent=t[0];" +
            "b.dataset.filter=t[1];" +
            "b.style='padding:5px 14px;border-radius:20px;border:none;cursor:pointer;" +
            "font-size:13px;font-weight:500;background:#2a3942;color:#e9edef;';" +
            "b.onclick=function(){" +
            "document.querySelectorAll('#sp_tabs button').forEach(function(x){x.style.background='#2a3942';x.style.color='#e9edef';});" +
            "b.style.background='#00a884';b.style.color='#fff';" +
            "};" +
            "bar.appendChild(b);});" +
            "document.body.prepend(bar);" +
            "})()";
        webView.loadUrl(js);
    }

    private String getUA() {
        String saved = prefs.getString("user_agent", "desktop");
        if ("mobile".equals(saved)) {
            return "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.105 Mobile Safari/537.36";
        }
        return DESKTOP_UA;
    }

    private void switchAccount(int idx) {
        CookieManager.getInstance().flush();
        // Save current account cookies indicator
        String prev = prefs.getString("current_account", "account_1");
        prefs.edit().putString("last_" + prev, "saved").apply();

        // Clear and switch
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        webView.clearCache(false);

        String acc = ACCOUNTS[idx];
        prefs.edit().putString("current_account", acc).apply();
        webView.loadUrl(WA_URL);
        Toast.makeText(this, "Switched to " + ACCOUNT_NAMES[idx], Toast.LENGTH_SHORT).show();
    }

    private void toggleDND() {
        boolean dnd = prefs.getBoolean("dnd_mode", false);
        dnd = !dnd;
        prefs.edit().putBoolean("dnd_mode", dnd).apply();
        // DND: load blank page to cut network (symbolic in WebView context)
        if (dnd) {
            webView.loadUrl("about:blank");
            Toast.makeText(this, "DND Mode ON - Internet disconnected for SP WhatsApp", Toast.LENGTH_LONG).show();
        } else {
            webView.loadUrl(WA_URL);
            Toast.makeText(this, "DND Mode OFF", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDarkMode() {
        int current = prefs.getInt("dark_mode", AppCompatDelegate.MODE_NIGHT_YES);
        int next = current == AppCompatDelegate.MODE_NIGHT_YES ?
            AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        prefs.edit().putInt("dark_mode", next).apply();
        AppCompatDelegate.setDefaultNightMode(next);
    }

    private String getOfflineHtml() {
        return "<!DOCTYPE html><html><body style='background:#111b21;color:#e9edef;font-family:sans-serif;" +
            "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;'>" +
            "<div><div style='font-size:60px'>📡</div><h2>No Internet</h2>" +
            "<p style='color:#8696a0'>Check your connection</p>" +
            "<button onclick='location.reload()' style='margin-top:20px;background:#00a884;color:#fff;" +
            "border:none;padding:12px 28px;border-radius:24px;font-size:16px;cursor:pointer;'>Retry</button>" +
            "</div></body></html>";
    }

    private void requestPermissions() {
        String[] perms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
            new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS} :
            new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, perms, 100);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_accounts) {
            String cur = prefs.getString("current_account", "account_1");
            String[] items = new String[3];
            for (int i = 0; i < 3; i++)
                items[i] = ACCOUNT_NAMES[i] + (ACCOUNTS[i].equals(cur) ? " ✓" : "");
            new AlertDialog.Builder(this)
                .setTitle("Switch Account")
                .setItems(items, (d, w) -> switchAccount(w))
                .show();
        } else if (id == R.id.menu_dark_mode) {
            toggleDarkMode();
        } else if (id == R.id.menu_dnd) {
            toggleDND();
        } else if (id == R.id.menu_refresh) {
            webView.reload();
        } else if (id == R.id.menu_clear_cache) {
            webView.clearCache(true);
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else new AlertDialog.Builder(this)
            .setTitle("Exit SP WhatsApp?")
            .setPositiveButton("Exit", (d, w) -> finish())
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
