package com.example.witspath.model;

import android.content.Context;

import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Edge;
import com.example.witspath.util.GraphStore;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.Set;

/**
 * Applies live edge status changes (ok, flagged, blocked) from Firestore to the routing graph, and
 * tells the caller which edges changed so it can re-route. Every instance applies the change itself,
 * so listeners do not depend on each other.
 */
public class EdgeUpdateListener {

    public interface RouteRefreshCallback {
        /** Edge ids whose status changed. */
        void onEdgesChanged(Set<String> edgeIds);
    }

    private final Context context;
    private final RouteRefreshCallback refreshCallback;

    public EdgeUpdateListener(Context context, RouteRefreshCallback callback) {
        this.context = context.getApplicationContext();
        this.refreshCallback = callback;
    }

    /** Keep the registration and call {@code remove()} when the screen goes away. */
    public ListenerRegistration listenForEdgeUpdates() {
        return FirebaseFirestore.getInstance()
                .collection("edges")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    CampusGraph graph = GraphStore.get(context);
                    if (graph == null) return;
                    Set<String> changed = new HashSet<>();
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.MODIFIED) continue;
                        String edgeId = dc.getDocument().getString("edgeId");
                        Edge e = graph.edge(edgeId != null ? edgeId : dc.getDocument().getId());
                        if (e == null) continue;
                        String before = e.statusText();
                        e.setStatus(dc.getDocument().getString("status"));
                        if (!before.equalsIgnoreCase(e.statusText())) changed.add(e.edgeId);
                    }
                    if (!changed.isEmpty() && refreshCallback != null) {
                        refreshCallback.onEdgesChanged(changed);
                    }
                });
    }
}
