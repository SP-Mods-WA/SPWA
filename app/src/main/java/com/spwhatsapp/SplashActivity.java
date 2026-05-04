package com.spwhatsapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            SharedPreferences p = SPApp.prefs();
            boolean lockEnabled = p.getBoolean("app_lock_enabled", false);
            String pin = p.getString("app_pin", "");

            if (lockEnabled && !pin.isEmpty()) {
                startActivity(new Intent(this, LockActivity.class));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        }, 2000);
    }
}
