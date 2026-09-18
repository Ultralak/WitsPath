package com.example.witspath.model;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreGraphConverter {

    public static void fetchGraphFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("nodes").get().addOnSuccessListener(nodeSnapshots -> {
            List<NodeDTO> nodeDTOs = new ArrayList<>();
            for (QueryDocumentSnapshot doc : nodeSnapshots) {
                nodeDTOs.add(doc.toObject(NodeDTO.class));
            }

            db.collection("edges").get().addOnSuccessListener(edgeSnapshots -> {
                List<EdgeDTO> edgeDTOs = new ArrayList<>();
                for (QueryDocumentSnapshot doc : edgeSnapshots) {
                    edgeDTOs.add(doc.toObject(EdgeDTO.class));
                }

                buildGraphFromDTOs(nodeDTOs, edgeDTOs);
            });
        });
    }

    public static void buildGraphFromDTOs(List<NodeDTO> nodeDTOs, List<EdgeDTO> edgeDTOs) {
        if (nodeDTOs == null || edgeDTOs == null) {
            return;
        }

        for (NodeDTO nodeDTO : nodeDTOs) {
            String name = nodeDTO.getLabel() != null ? nodeDTO.getLabel() : nodeDTO.getNodeId();
            String area = nodeDTO.getFloorId() != null ? nodeDTO.getFloorId() : "WSS"; 
            String type = nodeDTO.getType() != null ? nodeDTO.getType() : "CLASSROOM";

            if (Node.getByName(name) == null) {
                new Node(name, area, nodeDTO.getX(), nodeDTO.getY(), type);
            }
        }

        for (EdgeDTO edgeDTO : edgeDTOs) {
            Node fromNode = Node.getByName(edgeDTO.getFromNodeId());
            Node toNode = Node.getByName(edgeDTO.getToNodeId());

            if (fromNode == null || toNode == null) {
                System.out.println("Warning: Firestore Edge references unknown node (" 
                        + edgeDTO.getFromNodeId() + " -> " + edgeDTO.getToNodeId() + ")");
                continue;
            }

            boolean isStatusOk = "ok".equalsIgnoreCase(edgeDTO.getStatus()) || edgeDTO.getStatus() == null;

            new Edge(
                fromNode, 
                toNode, 
                edgeDTO.getDistance(), 
                edgeDTO.getAccessibilityCost(),
                isStatusOk
            );
        }
    }
}
