package com.example.witspath;

import android.app.Application;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.witspath.model.EdgeUpdateListener;
import com.example.witspath.model.FirestoreGraphConverter;
import com.example.witspath.util.GraphStore;
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

        boolean darkTheme = appPrefs.getBoolean(Prefs.KEY_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(darkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        new EdgeUpdateListener(this, null).listenForEdgeUpdates();

        // Load graph data at startup
        GraphStore.get(this);
        FirestoreGraphConverter.fetchGraphFromFirestore(this);
    }
}
