package com.spwhatsapp;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDex;
import android.content.Context;

public class SPApp extends Application {
    private static SPApp instance;
    public static final String PREFS = "sp_prefs";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        applyTheme();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static SPApp getInstance() { return instance; }

    public static SharedPreferences prefs() {
        return instance.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void applyTheme() {
        int mode = prefs().getInt("dark_mode", AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
