package com.example.witspath.routing;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TravelTimeTest {

    private static CampusGraph graph() {
        return new GraphBuilder().floor("f2", 1.0)
                .node("a", 0, 0).node("b", 14, 0).node("c", "f2", "room", 14, 10)
                .node("d", "f1", "room", 24, 10)
                .edgeWith("a", "b", 14, "uphillFrom", "a")
                .edgeWith("b", "c", 10, "stairs", true)
                .edgeWith("c", "d", 10, "elevator", true).build();
    }

    private static List<Node> path(CampusGraph g, String... ids) {
        Node[] ns = new Node[ids.length];
        for (int i = 0; i < ids.length; i++) ns[i] = g.node(ids[i]);
        return Arrays.asList(ns);
    }

    @Test
    public void shortPathsCostNothing() {
        CampusGraph g = graph();
        assertEquals(0, new TravelTimeEstimator(g, 1, "none").estimateSeconds(path(g, "a")), 0);
    }

    @Test
    public void overheadPlusWalkingTime() {
        CampusGraph g = graph();
        // downhill: 30 + 14 / 1.4 = 40
        assertEquals(40, new TravelTimeEstimator(g, 1, "none").estimateSeconds(path(g, "b", "a")), 1e-9);
    }

    @Test
    public void uphillIsSlower() {
        CampusGraph g = graph();
        double up = new TravelTimeEstimator(g, 1, "none").estimateSeconds(path(g, "a", "b"));
        double down = new TravelTimeEstimator(g, 1, "none").estimateSeconds(path(g, "b", "a"));
        assertEquals(30 + 14 * 1.8 / 1.4, up, 1e-9);
        assertEquals(30 + 14 / 1.4, down, 1e-9);
    }

    @Test
    public void floorChangePenalties() {
        CampusGraph g = graph();
        TravelTimeEstimator t = new TravelTimeEstimator(g, 1, "none");
        assertEquals(30 + 10 / 1.4 + 20, t.estimateSeconds(path(g, "b", "c")), 1e-9);
        assertEquals(30 + 10 / 1.4 + 45, t.estimateSeconds(path(g, "c", "d")), 1e-9);
    }

    @Test
    public void speedsPerProfile() {
        CampusGraph g = graph();
        assertEquals(30 + 14 / 0.8, new TravelTimeEstimator(g, 1, "wheelchair").estimateSeconds(path(g, "b", "a")), 1e-9);
        assertEquals(30 + 14 / 1.1, new TravelTimeEstimator(g, 1, "walking_aid").estimateSeconds(path(g, "b", "a")), 1e-9);
        assertEquals(30 + 14 / 1.1, new TravelTimeEstimator(g, 1, "low_vision").estimateSeconds(path(g, "b", "a")), 1e-9);
    }

    @Test
    public void speedMultiplierIsClampedAndDefaulted() {
        CampusGraph g = graph();
        assertEquals(30 + 14 / (1.4 * 2.0), new TravelTimeEstimator(g, 9, "none").estimateSeconds(path(g, "b", "a")), 1e-9);
        assertEquals(30 + 14 / (1.4 * 0.3), new TravelTimeEstimator(g, 0.01, "none").estimateSeconds(path(g, "b", "a")), 1e-9);
        assertEquals(30 + 14 / 1.4, new TravelTimeEstimator(g, 0, "none").estimateSeconds(path(g, "b", "a")), 1e-9);
    }

    @Test
    public void minutesRoundUpWithMinimumOfOne() {
        assertEquals(1, TravelTimeEstimator.minutes(0));
        assertEquals(1, TravelTimeEstimator.minutes(5));
        assertEquals(1, TravelTimeEstimator.minutes(60));
        assertEquals(2, TravelTimeEstimator.minutes(61));
    }
}
