package com.example.myvpn;

import android.net.VpnService;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class MyVpnService extends VpnService {

    private static final String TAG = "MyVpnService";
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private boolean running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        try {
            // VPN Interface හදන්න
            Builder builder = new Builder();
            builder.setSession("MyVPN");
            builder.addAddress("10.0.0.2", 24);          // VPN IP
            builder.addDnsServer("8.8.8.8");              // Google DNS
            builder.addRoute("0.0.0.0", 0);               // සියලු traffic route කරන්න
            builder.setMtu(1500);

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface establish වුණේ නෑ");
                return;
            }

            running = true;
            Log.i(TAG, "VPN Started!");

            // Packet handle කරන thread එක
            vpnThread = new Thread(this::handlePackets);
            vpnThread.start();

        } catch (Exception e) {
            Log.e(TAG, "VPN start error: " + e.getMessage());
        }
    }

    private void handlePackets() {
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(32767);

        while (running) {
            try {
                packet.clear();
                int length = in.read(packet.array());

                if (length > 0) {
                    packet.limit(length);
                    // මෙහිදී packets process / forward කරන්න
                    // Simple loopback: packet ආපහු යවනවා (demo purposes)
                    out.write(packet.array(), 0, length);
                }

            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "Packet handle error: " + e.getMessage());
                }
            }
        }
    }

    private void stopVpn() {
        running = false;
        try {
            if (vpnThread != null) {
                vpnThread.interrupt();
            }
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Stop error: " + e.getMessage());
        }
        stopSelf();
        Log.i(TAG, "VPN Stopped.");
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
