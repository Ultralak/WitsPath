package com.example.witspath.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds small graph maps for tests. All nodes are on floor f1 at 1 metre per pixel unless stated. */
final class GraphBuilder {
    final List<Object> floors = new ArrayList<>();
    final List<Object> nodes = new ArrayList<>();
    final List<Object> edges = new ArrayList<>();

    GraphBuilder() {
        floor("f1", 1.0);
    }

    GraphBuilder floor(String id, double mpp) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("floorId", id);
        f.put("name", id);
        f.put("level", 0);
        f.put("imageWidth", 1000);
        f.put("imageHeight", 1000);
        f.put("metresPerPixel", mpp);
        floors.add(f);
        return this;
    }

    GraphBuilder node(String id, double x, double y) {
        return node(id, "f1", "room", x, y);
    }

    GraphBuilder node(String id, String floorId, String type, double x, double y) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("nodeId", id);
        n.put("floorId", floorId);
        n.put("type", type);
        n.put("label", id.toUpperCase());
        n.put("x", x);
        n.put("y", y);
        nodes.add(n);
        return this;
    }

    GraphBuilder edge(String a, String b, double dist) {
        return edge(a, b, dist, new HashMap<>());
    }

    GraphBuilder edge(String a, String b, double dist, Map<String, Object> extra) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("edgeId", a + "-" + b);
        e.put("fromNodeId", a);
        e.put("toNodeId", b);
        e.put("distance", dist);
        e.putAll(extra);
        edges.add(e);
        return this;
    }

    /** Edge with the given flags as key/value pairs, e.g. edge("a","b",5,"stairs",true). */
    GraphBuilder edgeWith(String a, String b, double dist, Object... kv) {
        Map<String, Object> extra = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) extra.put((String) kv[i], kv[i + 1]);
        return edge(a, b, dist, extra);
    }

    CampusGraph build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("floors", floors);
        root.put("nodes", nodes);
        root.put("edges", edges);
        return CampusGraph.fromMap(root);
    }
}
