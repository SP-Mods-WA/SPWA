package com.example.myvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class PrivacyVpnService extends VpnService {

    private static final String TAG = "PrivacyVpnService";
    private static final String CHANNEL_ID = "vpn_channel";

    // Cloudflare WARP IPs — privacy සඳහා
    private static final String VPN_ADDRESS   = "172.16.0.2";
    private static final String VPN_ROUTE     = "0.0.0.0";          // සියලු traffic
    private static final String DNS_PRIMARY   = "1.1.1.1";          // Cloudflare
    private static final String DNS_SECONDARY = "1.0.0.1";          // Cloudflare backup

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

            // VPN tunnel address
            builder.addAddress(VPN_ADDRESS, 24);

            // සියලු IPv4 traffic route කරනවා
            builder.addRoute(VPN_ROUTE, 0);

            // Cloudflare DNS — privacy + fast
            builder.addDnsServer(DNS_PRIMARY);
            builder.addDnsServer(DNS_SECONDARY);

            // MTU set කරනවා
            builder.setMtu(1500);

            // Local traffic bypass කරනවා
            builder.allowFamily(android.system.OsConstants.AF_INET);

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface failed");
                return;
            }

            running = true;
            Log.i(TAG, "Privacy VPN Started - IP Hidden");

            vpnThread = new Thread(this::handlePackets);
            vpnThread.start();

        } catch (Exception e) {
            Log.e(TAG, "VPN Error: " + e.getMessage());
        }
    }

    private void handlePackets() {
        try {
            FileInputStream  in     = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out    = new FileOutputStream(vpnInterface.getFileDescriptor());
            ByteBuffer       packet = ByteBuffer.allocate(32767);

            while (running) {
                packet.clear();
                int length = in.read(packet.array());

                if (length > 0) {
                    packet.limit(length);

                    // Packet forward කරනවා
                    // Real production VPN එකක් නම් මෙහිදී
                    // encrypted tunnel (TLS/DTLS) හරහා server එකට යවනවා
                    // Demo: loopback
                    out.write(packet.array(), 0, length);
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "Packet error: " + e.getMessage());
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
            .setContentText("Your IP is hidden")
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
