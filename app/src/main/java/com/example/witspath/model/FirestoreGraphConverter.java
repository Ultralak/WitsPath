package com.example.witspath.model;

import com.example.witspath.Edge;
import com.example.witspath.Node;
import java.util.List;

public class FirestoreGraphConverter {

    public static void buildGraphFromDTOs(List<NodeDTO> nodeDTOs, List<EdgeDTO> edgeDTOs) {
        if (nodeDTOs == null || edgeDTOs == null) {
            return;
        }

        // 1. Re-instantiate Person 2's Node objects.
        // Node's constructor internally registers the node via nodes.put(name, this).
        // DTO field map: label/nodeId -> name, floorId -> area, type -> type.
        for (NodeDTO nodeDTO : nodeDTOs) {
            String name = nodeDTO.getLabel() != null ? nodeDTO.getLabel() : nodeDTO.getNodeId();
            String area = nodeDTO.getFloorId() != null ? nodeDTO.getFloorId() : "WSS"; 
            String type = nodeDTO.getType() != null ? nodeDTO.getType() : "CLASSROOM";

            // If a Node with this name already exists, skip or re-create depending on implementation.
            if (Node.getByName(name) == null) {
                new Node(name, area, nodeDTO.getX(), nodeDTO.getY(), type);
            }
        }

        // 2. Re-instantiate Person 2's Edge objects.
        // Edge's constructor automatically wires up neighbors and edges bidirectionally
        // without passing complex objects, avoiding circular serialization issues entirely.
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
                edgeDTO.isRamp(), 
                edgeDTO.isStairs(), 
                edgeDTO.isElevator(), 
                isStatusOk
            );
        }
    }
}
