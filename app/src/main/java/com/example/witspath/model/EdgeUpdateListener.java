package com.example.witspath.model;

import android.util.Log;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;

public class EdgeUpdateListener {

    public void listenForEdgeUpdates() {
        FirebaseFirestore.getInstance()
            .collection("edges")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null) {
                    return;
                }
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.MODIFIED) {
                        EdgeDTO edgeDTO = dc.getDocument().toObject(EdgeDTO.class);
                        if (edgeDTO != null) {
                            Node fromNode = Node.getByName(edgeDTO.getFromNodeId());
                            if (fromNode != null) {
                                for (Edge e : fromNode.edges) {
                                    if ((e.node1 == fromNode && e.node2.name.equals(edgeDTO.getToNodeId())) ||
                                        (e.node2 == fromNode && e.node1.name.equals(edgeDTO.getToNodeId()))) {
                                        e.status = "ok".equalsIgnoreCase(edgeDTO.getStatus()) || edgeDTO.getStatus() == null;
                                        if ("flagged".equalsIgnoreCase(edgeDTO.getStatus())) {
                                            Log.w("WitsPath", "Recalculation event triggered for flagged edge: " + edgeDTO.getEdgeId());
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            });
    }
}
