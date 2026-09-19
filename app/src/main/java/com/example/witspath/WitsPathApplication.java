package com.example.witspath;

import android.app.Application;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.witspath.model.EdgeUpdateListener;
import com.example.witspath.model.FirestoreGraphConverter;
import com.example.witspath.model.Graph;
import com.example.witspath.util.AppConfiguration;
import com.example.witspath.util.Prefs;

/**
 * Team Wavelets - WitsPath
 */
public class WitsPathApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Re-apply locale to prevent "staggered" language changes
        Prefs appPrefs = new Prefs(this);
        String tag = appPrefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        if (!tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }

        boolean darkTheme = appPrefs.getBoolean("pref_dark_theme", false);
        AppCompatDelegate.setDefaultNightMode(darkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        new EdgeUpdateListener(null).listenForEdgeUpdates();

        // Load graph data at startup
        Graph.loadFromAssets(this, "graph_data.json");
        FirestoreGraphConverter.fetchGraphFromFirestore();
    }
}
