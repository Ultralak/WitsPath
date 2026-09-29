package com.example.witspath.routing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Loads the app's bundled graph_data.json. Gradle runs tests with the module directory as cwd. */
final class RealGraph {
    private RealGraph() {}

    static CampusGraph load() throws Exception {
        String json = new String(Files.readAllBytes(
                Paths.get("..", "app", "src", "main", "assets", "graph_data.json")), StandardCharsets.UTF_8);
        return CampusGraph.fromMap(MiniJson.parseObject(json));
    }
}
