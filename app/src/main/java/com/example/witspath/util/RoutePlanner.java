package com.example.witspath.util;

import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Directions;
import com.example.witspath.routing.Node;
import com.example.witspath.routing.PathFinder;
import com.example.witspath.routing.PhraseBook;
import com.example.witspath.routing.RouteOptions;
import com.example.witspath.routing.TravelTimeEstimator;

import java.util.Collections;
import java.util.List;

/**
 * The single entry point for planning a route in the app. The planner, navigation screen and the
 * companion all use this, so they all run the same A* with the same settings.
 */
public final class RoutePlanner {
    private RoutePlanner() {}

    public static final class Plan {
        public final List<Node> nodes;
        public final List<String> edgeIds;
        public final double distanceMetres;
        public final double seconds;
        public final Directions directions;
        /** Set when there is no route; the nodes are then empty. */
        public final String error;

        Plan(List<Node> nodes, List<String> edgeIds, double distanceMetres, double seconds,
             Directions directions, String error) {
            this.nodes = nodes;
            this.edgeIds = edgeIds;
            this.distanceMetres = distanceMetres;
            this.seconds = seconds;
            this.directions = directions;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }

        public int minutes() {
            return TravelTimeEstimator.minutes(seconds);
        }

        public boolean stepFree() {
            return directions != null && directions.stepFree;
        }
    }

    /** Profile, step-free only, prefer lifts and avoid steep ramps, straight from Settings. */
    public static RouteOptions optionsFrom(Prefs prefs) {
        return new RouteOptions(
                prefs.getString(Prefs.KEY_MOBILITY_PROFILE, "wheelchair"),
                prefs.getBoolean(Prefs.KEY_STEP_FREE_ONLY, true),
                prefs.getBoolean(Prefs.KEY_PREFER_LIFTS, true),
                prefs.getBoolean(Prefs.KEY_AVOID_STEEP_RAMPS, true));
    }

    /** The walking-speed setting as a multiplier (100 percent is 1.0). */
    public static double speedMultiplier(Prefs prefs) {
        return prefs.getInt(Prefs.KEY_WALKING_SPEED_PERCENT, 100) / 100.0;
    }

    public static Plan plan(CampusGraph graph, Node from, Node to, Prefs prefs, PhraseBook book) {
        return plan(graph, from, to, optionsFrom(prefs), speedMultiplier(prefs), book);
    }

    /** For screens that choose the mobility options themselves (the home screen's mode buttons). */
    public static Plan plan(CampusGraph graph, Node from, Node to, RouteOptions options,
                            double speedMultiplier, PhraseBook book) {
        PathFinder finder = new PathFinder();
        List<Node> path = finder.aStarSearch(from, to, options);
        if (path == null || path.isEmpty()) {
            return new Plan(Collections.<Node>emptyList(), Collections.<String>emptyList(), 0, 0, null,
                    finder.errorMessage);
        }
        double seconds = new TravelTimeEstimator(graph, speedMultiplier, options.mobilityProfile)
                .estimateSeconds(path);
        Directions directions = Directions.build(graph, path, seconds, book);
        return new Plan(path, graph.pathEdgeIds(path), directions.distanceMetres, seconds, directions, null);
    }
}
