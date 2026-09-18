package com.example.witspath.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Derives the immutable, rendering-only {@link FloorPlanNode}/{@link FloorPlanEdge}
 * objects that {@link com.example.witspath.ui.FloorPlanRouteView} draws, FROM the
 * live {@link Node}/{@link Edge} routing graph that {@link PathFinder} already
 * operates on -- rather than loading/building a second, independent copy of the
 * graph from Firestore. Node/Edge stays the single source of truth; this is a
 * one-way projection of it for drawing purposes.
 *
 * Deliberately NOT merged into Node/Edge themselves -- see the two different
 * key schemes (Node is keyed by name/label; FloorPlanEdge references nodes by
 * raw id) and the mutable/registering Node constructor vs the plain immutable
 * FloorPlanNode value object. Merging them would reintroduce the same
 * name-vs-id mismatch that silently dropped every edge in FirestoreGraphConverter
 * a few iterations ago.
 */
public final class FloorPlanGraphConverter {

    private FloorPlanGraphConverter() {
        // static utility class -- not meant to be instantiated
    }

    /**
     * Converts a collection of Nodes into FloorPlanNodes for rendering.
     *
     * NOTE on identity: Node has no field carrying the original Firestore
     * document id (only the local auto-incrementing int id, and the
     * name/label used as the registry key). This converter uses Node.name
     * as the FloorPlanNode's nodeId, which is fine for internal consistency
     * within a single converted graph (edges below use the same scheme),
     * but it will NOT match the original Firestore "nd_..." id string. If
     * something downstream ever needs to correlate back to the raw
     * Firestore document (e.g. writing a report against a specific node),
     * Node will need an added field to carry that id through.
     *
     * NOTE on coordinates: Node.point is stored in METRES (converted from
     * pixels at load time so the A* heuristic and edge distances share
     * units). FloorPlanNode expects the floor plan's intrinsic PIXEL space.
     * metresPerPixel must be the same value used when this floor's nodes
     * were originally loaded (see the floor's "metresPerPixel" field in its
     * source JSON/Firestore document), or the rendered positions will be
     * wrong. There's currently nowhere on Node to look this up automatically,
     * so the caller must supply it.
     *
     * @param nodes           the Nodes to convert (e.g. all nodes for one floor)
     * @param floorId         the floorId to stamp onto every resulting FloorPlanNode
     * @param metresPerPixel  the same scale factor used when these nodes' coordinates
     *                        were converted from pixels to metres at load time
     */
    public static List<FloorPlanNode> toFloorPlanNodes(Collection<Node> nodes, String floorId, double metresPerPixel) {
        List<FloorPlanNode> result = new ArrayList<>();

        if (metresPerPixel <= 0) {
            // Guard against silently producing garbage coordinates (e.g. divide
            // by zero, or a caller that forgot to pass the real value).
            throw new IllegalArgumentException("metresPerPixel must be > 0 (got " + metresPerPixel + ")");
        }

        for (Node node : nodes) {
            if (node.point == null) {
                continue; // nothing to draw without a position
            }

            float pixelX = (float) (node.point.x / metresPerPixel);
            float pixelY = (float) (node.point.y / metresPerPixel);

            String type = (node.type != null) ? node.type.name().toLowerCase() : "unknown";

            result.add(new FloorPlanNode(
                    node.name,   // nodeId -- see identity note above
                    floorId,
                    type,
                    node.name,   // label
                    pixelX,
                    pixelY
            ));
        }

        return result;
    }

    /**
     * Converts a collection of Edges into FloorPlanEdges for rendering.
     *
     * NOTE on status: Edge.status is a boolean (usable / not usable).
     * FloorPlanEdge.status is documented as a String ("ok" / "blocked").
     * This mapping is: true -> "ok", false -> "blocked". It does NOT
     * distinguish a "flagged" (reported-but-still-passable, cost-penalised)
     * state from a fully impassable one -- WIRING (1).md's weight() function
     * expects that third state ("flagged".equals(e.status), 5x cost penalty
     * rather than impassable), which the current boolean Edge.status can't
     * represent. That's a real gap between the routing model and what the
     * UI/settings wiring assumes -- worth the team deciding on purpose
     * (e.g. promoting Edge.status to a String/enum with OK/FLAGGED/BLOCKED)
     * rather than this converter silently picking one.
     *
     * NOTE on edgeId: Edge has no stored identifier at all. One is
     * synthesized here from the endpoint names for readability/debugging;
     * it is NOT guaranteed to match any original Firestore edgeId.
     */
    public static List<FloorPlanEdge> toFloorPlanEdges(Collection<Edge> edges) {
        List<FloorPlanEdge> result = new ArrayList<>();

        for (Edge edge : edges) {
            if (edge.node1 == null || edge.node2 == null) {
                continue;
            }

            String edgeId = edge.node1.name + "__" + edge.node2.name;
            String status = edge.status ? "ok" : "blocked";

            result.add(new FloorPlanEdge(
                    edgeId,
                    edge.node1.name,
                    edge.node2.name,
                    edge.distance,
                    edge.accessibilityCost,
                    status
            ));
        }

        return result;
    }
}


































