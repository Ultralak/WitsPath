package com.example.witspath.util;

import com.example.witspath.routing.Node;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks path_status/{nodeId} = { blocked, reason, reportedAt } for every node on a route.
 * A route with a blocked node must never be shown as clear.
 */
public final class PathStatusChecker {
    private PathStatusChecker() {}

    public interface Callback {
        /** Names of the places on the route reported blocked. Empty means no active reports. */
        void onResult(List<String> blockedPlaces);

        /** The status could not be read. The route is then shown as unchecked, not as clear. */
        void onUnavailable();
    }

    public static void check(List<Node> path, Callback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (Node n : path) {
            tasks.add(db.collection("path_status").document(n.nodeId).get());
        }
        Tasks.whenAllComplete(tasks).addOnSuccessListener(done -> {
            List<String> blocked = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                Task<DocumentSnapshot> t = tasks.get(i);
                if (!t.isSuccessful()) {
                    callback.onUnavailable();
                    return;
                }
                DocumentSnapshot d = t.getResult();
                if (d != null && d.exists() && Boolean.TRUE.equals(d.getBoolean("blocked"))) {
                    blocked.add(path.get(i).displayName().trim());
                }
            }
            callback.onResult(blocked);
        }).addOnFailureListener(e -> callback.onUnavailable());
    }
}
