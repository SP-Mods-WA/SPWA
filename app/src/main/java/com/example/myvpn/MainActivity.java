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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvIp     = findViewById(R.id.tvIp);

        // App open වෙනකොටම VPN auto-start
        prepareAndStartVpn();
    }

    private void prepareAndStartVpn() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            // First time — permission dialog
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            // Permission already given — start directly
            startVpn();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startVpn();
            } else {
                tvStatus.setText("❌ Permission Denied");
            }
        }
    }

    private void startVpn() {
        Intent intent = new Intent(this, MyVpnService.class);
        intent.setAction("START");
        startService(intent);
        tvStatus.setText("🟢 VPN Connected");
        updateIpDisplay();
    }

    private void updateIpDisplay() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            for (java.net.NetworkInterface ni :
                    java.util.Collections.list(ifaces)) {
                for (java.net.InetAddress addr :
                        java.util.Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress()
                            && addr instanceof java.net.Inet4Address) {
                        tvIp.setText("IP: " + addr.getHostAddress());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            tvIp.setText("IP: Unknown");
        }
    }
}
