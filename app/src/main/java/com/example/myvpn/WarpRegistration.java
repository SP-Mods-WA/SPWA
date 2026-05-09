package com.example.myvpn;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Cloudflare WARP API වලට register කරලා
 * free credentials ලබාගන්නවා
 */
public class WarpRegistration {

    private static final String TAG      = "WarpRegistration";
    private static final String WARP_API = "https://api.cloudflareclient.com/v0a977/reg";

    public interface Callback {
        void onSuccess(String privateKey, String address, String address6);
        void onFailure(String error);
    }

    public static void register(Callback callback) {
        new Thread(() -> {
            try {
                // Cloudflare WARP API registration
                URL url = new URL(WARP_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "1.1.1.1/6.25");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // Registration payload
                JSONObject payload = new JSONObject();
                payload.put("key", generatePublicKey());
                payload.put("install_id", java.util.UUID.randomUUID().toString());
                payload.put("fcm_token", "");
                payload.put("tos", getCurrentTime());
                payload.put("model", android.os.Build.MODEL);
                payload.put("serial_number", java.util.UUID.randomUUID().toString());
                payload.put("locale", "en_US");

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes());
                os.close();

                int responseCode = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                if (responseCode == 200) {
                    JSONObject response = new JSONObject(sb.toString());
                    JSONObject config   = response.getJSONObject("config");
                    JSONObject peers    = config.getJSONArray("peers").getJSONObject(0);
                    String privateKey   = config.getString("client_id");
                    String address      = config.getJSONObject("interface")
                                               .getJSONArray("addresses")
                                               .getString(0);
                    String address6     = config.getJSONObject("interface")
                                               .getJSONArray("addresses")
                                               .getString(1);

                    Log.i(TAG, "WARP Registration success!");
                    callback.onSuccess(privateKey, address, address6);
                } else {
                    callback.onFailure("Registration failed: " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Registration error: " + e.getMessage());
                callback.onFailure(e.getMessage());
            }
        }).start();
    }

    private static String generatePublicKey() {
        // WireGuard-style key generate කරනවා
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP);
    }

    private static String getCurrentTime() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            java.util.Locale.US).format(new java.util.Date());
    }
}
