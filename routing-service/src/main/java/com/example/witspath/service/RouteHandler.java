package com.example.witspath.service;

import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Directions;
import com.example.witspath.routing.Floor;
import com.example.witspath.routing.Node;
import com.example.witspath.routing.PathFinder;
import com.example.witspath.routing.PhraseBook;
import com.example.witspath.routing.RouteOptions;
import com.example.witspath.routing.TravelTimeEstimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /v1/route. The caller sends the graph (with live edge statuses already applied) and the
 * user's options with every request; the service keeps no state. This runs the same routing-core
 * A* as the Android app, so the companion and the planner cannot disagree.
 */
final class RouteHandler {
    private RouteHandler() {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> handle(Map<String, Object> request) {
        try {
            Object graphObj = request.get("graph");
            String fromId = str(request.get("fromNodeId"));
            String toId = str(request.get("toNodeId"));
            if (!(graphObj instanceof Map) || fromId == null || toId == null) {
                return error("bad_request", "graph, fromNodeId and toNodeId are required");
            }

            CampusGraph graph = CampusGraph.fromMap((Map<String, Object>) graphObj);
            Node from = graph.node(fromId);
            Node to = graph.node(toId);
            if (from == null || to == null) {
                return error("unknown_place", "Use node ids returned by find_place");
            }

            Map<String, Object> o = request.get("options") instanceof Map
                    ? (Map<String, Object>) request.get("options") : new HashMap<String, Object>();
            RouteOptions options = new RouteOptions(str(o.get("mobilityProfile")),
                    bool(o.get("stepFree")), bool(o.get("preferLifts")), bool(o.get("avoidSteepRamps")));
            double speed = o.get("speedMultiplier") instanceof Number
                    ? ((Number) o.get("speedMultiplier")).doubleValue() : 1.0;

            PathFinder finder = new PathFinder();
            List<Node> path = finder.aStarSearch(from, to, options);
            if (path == null) {
                String code = PathFinder.SAME_PLACE.equals(finder.errorMessage) ? "same_place" : "no_route";
                return error(code, finder.errorMessage);
            }

            double seconds = new TravelTimeEstimator(graph, speed, options.mobilityProfile).estimateSeconds(path);
            Directions directions = Directions.build(graph, path, seconds, phraseBook(request));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("path", pathJson(graph, path));
            out.put("edgeIds", graph.pathEdgeIds(path));
            out.put("distanceM", Math.round(directions.distanceMetres * 10) / 10.0);
            out.put("seconds", Math.round(seconds));
            out.put("minutes", TravelTimeEstimator.minutes(seconds));
            out.put("stepFree", directions.stepFree);
            out.put("summary", stepsJson(directions.summary));
            out.put("steps", stepsJson(directions.steps));
            out.put("directionsLang", directions.steps.get(0).lang);
            out.put("fallbackToEnglish", directions.fallbackToEnglish());
            return out;
        } catch (IllegalArgumentException e) {
            return error("bad_request", String.valueOf(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private static PhraseBook phraseBook(Map<String, Object> request) {
        String lang = str(request.get("lang"));
        if (lang == null || "en".equals(lang) || !(request.get("phrases") instanceof Map)) return PhraseBook.english();
        Map<String, PhraseBook.Translation> t = new HashMap<>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) request.get("phrases")).entrySet()) {
            if (e.getValue() instanceof Map) {
                Map<String, Object> p = (Map<String, Object>) e.getValue();
                t.put(e.getKey(), new PhraseBook.Translation(str(p.get("text")), str(p.get("verifiedBy"))));
            }
        }
        return PhraseBook.forLanguage(lang, t);
    }

    private static List<Object> pathJson(CampusGraph graph, List<Node> path) {
        List<Object> list = new ArrayList<>();
        for (Node n : path) {
            Floor f = graph.floor(n.floorId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node_id", n.nodeId);
            m.put("name", n.displayName().trim());
            m.put("floorId", n.floorId);
            // Floor-plan pixels, so the app can draw the path on its own map
            m.put("x", f == null ? n.point.x : f.metresToPixels(n.point.x));
            m.put("y", f == null ? n.point.y : f.metresToPixels(n.point.y));
            list.add(m);
        }
        return list;
    }

    private static List<Object> stepsJson(List<Directions.Step> steps) {
        List<Object> list = new ArrayList<>();
        for (Directions.Step s : steps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phraseKey", s.phraseKey);
            m.put("params", new LinkedHashMap<String, Object>(s.params));
            m.put("text", s.text);
            m.put("lang", s.lang);
            m.put("fallback", s.fallback);
            m.put("pointIndex", s.pointIndex);
            list.add(m);
        }
        return list;
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", code);
        m.put("message", message);
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean && (Boolean) o;
    }
}
