package com.example.witspath.routing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** An undirected walkable segment. The constructor registers the edge on both nodes. */
public final class Edge {
    public static final double IMPASSABLE_COST = 999.0;

    public final String edgeId;
    public final Node node1;
    public final Node node2;
    /** Metres. */
    public final double distance;
    public final double accessibilityCost;
    public final boolean ramp;
    public final boolean stairs;
    public final boolean elevator;
    public final String label;
    /** Lower end of a slope, or null when flat/unknown. */
    public final String uphillFromNodeId;
    public boolean steepRamp;
    /** Keys: wheelchair / walkingAid / lowVision / noPreference. */
    public final Map<String, Double> profileCosts = new HashMap<>();
    /** Mobility profile ids (wheelchair, walking_aid, low_vision, none) that cannot use this edge. */
    public final Set<String> inaccessibleFor = new HashSet<>();

    private String statusText;
    private boolean status;

    public Edge(String edgeId, Node node1, Node node2, double distance, double accessibilityCost,
                boolean ramp, boolean stairs, boolean elevator, String statusText,
                String label, String uphillFromNodeId) {
        this.edgeId = edgeId;
        this.node1 = node1;
        this.node2 = node2;
        this.distance = distance;
        this.accessibilityCost = accessibilityCost;
        this.ramp = ramp;
        this.stairs = stairs;
        this.elevator = elevator;
        this.label = label == null ? "" : label;
        this.uphillFromNodeId = uphillFromNodeId;
        setStatus(statusText);
        node1.edges.add(this);
        node2.edges.add(this);
    }

    public Edge(String edgeId, Node node1, Node node2, double distance, double accessibilityCost,
                boolean ramp, boolean stairs, boolean elevator, String statusText) {
        this(edgeId, node1, node2, distance, accessibilityCost, ramp, stairs, elevator, statusText, "", null);
    }

    /** Live status change (e.g. from a Firestore listener). Missing status means ok. */
    public void setStatus(String statusText) {
        this.statusText = statusText == null ? "ok" : statusText;
        this.status = "ok".equalsIgnoreCase(this.statusText);
    }

    /** ok, flagged or blocked. */
    public String statusText() {
        return statusText;
    }

    /** True only when the status is "ok". Flagged and blocked edges are never routed through. */
    public boolean isUsable() {
        return status;
    }

    public boolean isStepFree() {
        return (!stairs || ramp || elevator) && accessibilityCost < IMPASSABLE_COST;
    }

    public boolean isStairsOnly() {
        return stairs && !ramp && !elevator;
    }

    public double costFor(RouteOptions o) {
        Double own = profileCosts.get(o.dataKey());
        return own != null ? own : accessibilityCost;
    }
}
