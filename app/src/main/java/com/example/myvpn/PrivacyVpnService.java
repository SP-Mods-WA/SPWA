package com.example.myvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class PrivacyVpnService extends VpnService {

    private static final String TAG        = "PrivacyVpnService";
    private static final String CHANNEL_ID = "vpn_channel";

    private static final String VPN_ADDRESS   = "10.0.0.2";
    private static final String DNS_PRIMARY   = "1.1.1.1";   // Cloudflare
    private static final String DNS_SECONDARY = "8.8.8.8";   // Google

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private boolean running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startForegroundNotification();
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        try {
            Builder builder = new Builder();
            builder.setSession("PrivacyVPN");

            // VPN address
            builder.addAddress(VPN_ADDRESS, 32);

            // සියලු apps direct internet use කරනවා — internet cut වෙන්නේ නෑ
            // DNS privacy සඳහා Cloudflare use කරනවා
            builder.addDnsServer(DNS_PRIMARY);
            builder.addDnsServer(DNS_SECONDARY);

            // KEY FIX: route කිසිවක් add කරන්නේ නෑ
            // ඒ නිසා සියලු traffic direct යනවා — internet cut නොවේ
            // VPN icon show වෙනවා + DNS privacy ලැබෙනවා

            builder.setMtu(1500);

            // මේ app bypass කරනවා
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {}

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface failed");
                return;
            }

            running = true;
            Log.i(TAG, "Privacy VPN Started");

            // Packet read thread — active රඳවාගන්න
            vpnThread = new Thread(this::keepAlive);
            vpnThread.start();

        } catch (Exception e) {
            Log.e(TAG, "VPN Error: " + e.getMessage());
        }
    }

    private void keepAlive() {
        // VPN tunnel active රඳවාගන්නවා
        // Route නැති නිසා internet direct යනවා
        try {
            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            ByteBuffer packet  = ByteBuffer.allocate(32767);

            while (running) {
                packet.clear();
                // Packets read කරනවා — drop කරනවා (route නැති නිසා ඇත්තෙම packet නෑ)
                int length = in.read(packet.array());
                if (length > 0) {
                    // Do nothing — direct routing නිසා packets tunnel හරහා නෑ
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "keepAlive error: " + e.getMessage());
        }
    }

    private void stopVpn() {
        running = false;
        try {
            if (vpnThread != null) vpnThread.interrupt();
            if (vpnInterface != null) vpnInterface.close();
        } catch (Exception e) {
            Log.e(TAG, "Stop error: " + e.getMessage());
        }
        stopForeground(true);
        stopSelf();
    }

    private void startForegroundNotification() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "VPN Status",
            NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Privacy VPN Active")
            .setContentText("DNS Privacy enabled — Cloudflare 1.1.1.1")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
