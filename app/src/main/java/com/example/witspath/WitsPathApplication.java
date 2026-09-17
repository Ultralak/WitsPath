package com.example.witspath;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.witspath.model.EdgeUpdateListener;
import com.example.witspath.model.FirestoreGraphConverter;
import com.example.witspath.model.Graph;
import com.example.witspath.util.Prefs;

/**
 * Team Wavelets - WitsPath
 * Register this in AndroidManifest.xml as android:name=".WitsPathApplication".
 * Re-applies the saved language on cold start so the app doesn't flash English
 * before LanguageActivity has a chance to run.
 */
public class WitsPathApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
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
