package com.example.witspath.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Team Wavelets - WitsPath
 * Single place for every SharedPreferences key used by the settings, preferences,
 * language and drawer screens.
 */
public final class Prefs {

    private static final String FILE_NAME = "witspath_prefs";

    // ---- keys -------------------------------------------------------------
    public static final String KEY_UI_LANGUAGE = "pref_ui_language";
    public static final String KEY_TEXT_SIZE = "pref_text_size";
    public static final String KEY_HIGH_CONTRAST = "pref_high_contrast";
    public static final String KEY_SCREEN_READER_HINTS = "pref_screen_reader_hints";
    public static final String KEY_SYNC_ENABLED = "pref_sync_enabled";

    public static final String KEY_MOBILITY_PROFILE = "pref_mobility_profile";
    public static final String KEY_STEP_FREE_ONLY = "pref_step_free_only";
    public static final String KEY_PREFER_LIFTS = "pref_prefer_lifts";
    public static final String KEY_AVOID_STEEP_RAMPS = "pref_avoid_steep_ramps";
    public static final String KEY_MAX_ROUTE_METRES = "pref_max_route_metres";

    public static final String KEY_VOICE_GUIDANCE = "pref_voice_guidance";
    public static final String KEY_HAPTICS = "pref_haptics";
    public static final String KEY_AUTO_LOCATE = "pref_auto_locate";
    public static final String KEY_DEFAULT_BUILDING = "pref_default_building";
    public static final String KEY_UNITS = "pref_units";

    public static final String KEY_SHOW_FLAGGED = "pref_show_flagged";
    public static final String KEY_ANON_USAGE = "pref_anon_usage";

    public static final String KEY_TRACK_FREQUENT = "pref_track_frequent";
    public static final String KEY_HOME_NODE_ID = "pref_home_node_id";
    public static final String KEY_SAVED_PLACES_JSON = "pref_saved_places_json";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = (appContext != null ? appContext : context)
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void setString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    /** Use for language and critical state that must persist before activity recreation. */
    public void setStringSync(String key, String value) {
        prefs.edit().putString(key, value).commit();
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void setInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
