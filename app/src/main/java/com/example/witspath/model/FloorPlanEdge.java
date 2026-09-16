package com.example.witspath.model;

/**
 * A single edge on the floor-plan graph, connecting two {@link FloorPlanNode}s.
 * Per the graph schema: weight = distance * accessibilityCost, and an
 * accessibilityCost >= 999 (or status "blocked") should be treated as impassable.
 */
public class FloorPlanEdge {

    private final String edgeId;
    private final String fromNodeId;
    private final String toNodeId;
    private final double distance;
    private final double accessibilityCost;
    private final String status;

    public FloorPlanEdge(String edgeId, String fromNodeId, String toNodeId,
                          double distance, double accessibilityCost, String status) {
        this.edgeId = edgeId;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.distance = distance;
        this.accessibilityCost = accessibilityCost;
        this.status = status;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public double getDistance() {
        return distance;
    }

    public double getAccessibilityCost() {
        return accessibilityCost;
    }

    /** e.g. "ok", "blocked" */
    public String getStatus() {
        return status;
    }
}
