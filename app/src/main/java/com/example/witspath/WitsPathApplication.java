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
        String tag = new Prefs(this).getString(Prefs.KEY_UI_LANGUAGE, "");
        if (!tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }

        new EdgeUpdateListener(null).listenForEdgeUpdates();

        // Load graph data at startup
        Graph.loadFromAssets(this, "wavelets-graph.json");
        FirestoreGraphConverter.fetchGraphFromFirestore();
    }
}
