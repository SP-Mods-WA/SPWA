package com.example.myvpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST_CODE = 100;
    private TextView tvStatus;
    private TextView tvIp;
    private TextView tvHiddenIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus    = findViewById(R.id.tvStatus);
        tvIp        = findViewById(R.id.tvIp);
        tvHiddenIp  = findViewById(R.id.tvHiddenIp);

        tvStatus.setText("⏳ Starting VPN...");

        // App open වෙනකොටම auto-start
        prepareVpn();
    }

    private void prepareVpn() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            startVpn();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == VPN_REQUEST_CODE && res == RESULT_OK) {
            startVpn();
        } else {
            tvStatus.setText("❌ Permission Denied");
        }
    }

    private void startVpn() {
        Intent intent = new Intent(this, PrivacyVpnService.class);
        intent.setAction("START");
        startService(intent);
        tvStatus.setText("🟢 VPN Active - IP Hidden");
        showRealIp();
    }

    private void showRealIp() {
        new Thread(() -> {
            // Multiple IP APIs try කරනවා
            String[] ipApis = {
                "https://api4.my-ip.io/ip",
                "https://ipv4.icanhazip.com",
                "https://checkip.amazonaws.com",
                "https://api.ipify.org"
            };

            for (String apiUrl : ipApis) {
                try {
                    java.net.URL url = new java.net.URL(apiUrl);
                    java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    String ip = reader.readLine().trim();
                    reader.close();

                    if (ip != null && !ip.isEmpty()) {
                        runOnUiThread(() -> {
                            tvIp.setText("Real IP: " + ip);
                            tvHiddenIp.setText("Visible IP: 🔒 Hidden via VPN");
                        });
                        return; // Success — stop trying
                    }
                } catch (Exception ignored) {
                    // Next API try කරනවා
                }
            }

            // සියලු APIs fail වුනා
            runOnUiThread(() -> {
                tvIp.setText("Real IP: Unavailable");
                tvHiddenIp.setText("Visible IP: 🔒 Hidden via VPN");
            });
        }).start();
    }
}
