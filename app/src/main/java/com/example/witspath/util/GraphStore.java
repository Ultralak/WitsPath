package com.example.witspath.util;

import android.content.Context;
import android.util.Log;

import com.example.witspath.routing.CampusGraph;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the one campus graph every screen routes on. Loaded from the bundled graph_data.json,
 * then replaced by the Firestore copy when that arrives. All routing goes through routing-core.
 */
public final class GraphStore {
    private static final String TAG = "GraphStore";
    public static final String ASSET_NAME = "graph_data.json";

    private static CampusGraph graph;
    private static Map<String, Object> assetRoot;

    private GraphStore() {}

    /** The current graph, loading the bundled one on first use. Null only if the asset is unreadable. */
    public static synchronized CampusGraph get(Context context) {
        if (graph == null) {
            try {
                graph = CampusGraph.fromMap(assetRoot(context));
                for (String w : graph.warnings()) Log.w(TAG, w);
            } catch (IOException | JSONException | IllegalArgumentException e) {
                Log.e(TAG, "Could not load " + ASSET_NAME, e);
            }
        }
        return graph;
    }

    public static synchronized void set(CampusGraph g) {
        graph = g;
    }

    /** The bundled graph as plain maps. Firestore data reuses its floors when it has none of its own. */
    public static synchronized Map<String, Object> assetRoot(Context context) throws IOException, JSONException {
        if (assetRoot == null) {
            try (InputStream is = context.getApplicationContext().getAssets().open(ASSET_NAME)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
                assetRoot = toMap(new JSONObject(out.toString(StandardCharsets.UTF_8.name())));
            }
        }
        return assetRoot;
    }

    static Map<String, Object> toMap(JSONObject o) throws JSONException {
        Map<String, Object> m = new LinkedHashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            m.put(k, toValue(o.get(k)));
        }
        return m;
    }

    private static Object toValue(Object v) throws JSONException {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof JSONObject) return toMap((JSONObject) v);
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<Object> l = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) l.add(toValue(a.get(i)));
            return l;
        }
        return v;
    }
}
