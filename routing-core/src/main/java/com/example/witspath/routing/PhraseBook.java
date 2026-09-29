package com.example.witspath.routing;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phrase templates for turn-by-turn directions. The model never writes directions; they come from here.
 *
 * A translated phrase is used only if both {@code text} and {@code verifiedBy} are non-empty.
 * Otherwise the English phrase is used and the step is marked as a fallback.
 */
public final class PhraseBook {
    public static final class Translation {
        public final String text;
        public final String verifiedBy;

        public Translation(String text, String verifiedBy) {
            this.text = text;
            this.verifiedBy = verifiedBy;
        }

        boolean verified() {
            return text != null && !text.trim().isEmpty() && verifiedBy != null && !verifiedBy.trim().isEmpty();
        }
    }

    /** A resolved phrase. */
    public static final class Resolved {
        public final String template;
        public final String lang;
        public final boolean fallback;

        Resolved(String template, String lang, boolean fallback) {
            this.template = template;
            this.lang = lang;
            this.fallback = fallback;
        }
    }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("route_summary", "Route to {place}: {distance} metres.");
        m.put("step_free_route", "This route is step-free.");
        m.put("not_step_free", "This route is not confirmed step-free.");
        m.put("estimated_time", "Estimated time: about {minutes} minutes.");
        m.put("estimated_time_one", "Estimated time: about 1 minute.");
        m.put("start_at", "Start at {place}.");
        m.put("head_towards", "Head towards {place} for {distance} metres.");
        m.put("go_straight", "Go straight on for {distance} metres to {place}.");
        m.put("turn_left", "Turn left and continue {distance} metres to {place}.");
        m.put("turn_right", "Turn right and continue {distance} metres to {place}.");
        m.put("slight_left", "Bear slightly left and continue {distance} metres to {place}.");
        m.put("slight_right", "Bear slightly right and continue {distance} metres to {place}.");
        m.put("continue_to", "Continue {distance} metres to {place}.");
        m.put("use_ramp", "Use the ramp at {place}.");
        m.put("arrive", "You have arrived at {place}.");
        m.put("take_elevator", "Take the lift at {place}.");
        m.put("elevator_out_of_service", "The lift at {place} is out of service.");
        m.put("floor_level", "You are now on {floor}.");
        m.put("stairs_warning", "Warning: the next section to {place} uses stairs.");
        m.put("path_blocked", "Part of this route is reported blocked near {place}.");
        m.put("accessible_entrance", "Use the accessible entrance at {place}.");
        m.put("route_unavailable", "I can't confirm a route right now.");
        m.put("caution_slope", "Caution: the next section to {place} is a steep slope.");
        ENGLISH = Collections.unmodifiableMap(m);
    }

    private final String lang;
    private final Map<String, Translation> translations;

    private PhraseBook(String lang, Map<String, Translation> translations) {
        this.lang = lang;
        this.translations = translations;
    }

    public static PhraseBook english() {
        return new PhraseBook("en", new HashMap<>());
    }

    public static PhraseBook forLanguage(String lang, Map<String, Translation> translations) {
        return new PhraseBook(lang == null ? "en" : lang, translations == null ? new HashMap<>() : translations);
    }

    public String lang() {
        return lang;
    }

    public Resolved resolve(String key) {
        if (!"en".equals(lang)) {
            Translation t = translations.get(key);
            if (t != null && t.verified()) return new Resolved(t.text, lang, false);
        }
        String en = ENGLISH.get(key);
        if (en == null) throw new IllegalArgumentException("Unknown phrase key: " + key);
        return new Resolved(en, "en", !"en".equals(lang));
    }

    /** Fills {name} placeholders. Place names are inserted verbatim. */
    public static String fill(String template, Map<String, String> params) {
        String out = template;
        for (Map.Entry<String, String> e : params.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
