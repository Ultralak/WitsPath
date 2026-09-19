package com.example.witspath.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import com.example.witspath.ui.HomeActivity;

import java.util.Locale;

/**
 * Team Wavelets - WitsPath
 * Utility to apply global configuration (Language and Text Size) to a Context.
 */
public final class AppConfiguration {

    private AppConfiguration() {}

    public static Context updateContext(Context context) {
        Prefs prefs = new Prefs(context);
        Configuration config = new Configuration(context.getResources().getConfiguration());

        // 1. Apply Language
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        if (!tag.isEmpty()) {
            Locale locale = Locale.forLanguageTag(tag);
            Locale.setDefault(locale);
            config.setLocale(locale);
        }

        // 2. Apply Text Size
        String textSize = prefs.getString(Prefs.KEY_TEXT_SIZE, "default");
        float scale = 1.0f;
        switch (textSize) {
            case "small": scale = 0.85f; break;
            case "large": scale = 1.25f; break;
            case "huge":  scale = 1.50f; break;
            default:      scale = 1.00f; break;
        }
        config.fontScale = context.getResources().getConfiguration().fontScale * scale;

        return context.createConfigurationContext(config);
    }

    /**
     * Restarts the current activity to apply configuration changes on the same screen.
     */
    public static void refreshApp(Activity activity) {
        Intent intent = new Intent(activity, activity.getClass());
        if (activity.getIntent() != null && activity.getIntent().getExtras() != null) {
            intent.putExtras(activity.getIntent().getExtras());
        }
        activity.startActivity(intent);
        activity.finish();
        activity.overridePendingTransition(0, 0);
    }
}
