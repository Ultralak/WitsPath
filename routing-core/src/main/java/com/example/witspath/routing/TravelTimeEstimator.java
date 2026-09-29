package com.example.witspath.routing;

import java.util.List;

/** Estimates walking time along a path. Always present the result as an estimate. */
public final class TravelTimeEstimator {
    private final CampusGraph graph;
    private final double speedMultiplier;
    private final String mobilityProfile;

    /** {@code speedMultiplier <= 0} means 1.0; the user's walking-speed setting is clamped to [0.3, 2.0]. */
    public TravelTimeEstimator(CampusGraph graph, double speedMultiplier, String mobilityProfile) {
        this.graph = graph;
        double m = speedMultiplier <= 0 ? 1.0 : speedMultiplier;
        this.speedMultiplier = Math.max(TravelTimeConfig.MIN_SPEED_MULTIPLIER,
                Math.min(TravelTimeConfig.MAX_SPEED_MULTIPLIER, m));
        this.mobilityProfile = mobilityProfile;
    }

    public double estimateSeconds(List<Node> path) {
        if (path == null || path.size() < 2) return 0;
        double t = TravelTimeConfig.OVERHEAD_SECONDS;
        double speed = TravelTimeConfig.speedFor(mobilityProfile) * speedMultiplier;
        for (int i = 0; i < path.size() - 1; i++) {
            Node cur = path.get(i);
            Node next = path.get(i + 1);
            Edge edge = graph.edgeBetween(cur, next);
            if (edge != null) {
                double slope = cur.nodeId.equals(edge.uphillFromNodeId) ? TravelTimeConfig.UPHILL_FACTOR : 1.0;
                t += edge.distance * slope / speed;
            }
            if (cur.floorId != null && next.floorId != null && !cur.floorId.equals(next.floorId)) {
                t += edge == null ? TravelTimeConfig.DEFAULT_FLOOR_CHANGE_PENALTY
                        : edge.elevator ? TravelTimeConfig.ELEVATOR_PENALTY_SECONDS
                        : edge.stairs ? TravelTimeConfig.STAIRS_PENALTY_SECONDS
                        : TravelTimeConfig.DEFAULT_FLOOR_CHANGE_PENALTY;
            }
        }
        return t;
    }

    /** Whole minutes to show the user: {@code max(1, ceil(seconds / 60))}. */
    public static int minutes(double seconds) {
        return (int) Math.max(1, Math.ceil(seconds / 60.0));
    }
}
