package com.spwhatsapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.*;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings_container, new SettingsFragment()).commit();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle saved, String root) {
            setPreferencesFromResource(R.xml.preferences, root);

            // App Lock toggle
            SwitchPreferenceCompat lockSwitch = findPreference("app_lock_enabled");
            if (lockSwitch != null) {
                lockSwitch.setOnPreferenceChangeListener((pref, val) -> {
                    boolean enable = (boolean) val;
                    if (enable) showSetPinDialog();
                    else {
                        SPApp.prefs().edit().putBoolean("app_lock_enabled", false)
                            .putString("app_pin", "").apply();
                    }
                    return false; // We set it manually
                });
            }

            // User Agent
            ListPreference ua = findPreference("user_agent");
            if (ua != null) ua.setOnPreferenceChangeListener((p, v) -> {
                Toast.makeText(getContext(), "Restart app to apply", Toast.LENGTH_SHORT).show();
                return true;
            });

            // Clear all data
            Preference clear = findPreference("clear_all_data");
            if (clear != null) clear.setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle("Clear All Data")
                    .setMessage("You will be logged out of all accounts!")
                    .setPositiveButton("Clear", (d, w) -> {
                        android.webkit.CookieManager.getInstance().removeAllCookies(null);
                        android.webkit.CookieManager.getInstance().flush();
                        SPApp.prefs().edit().clear().apply();
                        Toast.makeText(getContext(), "Done! Restart app.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null).show();
                return true;
            });

            // Auto-reply toggle
            SwitchPreferenceCompat autoReply = findPreference("auto_reply_enabled");
            if (autoReply != null) autoReply.setOnPreferenceChangeListener((p, v) -> {
                boolean on = (boolean) v;
                SPApp.prefs().edit().putBoolean("auto_reply_enabled", on).apply();
                Toast.makeText(getContext(), "Auto-reply " + (on ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        private void showSetPinDialog() {
            EditText et = new EditText(requireContext());
            et.setHint("Enter 4-digit PIN");
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            new AlertDialog.Builder(requireContext())
                .setTitle("Set App Lock PIN")
                .setView(et)
                .setPositiveButton("Set", (d, w) -> {
                    String pin = et.getText().toString().trim();
                    if (pin.length() != 4) {
                        Toast.makeText(getContext(), "PIN must be 4 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SPApp.prefs().edit()
                        .putBoolean("app_lock_enabled", true)
                        .putString("app_pin", pin).apply();
                    SwitchPreferenceCompat sw = findPreference("app_lock_enabled");
                    if (sw != null) sw.setChecked(true);
                    Toast.makeText(getContext(), "App Lock enabled!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
        }
    }
}
