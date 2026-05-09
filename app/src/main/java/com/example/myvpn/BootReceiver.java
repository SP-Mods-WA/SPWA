package com.example.myvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Phone boot වෙලා ඉවර වෙනකොට VPN auto-start
            Intent vpnIntent = new Intent(context, PrivacyVpnService.class);
            vpnIntent.setAction("START");
            context.startForegroundService(vpnIntent);
        }
    }
}
