package com.example.witspath.companion;

import android.os.Handler;
import android.os.Looper;

import com.example.witspath.util.Prefs;
import com.example.witspath.util.RoutePlanner;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Talks to the companionMessage Cloud Function. The Anthropic API key lives only on the server;
 * the app sends a Firebase ID token and never sees the key.
 */
public final class CompanionClient {

    public interface Callback {
        void onReply(CompanionReply reply);

        /** Any failure: offline, busy, unavailable. The UI shows one friendly message, never a raw code. */
        void onUnavailable();
    }

    private static final int TIMEOUT_MS = 30_000;

    private final String endpoint;
    private final Prefs prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String sessionId = "";

    /** @param endpoint the full URL of POST /api/companion/message; empty means not configured */
    public CompanionClient(String endpoint, Prefs prefs) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.prefs = prefs;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * @param inputMode     "text" or "voice"
     * @param inputLang     language the speech recogniser detected, or null
     * @param preferredLang reply language the user chose, or null for automatic
     */
    public void send(String text, String inputMode, String inputLang, String preferredLang, Callback callback) {
        executor.execute(() -> {
            CompanionReply reply = null;
            try {
                reply = post(text, inputMode, inputLang, preferredLang);
            } catch (Exception ignored) {
                // Falls through to onUnavailable
            }
            CompanionReply result = reply;
            main.post(() -> {
                if (result == null) {
                    callback.onUnavailable();
                } else {
                    sessionId = result.sessionId.isEmpty() ? sessionId : result.sessionId;
                    callback.onReply(result);
                }
            });
        });
    }

    public void newConversation() {
        sessionId = "";
    }

    private CompanionReply post(String text, String inputMode, String inputLang, String preferredLang)
            throws Exception {
        if (endpoint.isEmpty()) throw new IllegalStateException("Companion endpoint is not configured");

        JSONObject body = new JSONObject();
        if (!sessionId.isEmpty()) body.put("sessionId", sessionId);
        body.put("text", text);
        body.put("inputMode", inputMode);
        if (inputLang != null) body.put("inputLang", inputLang);
        if (preferredLang != null) body.put("preferredLang", preferredLang);
        // Same settings the route planner uses, so companion routes match planner routes.
        body.put("mobilityProfile", prefs.getString(Prefs.KEY_MOBILITY_PROFILE, "wheelchair"));
        body.put("preferLifts", prefs.getBoolean(Prefs.KEY_PREFER_LIFTS, true));
        body.put("avoidSteepRamps", prefs.getBoolean(Prefs.KEY_AVOID_STEEP_RAMPS, true));
        body.put("speedMultiplier", RoutePlanner.speedMultiplier(prefs));

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            // Anonymous use is allowed; reports then do not count toward flagging a path.
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String token = Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS).getToken();
                if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conn.getResponseCode());
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                return CompanionReply.parse(buf.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            conn.disconnect();
        }
    }
}
