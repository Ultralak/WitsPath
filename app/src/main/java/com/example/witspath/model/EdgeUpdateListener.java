package com.example.witspath.model;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.LinkedList;

public class EdgeUpdateListener {

    public interface RouteRefreshCallback {
        void onRouteNeedsRefresh();
    }

    private RouteRefreshCallback refreshCallback;

    public EdgeUpdateListener(RouteRefreshCallback callback) {
        this.refreshCallback = callback;
    }

    public void listenForEdgeUpdates() {
        FirebaseFirestore.getInstance()
                .collection("edges")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    boolean triggerRefresh = false;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.MODIFIED) {
                            EdgeDTO dto = dc.getDocument().toObject(EdgeDTO.class);
                            if (dto == null) continue;
                            Node from = Node.getByName(dto.getFromNodeId());
                            if (from != null) {
                                for (Edge e : from.edges) {
                                    if ((e.node1 == from && e.node2.name.equals(dto.getToNodeId())) ||
                                        (e.node2 == from && e.node1.name.equals(dto.getToNodeId()))) {
                                        boolean wasOk = e.status;
                                        e.status = "ok".equalsIgnoreCase(dto.getStatus()) || dto.getStatus() == null;
                                        if (wasOk && !e.status && "flagged".equalsIgnoreCase(dto.getStatus())) {
                                            triggerRefresh = true;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (triggerRefresh && refreshCallback != null) {
                        refreshCallback.onRouteNeedsRefresh();
                    }
                });
    }
}
