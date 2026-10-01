package com.example.witspath.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The campus routing graph. Built from plain Maps and Lists (the shape of graph_data.json and the
 * Firestore collections), so this module needs no JSON library.
 */
public final class CampusGraph {
    private final Map<String, Floor> floors = new LinkedHashMap<>();
    private final Map<String, Node> nodesById = new LinkedHashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, Edge> edgesById = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private CampusGraph() {}

    /**
     * @throws IllegalArgumentException for a node with a missing id/x/y or on an unknown floor.
     *                                  Edges that reference unknown nodes are skipped and recorded in {@link #warnings()}.
     */
    @SuppressWarnings("unchecked")
    public static CampusGraph fromMap(Map<String, Object> root) {
        CampusGraph g = new CampusGraph();

        for (Object o : list(root.get("floors"))) {
            Map<String, Object> f = (Map<String, Object>) o;
            String id = str(f.get("floorId"));
            if (id == null) throw new IllegalArgumentException("Floor without floorId");
            Double mpp = num(f.get("metresPerPixel"));
            String viewBox = str(f.get("viewBox"));
            double originX = 0;
            double originY = 0;
            if (viewBox != null) {
                String[] parts = viewBox.trim().split("[,\\s]+");
                if (parts.length >= 4) {
                    try {
                        originX = Double.parseDouble(parts[0]);
                        originY = Double.parseDouble(parts[1]);
                    } catch (NumberFormatException ignored) {
                        originX = 0;
                        originY = 0;
                    }
                }
            }
            g.floors.put(id, new Floor(id, orEmpty(str(f.get("name"))),
                    (int) num(f.get("level"), 0), (int) num(f.get("imageWidth"), 0),
                    (int) num(f.get("imageHeight"), 0), mpp == null ? 1.0 : mpp, originX, originY));
        }

        int index = 0;
        for (Object o : list(root.get("nodes"))) {
            Map<String, Object> n = (Map<String, Object>) o;
            String nodeId = str(n.get("nodeId"));
            Double x = num(n.get("x"));
            Double y = num(n.get("y"));
            if (nodeId == null || x == null || y == null) {
                throw new IllegalArgumentException("Node is missing nodeId, x or y: " + n);
            }
            String floorId = str(n.get("floorId"));
            Floor floor = floorId == null ? null : g.floors.get(floorId);
            if (floor == null) {
                throw new IllegalArgumentException("Node " + nodeId + " is on unknown floor: " + floorId);
            }
            Node node = new Node(++index, nodeId, str(n.get("label")), str(n.get("type")), floorId,
                    str(n.get("bssid")), new Point(floor.pixelsToMetres(x), floor.pixelsToMetres(y)));
            g.nodes.add(node);
            g.nodesById.put(nodeId, node);
        }

        int edgeIndex = 0;
        for (Object o : list(root.get("edges"))) {
            Map<String, Object> e = (Map<String, Object>) o;
            int i = edgeIndex++;
            String edgeId = str(e.get("edgeId"));
            if (edgeId == null) edgeId = "edge_" + i;
            Node from = g.nodesById.get(str(e.get("fromNodeId")));
            Node to = g.nodesById.get(str(e.get("toNodeId")));
            if (from == null || to == null) {
                g.warnings.add("Edge " + edgeId + " skipped: unknown node "
                        + (from == null ? e.get("fromNodeId") : e.get("toNodeId")));
                continue;
            }
            Double dist = num(e.get("distance"));
            if (dist == null) {
                g.warnings.add("Edge " + edgeId + " skipped: missing distance");
                continue;
            }
            boolean elevator = bool(e.get("elevator")) || bool(e.get("lift"));
            Edge edge = new Edge(edgeId, from, to, dist, num(e.get("accessibilityCost"), 1.0),
                    bool(e.get("ramp")), bool(e.get("stairs")), elevator, str(e.get("status")),
                    orEmpty(str(e.get("label"))), str(e.get("uphillFrom")));
            edge.steepRamp = bool(e.get("steepRamp")) || bool(e.get("steep"))
                    || "steep".equalsIgnoreCase(str(e.get("rampGrade")));

            Object costs = e.get("accessibilityCosts") != null ? e.get("accessibilityCosts")
                    : e.get("accessibilityCostByProfile");
            if (costs instanceof Map) {
                for (Map.Entry<String, Object> c : ((Map<String, Object>) costs).entrySet()) {
                    Double v = num(c.getValue());
                    if (v != null) edge.profileCosts.put(c.getKey(), v);
                }
            }
            for (Object p : list(e.get("inaccessibleFor"))) {
                String id = str(p);
                if (id == null) continue;
                id = id.trim().toLowerCase().replace('-', '_');
                edge.inaccessibleFor.add(id.equals("no_preference") ? RouteOptions.NONE : id);
            }
            g.edges.add(edge);
            g.edgesById.put(edgeId, edge);
        }
        return g;
    }

    public Floor floor(String floorId) {
        return floors.get(floorId);
    }

    public List<Floor> floors() {
        List<Floor> l = new ArrayList<>(floors.values());
        l.sort((a, b) -> Integer.compare(a.level, b.level));
        return l;
    }

    public Node node(String nodeId) {
        return nodeId == null ? null : nodesById.get(nodeId);
    }

    public List<Node> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public Edge edge(String edgeId) {
        return edgesById.get(edgeId);
    }

    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /** Problems found while loading (skipped edges). */
    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * The edge joining {@code a} and {@code b}: the shortest usable one if several exist,
     * otherwise the shortest of any status. Null when they are not adjacent.
     */
    public Edge edgeBetween(Node a, Node b) {
        Edge best = null;
        for (Edge e : a.edges) {
            if (e.node1 != b && e.node2 != b) continue;
            if (best == null || better(e, best)) best = e;
        }
        return best;
    }

    private static boolean better(Edge e, Edge best) {
        if (e.isUsable() != best.isUsable()) return e.isUsable();
        return e.distance < best.distance;
    }

    /** Total distance of a path in metres, summed over {@link #edgeBetween}. */
    public double pathDistance(List<Node> path) {
        double d = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Edge e = edgeBetween(path.get(i), path.get(i + 1));
            if (e != null) d += e.distance;
        }
        return d;
    }

    /** Edge ids of a path, in order. */
    public List<String> pathEdgeIds(List<Node> path) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            Edge e = edgeBetween(path.get(i), path.get(i + 1));
            if (e != null) ids.add(e.edgeId);
        }
        return ids;
    }

    // ---- lenient value readers ----

    private static List<?> list(Object o) {
        return o instanceof List ? (List<?>) o : Collections.emptyList();
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isEmpty() ? null : s;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double num(Object o, double def) {
        Double d = num(o);
        return d == null ? def : d;
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        return o instanceof String && Boolean.parseBoolean((String) o);
    }
}
