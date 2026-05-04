package com.spwhatsapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SPPrefs {

    public static final String PREFS_NAME     = "sp_whatsapp_prefs";
    public static final String KEY_DARK_MODE  = "dark_mode";
    public static final String KEY_APP_LOCK   = "app_lock_enabled";
    public static final String KEY_PIN        = "app_pin";
    public static final String KEY_DND        = "dnd_mode";
    public static final String KEY_ACCOUNT    = "current_account";
    public static final String KEY_USER_AGENT = "user_agent_mode";
    public static final String KEY_AUTOREPLY  = "auto_reply_enabled";
    public static final String KEY_AUTOREPLY_MSG = "auto_reply_message";
    public static final String KEY_FIRST_RUN  = "first_run";

    private static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void putBoolean(Context ctx, String key, boolean value) {
        get(ctx).edit().putBoolean(key, value).apply();
    }

    public static boolean getBoolean(Context ctx, String key, boolean def) {
        return get(ctx).getBoolean(key, def);
    }

    public static void putString(Context ctx, String key, String value) {
        get(ctx).edit().putString(key, value).apply();
    }

    public static String getString(Context ctx, String key, String def) {
        return get(ctx).getString(key, def);
    }

    public static void putInt(Context ctx, String key, int value) {
        get(ctx).edit().putInt(key, value).apply();
    }

    public static int getInt(Context ctx, String key, int def) {
        return get(ctx).getInt(key, def);
    }
}
