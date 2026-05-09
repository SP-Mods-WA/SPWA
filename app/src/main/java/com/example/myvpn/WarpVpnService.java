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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class WarpVpnService extends VpnService {

    private static final String TAG        = "WarpVpnService";
    private static final String CHANNEL_ID = "vpn_channel";

    // Cloudflare WARP endpoints
    private static final String WARP_ENDPOINT   = "162.159.192.1"; // Cloudflare WARP IP
    private static final int    WARP_PORT        = 2408;           // WARP UDP port
    private static final String VPN_ADDRESS      = "172.16.0.2";
    private static final String VPN_ADDRESS_V6   = "fd01:5ca1:ab1e::2";
    private static final String DNS_WARP         = "1.1.1.1";
    private static final String DNS_WARP_V6      = "2606:4700:4700::1111";

    private ParcelFileDescriptor vpnInterface;
    private DatagramSocket       warpSocket;
    private Thread               inThread;
    private Thread               outThread;
    private boolean              running = false;

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
            // Cloudflare WARP UDP socket
            warpSocket = new DatagramSocket();
            protect(warpSocket); // VPN loop වෙන්නේ නෑ

            Builder builder = new Builder();
            builder.setSession("WARP VPN");

            // IPv4
            builder.addAddress(VPN_ADDRESS, 24);
            builder.addRoute("0.0.0.0", 0); // සියලු traffic WARP හරහා

            // IPv6
            builder.addAddress(VPN_ADDRESS_V6, 128);
            builder.addRoute("::", 0);

            // Cloudflare DNS
            builder.addDnsServer(DNS_WARP);
            builder.addDnsServer(DNS_WARP_V6);

            builder.setMtu(1280);

            // VPN app bypass
            try { builder.addDisallowedApplication(getPackageName()); }
            catch (Exception ignored) {}

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface failed");
                return;
            }

            running = true;
            Log.i(TAG, "WARP VPN Started");

            // Device → WARP thread
            outThread = new Thread(this::sendToWarp);
            outThread.start();

            // WARP → Device thread
            inThread = new Thread(this::receiveFromWarp);
            inThread.start();

        } catch (Exception e) {
            Log.e(TAG, "VPN start error: " + e.getMessage());
        }
    }

    // Device packets → Cloudflare WARP server
    private void sendToWarp() {
        try {
            FileInputStream in     = new FileInputStream(vpnInterface.getFileDescriptor());
            ByteBuffer      packet = ByteBuffer.allocate(32767);
            InetAddress     warpIp = InetAddress.getByName(WARP_ENDPOINT);

            while (running) {
                packet.clear();
                int len = in.read(packet.array());
                if (len > 0) {
                    DatagramPacket dp = new DatagramPacket(
                        packet.array(), len, warpIp, WARP_PORT);
                    warpSocket.send(dp);
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "sendToWarp: " + e.getMessage());
        }
    }

    // Cloudflare WARP → Device
    private void receiveFromWarp() {
        try {
            FileOutputStream out    = new FileOutputStream(vpnInterface.getFileDescriptor());
            byte[]           buffer = new byte[32767];
            DatagramPacket   dp     = new DatagramPacket(buffer, buffer.length);

            while (running) {
                warpSocket.receive(dp);
                if (dp.getLength() > 0) {
                    out.write(dp.getData(), 0, dp.getLength());
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "receiveFromWarp: " + e.getMessage());
        }
    }

    private void stopVpn() {
        running = false;
        try {
            if (inThread  != null) inThread.interrupt();
            if (outThread != null) outThread.interrupt();
            if (warpSocket != null) warpSocket.close();
            if (vpnInterface != null) vpnInterface.close();
        } catch (Exception ignored) {}
        stopForeground(true);
        stopSelf();
    }

    private void startForegroundNotification() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 WARP VPN Active")
            .setContentText("Protected by Cloudflare WARP")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build();

        startForeground(1, n);
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
