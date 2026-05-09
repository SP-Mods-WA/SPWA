package com.example.myvpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.TextView;

import ca.psiphon.PsiphonTunnel;

public class MainActivity extends Activity implements PsiphonTunnel.HostService {

    private static final int VPN_REQUEST_CODE = 100;
    private TextView tvStatus;
    private TextView tvIp;
    private TextView tvHiddenIp;
    private PsiphonTunnel psiphonTunnel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus   = findViewById(R.id.tvStatus);
        tvIp       = findViewById(R.id.tvIp);
        tvHiddenIp = findViewById(R.id.tvHiddenIp);

        tvStatus.setText("⏳ Starting Psiphon...");

        psiphonTunnel = PsiphonTunnel.newPsiphonTunnel(this);
        prepareVpn();
    }

    private void prepareVpn() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            startPsiphon();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == VPN_REQUEST_CODE && res == RESULT_OK) {
            startPsiphon();
        } else {
            tvStatus.setText("❌ Permission Denied");
        }
    }

    private void startPsiphon() {
        new Thread(() -> {
            try {
                psiphonTunnel.startRouting();
                runOnUiThread(() -> tvStatus.setText("⏳ Connecting..."));
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ Error: " + e.getMessage()));
            }
        }).start();
    }

    // ── PsiphonTunnel.HostService callbacks ──────────────────────────

    @Override
    public String getAppName() { return "PrivacyVPN"; }

    @Override
    public Activity getContext() { return this; }

    @Override
    public VpnService.Builder newVpnServiceBuilder() {
        return new VpnService.Builder();
    }

    @Override
    public String getPsiphonConfig() {
        // Psiphon embedded config — account ඕනෑ නෑ
        return "{}";
    }

    @Override
    public void onDiagnosticMessage(String message) {
        android.util.Log.d("Psiphon", message);
    }

    @Override
    public void onAvailableEgressRegions(java.util.List<String> regions) {}

    @Override
    public void onSocksProxyPortInUse(int port) {}

    @Override
    public void onHttpProxyPortInUse(int port) {}

    @Override
    public void onListeningSocksProxyPort(int port) {}

    @Override
    public void onListeningHttpProxyPort(int port) {}

    @Override
    public void onUpstreamProxyError(String message) {}

    @Override
    public void onConnecting() {
        runOnUiThread(() -> tvStatus.setText("⏳ Connecting..."));
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            tvStatus.setText("🟢 Connected - IP Hidden");
            fetchIp();
        });
    }

    @Override
    public void onHomepage(String url) {}

    @Override
    public void onClientRegion(String region) {
        runOnUiThread(() -> tvHiddenIp.setText("🔒 Region: " + region));
    }

    @Override
    public void onClientAddress(String address) {
        runOnUiThread(() -> tvIp.setText("IP: " + address));
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> tvStatus.setText("🔴 Disconnected"));
    }

    @Override
    public void onFailedConnectionAttempt() {}

    @Override
    public void onRequestingUpstreamProxy() {}

    @Override
    public void onActiveAuthorizationIDs(java.util.List<String> ids) {}

    @Override
    public void onTrafficRateLimits(long up, long down) {}

    @Override
    public void onApplicationParameters(Object params) {}

    @Override
    public void onServerAlert(String reason, String subject,
                              java.util.List<String> actionURLs) {}

    @Override
    public void onExiting() {}

    private void fetchIp() {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(
                        "https://ipv4.icanhazip.com").openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()));
                String ip = r.readLine().trim();
                r.close();
                runOnUiThread(() -> tvIp.setText("IP: " + ip));
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (psiphonTunnel != null) psiphonTunnel.stop();
        super.onDestroy();
    }
}
