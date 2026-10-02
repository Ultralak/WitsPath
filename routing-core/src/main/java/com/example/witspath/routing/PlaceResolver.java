package com.example.witspath.routing;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Finds a place in the app's map from what another system said about it. The AI companion's backend
 * answers with place ids from ITS copy of the map. If that copy is a different version from the app's, an
 * id can be unknown here, and the app must say so instead of quietly choosing another place.
 */
public final class PlaceResolver {
    private PlaceResolver() {}

    /**
     * @return the node with this id; otherwise the one node whose label matches {@code label} exactly
     *         (ignoring case, accents and extra spaces); otherwise null. Never a guess.
     */
    public static Node resolve(CampusGraph graph, String nodeId, String label) {
        if (graph == null) return null;
        Node byId = graph.node(nodeId);
        if (byId != null) return byId;

        String wanted = normalise(label);
        if (wanted.isEmpty()) return null;
        Node found = null;
        for (Node n : graph.nodes()) {
            if (!wanted.equals(normalise(n.label))) continue;
            if (found != null) return null; // two places share the name: ambiguous, so no answer
            found = n;
        }
        return found;
    }

    static String normalise(String s) {
        if (s == null) return "";
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return d.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
