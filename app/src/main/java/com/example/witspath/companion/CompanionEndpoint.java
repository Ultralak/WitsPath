package com.example.witspath.companion;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.example.witspath.R;
import com.example.witspath.util.Prefs;

import java.net.URI;

/**
 * Where the companion backend lives. Release builds always use the address baked into strings.xml.
 * Debug builds can override it from the companion's menu, so a changing tunnel address needs no rebuild.
 */
public final class CompanionEndpoint {
    public static final String PATH = "/api/companion/message";

    private CompanionEndpoint() {}

    /** True only for a debuggable build (Run from Android Studio, or a debug APK). */
    public static boolean overrideAllowed(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /** The address to call: the debug override when allowed and set, otherwise the built-in one. */
    public static String resolve(Context context, Prefs prefs) {
        if (overrideAllowed(context)) {
            String override = prefs.getString(Prefs.KEY_DEBUG_COMPANION_ENDPOINT, "");
            if (override != null && !override.isEmpty()) return override;
        }
        return context.getString(R.string.companion_endpoint);
    }

    /**
     * Turns what a person pasted into the full endpoint URL. A bare address such as
     * {@code https://name.trycloudflare.com} gets the API path added. Only https is accepted, because the
     * app does not allow plain http traffic.
     *
     * @return the URL, or null if the text is not a usable https address
     */
    public static String normalise(String typed) {
        if (typed == null) return null;
        String s = typed.trim();
        if (s.isEmpty()) return null;
        try {
            URI uri = new URI(s);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
            if (uri.getHost() == null || uri.getHost().isEmpty()) return null;
            if (uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) return null;
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                return s.replaceAll("/+$", "") + PATH;
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** The server's address without the companion path, or null if the endpoint is not a usable https address. */
    public static String baseUrl(String endpoint) {
        if (endpoint == null) return null;
        String s = endpoint.trim();
        if (s.isEmpty()) return null;
        try {
            URI uri = new URI(s);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return null;
            String origin = "https://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            return origin;
        } catch (Exception e) {
            return null;
        }
    }

    /** Where the backend says whether voice transcription is switched on. */
    public static String configUrl(String endpoint) {
        String base = baseUrl(endpoint);
        return base == null ? null : base + "/api/companion/config";
    }

    /** Where to send a recorded clip for transcription. {@code lang} is a code such as "zu". */
    public static String transcribeUrl(String endpoint, String lang) {
        String base = baseUrl(endpoint);
        if (base == null || lang == null || !lang.matches("[a-z]{2,3}")) return null;
        return base + "/api/speech/transcribe?lang=" + lang;
    }
}
