package com.example.witspath.routing;

import java.util.ArrayList;
import java.util.List;

public final class Node {
    /** 1-based, in file order. Used for deterministic tie-breaking. */
    public final int index;
    public final String nodeId;
    public final String label;
    /** Lower-case type: room, entrance, ramp, elevator, node, ... */
    public final String type;
    public final String floorId;
    public final String bssid;
    public final Point point;
    public final List<Edge> edges = new ArrayList<>();

    public Node(int index, String nodeId, String label, String type, String floorId, String bssid, Point point) {
        this.index = index;
        this.nodeId = nodeId;
        this.label = label == null ? "" : label;
        this.type = type == null ? "node" : type.toLowerCase();
        this.floorId = floorId;
        this.bssid = bssid == null ? "" : bssid;
        this.point = point;
    }

    /** The node at the other end of {@code e}, or null if {@code e} does not touch this node. */
    public Node other(Edge e) {
        if (e.node1 == this) return e.node2;
        if (e.node2 == this) return e.node1;
        return null;
    }

    /** The label, or the id when the node has no label. */
    public String displayName() {
        return label.isEmpty() ? nodeId : label;
    }

    @Override
    public String toString() {
        return "Node[" + nodeId + " " + label + "]";
    }
}
