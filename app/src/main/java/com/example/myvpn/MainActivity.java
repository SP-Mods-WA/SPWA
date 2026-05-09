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
            try {
                // Real IP fetch කරනවා
                java.net.URL url = new java.net.URL("https://api.ipify.org");
                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) url.openConnection();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()));
                String realIp = reader.readLine();
                reader.close();

                runOnUiThread(() -> {
                    tvIp.setText("Real IP: " + realIp);
                    tvHiddenIp.setText("Visible IP: 🔒 Hidden");
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvIp.setText("IP: Detecting..."));
            }
        }).start();
    }
}
