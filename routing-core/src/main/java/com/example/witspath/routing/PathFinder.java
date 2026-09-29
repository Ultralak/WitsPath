package com.example.witspath.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* over the campus graph with a straight-line heuristic in metres. Every cost factor is at least 1,
 * so the heuristic never overestimates and the result is optimal.
 *
 * Not thread-safe per instance: {@link #errorMessage} holds the result of the last search.
 * Use one PathFinder per search.
 */
public final class PathFinder {
    public static final String SAME_PLACE = "Start and destination are the same place.";
    public static final String NO_ACCESSIBLE_ROUTE =
            "Failed to find an accessible route to the destination node "
                    + "(a path may exist, but only via stairs with no ramp/elevator).";
    public static final String NO_ROUTE = "Failed to find the destination node.";

    /** Why the last search returned null. */
    public String errorMessage = "";

    private static final class Details {
        Node parent;
        double f = Double.MAX_VALUE;
        double g = Double.MAX_VALUE;
        double h = Double.MAX_VALUE;
    }

    private static final class Entry implements Comparable<Entry> {
        final double f;
        final Node node;

        Entry(double f, Node node) {
            this.f = f;
            this.node = node;
        }

        @Override
        public int compareTo(Entry o) {
            int c = Double.compare(f, o.f);
            return c != 0 ? c : Integer.compare(node.index, o.node.index);
        }
    }

    public LinkedList<Node> aStarSearch(Node src, Node goal, boolean requireAccessible) {
        return aStarSearch(src, goal, RouteOptions.accessible(requireAccessible));
    }

    /** @return the path including both ends, or null (see {@link #errorMessage}). */
    public LinkedList<Node> aStarSearch(Node src, Node goal, RouteOptions options) {
        errorMessage = "";
        if (src == goal) {
            errorMessage = SAME_PLACE;
            return null;
        }

        Map<Node, Details> details = new HashMap<>();
        Set<Node> closed = new HashSet<>();
        Details start = new Details();
        start.g = 0;
        start.h = heuristic(src, goal);
        start.f = start.h;
        start.parent = src;
        details.put(src, start);

        PriorityQueue<Entry> open = new PriorityQueue<>();
        open.add(new Entry(start.f, src));

        while (!open.isEmpty()) {
            Node cur = open.poll().node;
            if (!closed.add(cur)) continue;
            if (cur == goal) return tracePath(details, src, goal);

            double gCur = details.get(cur).g;
            for (Edge edge : successors(cur, options)) {
                Node nb = cur.other(edge);
                if (nb == null || closed.contains(nb)) continue;
                double gNew = gCur + edgeCost(edge, options);
                double h = heuristic(nb, goal);
                double fNew = gNew + h;
                Details d = details.get(nb);
                if (d == null || d.f > fNew) {
                    if (d == null) {
                        d = new Details();
                        details.put(nb, d);
                    }
                    d.f = fNew;
                    d.g = gNew;
                    d.h = h;
                    d.parent = cur;
                    open.add(new Entry(fNew, nb));
                }
            }
        }

        errorMessage = options.stepFree ? NO_ACCESSIBLE_ROUTE : NO_ROUTE;
        return null;
    }

    /** Edges a user with these options may walk from {@code node}. */
    List<Edge> successors(Node node, RouteOptions options) {
        List<Edge> result = new ArrayList<>();
        for (Edge e : node.edges) {
            if (!e.isUsable()) continue;
            if (options.stepFree && !e.isStepFree()) continue;
            Double own = e.profileCosts.get(options.dataKey());
            if (own != null && own >= Edge.IMPASSABLE_COST) continue;
            if (e.inaccessibleFor.contains(options.mobilityProfile)) continue;
            if (options.avoidSteepRamps && e.ramp && e.steepRamp) continue;
            result.add(e);
        }
        return result;
    }

    static double edgeCost(Edge e, RouteOptions options) {
        double cost = e.distance * Math.max(1.0, e.costFor(options));
        if (RouteOptions.WALKING_AID.equals(options.mobilityProfile) && e.isStairsOnly()) cost *= 1.5;
        if (options.preferLifts) cost *= e.elevator ? 1.0 : e.ramp ? 1.35 / 0.75 : 1.0 / 0.75;
        return cost;
    }

    private static double heuristic(Node n, Node goal) {
        if (n.point == null || goal.point == null) return 0.0;
        double dx = n.point.x - goal.point.x;
        double dy = n.point.y - goal.point.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static LinkedList<Node> tracePath(Map<Node, Details> details, Node src, Node goal) {
        LinkedList<Node> path = new LinkedList<>();
        Node cur = goal;
        while (cur != null && cur != src) {
            path.addFirst(cur);
            cur = details.get(cur).parent;
        }
        path.addFirst(src);
        return path;
    }
}
