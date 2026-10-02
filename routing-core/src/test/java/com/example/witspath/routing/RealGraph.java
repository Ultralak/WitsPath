package com.example.witspath.routing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Loads maps for tests. {@link #load()} is whatever map the app currently bundles; its place ids change when the
 * team replaces the map. {@link #loadWestCampus()} is a fixed copy of the original West Campus map, for tests that
 * check exact routes.
 */
final class RealGraph {
    private RealGraph() {}

    static CampusGraph load() throws Exception {
        String json = new String(Files.readAllBytes(
                Paths.get("..", "app", "src", "main", "assets", "graph_data.json")), StandardCharsets.UTF_8);
        return CampusGraph.fromMap(MiniJson.parseObject(json));
    }

    static CampusGraph loadWestCampus() throws Exception {
        try (java.io.InputStream in = RealGraph.class.getResourceAsStream("/west-campus-graph.json")) {
            if (in == null) throw new IllegalStateException("west-campus-graph.json is missing from the test resources");
            return CampusGraph.fromMap(MiniJson.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }
}
