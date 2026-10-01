package com.example.witspath.model;

import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Edge;
import com.example.witspath.routing.Floor;
import com.example.witspath.routing.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects the routing graph ({@link CampusGraph}) into the immutable {@link FloorPlanNode} /
 * {@link FloorPlanEdge} objects that FloorPlanRouteView draws. The routing graph stays the single
 * source of truth; this is a one-way conversion for drawing only.
 *
 * Node coordinates in the routing graph are METRES; the floor plan is drawn in PIXELS, so each
 * node is converted back with its floor's metresPerPixel.
 */
public final class FloorPlanGraphConverter {

    private FloorPlanGraphConverter() {}

    /** All nodes on {@code floorId}, in floor-plan pixel coordinates. */
    public static List<FloorPlanNode> toFloorPlanNodes(CampusGraph graph, String floorId) {
        List<FloorPlanNode> result = new ArrayList<>();
        Floor floor = graph.floor(floorId);
        if (floor == null || floor.metresPerPixel <= 0) {
            throw new IllegalArgumentException("Unknown floor or bad scale: " + floorId);
        }
        for (Node node : graph.nodes()) {
            if (node.point == null || !floorId.equals(node.floorId)) continue;
            result.add(new FloorPlanNode(
                    node.nodeId,
                    floorId,
                    node.type,
                    node.displayName(),
                    node.bssid,
                    (float) (floor.metresToPixels(node.point.x) - floor.originX),
                    (float) (floor.metresToPixels(node.point.y) - floor.originY)));
        }
        return result;
    }

    /**
     * Edges with both ends on {@code floorId}. FloorPlanEdge status is "ok" or "blocked"; the
     * flagged state is drawn as blocked because routing never uses it.
     */
    public static List<FloorPlanEdge> toFloorPlanEdges(CampusGraph graph, String floorId) {
        List<FloorPlanEdge> result = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            if (!floorId.equals(edge.node1.floorId) || !floorId.equals(edge.node2.floorId)) continue;
            result.add(new FloorPlanEdge(
                    edge.edgeId,
                    edge.node1.nodeId,
                    edge.node2.nodeId,
                    edge.distance,
                    edge.accessibilityCost,
                    edge.isUsable() ? "ok" : "blocked",
                    edge.label));
        }
        return result;
    }
}
