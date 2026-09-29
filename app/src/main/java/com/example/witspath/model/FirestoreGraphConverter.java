package com.example.witspath.model;

import android.content.Context;
import android.util.Log;

import com.example.witspath.routing.CampusGraph;
import com.example.witspath.util.GraphStore;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces the bundled graph with the live Firestore copy. Firestore documents have the same shape
 * as graph_data.json, so they go through the same CampusGraph loader (pixel to metre conversion,
 * validation). If anything is wrong, the bundled graph stays in use.
 */
public class FirestoreGraphConverter {
    private static final String TAG = "FirestoreGraph";

    public static void fetchGraphFromFirestore(Context context) {
        Context app = context.getApplicationContext();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<QuerySnapshot> floors = db.collection("floors").get();
        Task<QuerySnapshot> nodes = db.collection("nodes").get();
        Task<QuerySnapshot> edges = db.collection("edges").get();
        Tasks.whenAllSuccess(floors, nodes, edges).addOnSuccessListener(results -> {
            try {
                Map<String, Object> root = new LinkedHashMap<>();
                List<Object> floorDocs = documents((QuerySnapshot) results.get(0), null);
                root.put("floors", floorDocs.isEmpty() ? GraphStore.assetRoot(app).get("floors") : floorDocs);
                root.put("nodes", documents((QuerySnapshot) results.get(1), null));
                root.put("edges", documents((QuerySnapshot) results.get(2), "edgeId"));
                CampusGraph graph = CampusGraph.fromMap(root);
                for (String w : graph.warnings()) Log.w(TAG, w);
                if (!graph.nodes().isEmpty()) GraphStore.set(graph);
            } catch (Exception e) {
                Log.w(TAG, "Keeping the bundled graph: " + e.getMessage());
            }
        }).addOnFailureListener(e -> Log.w(TAG, "Keeping the bundled graph: " + e.getMessage()));
    }

    /** Document data as plain maps. {@code idField}, if given, defaults to the document id. */
    private static List<Object> documents(QuerySnapshot snap, String idField) {
        List<Object> list = new ArrayList<>();
        for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
            Map<String, Object> data = d.getData();
            if (data == null) continue;
            Map<String, Object> copy = new LinkedHashMap<>(data);
            if (idField != null && copy.get(idField) == null) copy.put(idField, d.getId());
            list.add(copy);
        }
        return list;
    }
}
