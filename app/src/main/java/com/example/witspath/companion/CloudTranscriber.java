package com.example.witspath.companion;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends a voice clip to the companion backend, which relays it to Lelapa's Vulavula speech-to-text.
 * The Vulavula key stays on the server; the app never sees it.
 */
public final class CloudTranscriber {

    public interface Callback {
        void onText(String text);

        /** The server has no transcription key, or could not be reached. The caller falls back. */
        void onUnavailable();

        /** The clip was transcribed but nothing could be made out. */
        void onNothingHeard();
    }

    public interface AvailabilityCallback {
        void onChecked(boolean available);
    }

    private static final int TIMEOUT_MS = 30_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Asks the backend whether transcription is switched on (its Vulavula key is set). */
    public void checkAvailable(String endpoint, AvailabilityCallback callback) {
        String url = CompanionEndpoint.configUrl(endpoint);
        if (url == null) {
            callback.onChecked(false);
            return;
        }
        executor.execute(() -> {
            boolean available = false;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                try {
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    if (conn.getResponseCode() == 200) {
                        JSONObject json = new JSONObject(read(conn.getInputStream()));
                        JSONObject voice = json.optJSONObject("voice");
                        available = voice != null && voice.optBoolean("vulavulaTranscription", false);
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) {
                // unreachable means not available
            }
            boolean result = available;
            main.post(() -> callback.onChecked(result));
        });
    }

    public void transcribe(String endpoint, byte[] wav, String languageCode, Callback callback) {
        String url = CompanionEndpoint.transcribeUrl(endpoint, languageCode);
        if (url == null) {
            callback.onUnavailable();
            return;
        }
        executor.execute(() -> {
            String text = null;
            boolean unavailable = false;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                try {
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(TIMEOUT_MS);
                    conn.setReadTimeout(TIMEOUT_MS);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "audio/wav");
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(wav);
                    }
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        text = new JSONObject(read(conn.getInputStream())).optString("text", "").trim();
                    } else {
                        unavailable = true; // 501 not configured, 5xx failed, 4xx unusable
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                unavailable = true;
            }
            String heard = text;
            boolean failed = unavailable;
            main.post(() -> {
                if (failed) callback.onUnavailable();
                else if (heard == null || heard.isEmpty()) callback.onNothingHeard();
                else callback.onText(heard);
            });
        });
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toString(StandardCharsets.UTF_8.name());
        }
    }
}
