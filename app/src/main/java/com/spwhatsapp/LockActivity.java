package com.spwhatsapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

public class LockActivity extends AppCompatActivity {
    private StringBuilder enteredPin = new StringBuilder();
    private TextView pinDisplay;
    private String savedPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        savedPin = SPApp.prefs().getString("app_pin", "");
        pinDisplay = findViewById(R.id.pinDisplay);

        // Biometric
        boolean bioEnabled = SPApp.prefs().getBoolean("biometric_enabled", false);
        if (bioEnabled) tryBiometric();

        // Number pad buttons
        int[] btnIds = {
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };
        for (int i = 0; i < btnIds.length; i++) {
            final String digit = String.valueOf(i);
            Button b = findViewById(btnIds[i]);
            if (b != null) b.setOnClickListener(v -> addDigit(digit));
        }

        Button del = findViewById(R.id.btnDel);
        if (del != null) del.setOnClickListener(v -> deleteDigit());

        Button bio = findViewById(R.id.btnBiometric);
        if (bio != null) bio.setOnClickListener(v -> tryBiometric());
    }

    private void addDigit(String d) {
        if (enteredPin.length() >= 4) return;
        enteredPin.append(d);
        updateDisplay();
        if (enteredPin.length() == 4) checkPin();
    }

    private void deleteDigit() {
        if (enteredPin.length() > 0)
            enteredPin.deleteCharAt(enteredPin.length() - 1);
        updateDisplay();
    }

    private void updateDisplay() {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < 4; i++)
            dots.append(i < enteredPin.length() ? "●" : "○");
        pinDisplay.setText(dots.toString());
    }

    private void checkPin() {
        if (enteredPin.toString().equals(savedPin)) {
            unlock();
        } else {
            Toast.makeText(this, "Wrong PIN!", Toast.LENGTH_SHORT).show();
            enteredPin.setLength(0);
            updateDisplay();
        }
    }

    private void tryBiometric() {
        BiometricManager bm = BiometricManager.from(this);
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS)
            return;

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    unlock();
                }
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    // Let user use PIN
                }
            });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("SP WhatsApp")
            .setSubtitle("Verify your identity")
            .setNegativeButtonText("Use PIN")
            .build();

        prompt.authenticate(info);
    }

    private void unlock() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        // Block back press on lock screen
        finishAffinity();
    }
}
