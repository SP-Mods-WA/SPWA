package com.example.myvpn;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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

        tvStatus   = findViewById(R.id.tvStatus);
        tvIp       = findViewById(R.id.tvIp);
        tvHiddenIp = findViewById(R.id.tvHiddenIp);

        tvStatus.setText("⏳ Initializing WARP...");

        SharedPreferences prefs = getSharedPreferences("warp", MODE_PRIVATE);
        boolean registered = prefs.getBoolean("registered", false);

        if (!registered) {
            registerAndStart();
        } else {
            prepareVpn();
        }
    }

    private void registerAndStart() {
        tvStatus.setText("⏳ Registering with Cloudflare...");
        WarpRegistration.register(new WarpRegistration.Callback() {
            @Override
            public void onSuccess(String privateKey, String address, String address6) {
                getSharedPreferences("warp", MODE_PRIVATE).edit()
                    .putBoolean("registered", true)
                    .putString("private_key", privateKey)
                    .putString("address", address)
                    .putString("address6", address6)
                    .apply();
                runOnUiThread(() -> prepareVpn());
            }
            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> prepareVpn());
            }
        });
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
        Intent intent = new Intent(this, WarpVpnService.class);
        intent.setAction("START");
        startService(intent);
        tvStatus.setText("🟢 WARP Active - IP Hidden");
        fetchIpAddresses();
    }

    private void fetchIpAddresses() {
        new Thread(() -> {
            String[] apis = {
                "https://api4.my-ip.io/ip",
                "https://ipv4.icanhazip.com",
                "https://checkip.amazonaws.com"
            };
            for (String api : apis) {
                try {
                    java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(api).openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    String ip = r.readLine().trim();
                    r.close();
                    if (ip != null && !ip.isEmpty()) {
                        runOnUiThread(() -> {
                            tvIp.setText("Your IP: " + ip);
                            tvHiddenIp.setText("🔒 Routed via Cloudflare WARP");
                        });
                        return;
                    }
                } catch (Exception ignored) {}
            }
            runOnUiThread(() -> {
                tvIp.setText("IP: Protected");
                tvHiddenIp.setText("🔒 Cloudflare WARP Active");
            });
        }).start();
    }
}
