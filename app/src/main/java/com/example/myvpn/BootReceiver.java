package com.example.myvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Phone restart වෙද්දීත් VPN auto-start කරනවා
 * AndroidManifest.xml වල RECEIVE_BOOT_COMPLETED permission ඕනෑ
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Phone boot වෙලා ඉවර වෙනකොට VPN service start කරනවා
            Intent vpnIntent = new Intent(context, MyVpnService.class);
            vpnIntent.setAction("START");
            context.startForegroundService(vpnIntent);
        }
    }
}
