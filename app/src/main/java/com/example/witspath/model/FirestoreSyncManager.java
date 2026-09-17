package com.example.witspath.model;

import com.example.witspath.util.Prefs;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class FirestoreSyncManager {

    public void pushPreferencesToFirestore(FirebaseUser user, Prefs prefs) {
        if (user == null) return;
        Map<String, Object> p = new HashMap<>();
        p.put(Prefs.KEY_UI_LANGUAGE, prefs.getString(Prefs.KEY_UI_LANGUAGE, ""));
        p.put(Prefs.KEY_MOBILITY_PROFILE, prefs.getString(Prefs.KEY_MOBILITY_PROFILE, "wheelchair"));
        p.put(Prefs.KEY_STEP_FREE_ONLY, prefs.getBoolean(Prefs.KEY_STEP_FREE_ONLY, true));
        p.put(Prefs.KEY_AVOID_STEEP_RAMPS, prefs.getBoolean(Prefs.KEY_AVOID_STEEP_RAMPS, true));
        p.put(Prefs.KEY_PREFER_LIFTS, prefs.getBoolean(Prefs.KEY_PREFER_LIFTS, true));
        p.put(Prefs.KEY_HIGH_CONTRAST, prefs.getBoolean(Prefs.KEY_HIGH_CONTRAST, false));
        p.put(Prefs.KEY_TEXT_SIZE, prefs.getString(Prefs.KEY_TEXT_SIZE, "default"));
        p.put(Prefs.KEY_SYNC_ENABLED, prefs.getBoolean(Prefs.KEY_SYNC_ENABLED, true));
        
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .update("preferences", p);
    }

    public void reportObstacle(String userId, String edgeId) {
        Map<String, Object> r = new HashMap<>();
        r.put("userId", userId);
        r.put("edgeId", edgeId);
        r.put("timestamp", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("reports").add(r);
    }
}
