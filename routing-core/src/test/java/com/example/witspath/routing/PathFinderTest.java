package com.example.witspath.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.Test;

public class PathFinderTest {

    private static List<String> ids(List<Node> path) {
        List<String> r = new ArrayList<>();
        if (path != null) for (Node n : path) r.add(n.nodeId);
        return r;
    }

    private static List<String> route(CampusGraph g, String a, String b, RouteOptions o) {
        return ids(new PathFinder().aStarSearch(g.node(a), g.node(b), o));
    }

    /** a to c directly by stairs (short), or via b by ramp (long). */
    private static CampusGraph stairsVsRamp() {
        return new GraphBuilder().node("a", 0, 0).node("b", 0, 10).node("c", 10, 0)
                .edgeWith("a", "c", 10, "stairs", true)
                .edgeWith("a", "b", 12, "ramp", true)
                .edgeWith("b", "c", 12, "ramp", true).build();
    }

    @Test
    public void stepFreeAvoidsStairsOnlyEdges() {
        CampusGraph g = stairsVsRamp();
        assertEquals(Arrays.asList("a", "c"), route(g, "a", "c", RouteOptions.accessible(false)));
        assertEquals(Arrays.asList("a", "b", "c"), route(g, "a", "c", RouteOptions.accessible(true)));
    }

    @Test
    public void onlyStairsGivesAccessibleRouteMessage() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("c", 10, 0)
                .edgeWith("a", "c", 10, "stairs", true).build();
        PathFinder pf = new PathFinder();
        assertNull(pf.aStarSearch(g.node("a"), g.node("c"), true));
        assertTrue(pf.errorMessage.contains("accessible route"));
        assertEquals("Failed to find the destination node.",
                disconnectedMessage());
    }

    private static String disconnectedMessage() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("c", 10, 0).build();
        PathFinder pf = new PathFinder();
        assertNull(pf.aStarSearch(g.node("a"), g.node("c"), false));
        return pf.errorMessage;
    }

    @Test
    public void flaggedAndBlockedEdgesAreNeverUsed() {
        for (String status : new String[]{"flagged", "blocked"}) {
            CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0)
                    .edgeWith("a", "b", 5, "status", status).build();
            assertTrue(status, route(g, "a", "b", RouteOptions.accessible(false)).isEmpty());
        }
    }

    @Test
    public void profileCostOf999BlocksOnlyThatProfile() {
        Map<String, Object> costs = new HashMap<>();
        costs.put("wheelchair", 999.0);
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0)
                .edgeWith("a", "b", 5, "accessibilityCosts", costs).build();
        assertTrue(route(g, "a", "b", new RouteOptions("wheelchair", false, false, false)).isEmpty());
        assertEquals(2, route(g, "a", "b", new RouteOptions("walking_aid", false, false, false)).size());
        assertEquals(2, route(g, "a", "b", RouteOptions.accessible(false)).size());
    }

    @Test
    public void generalCostOf999BlocksOnlyStepFreeRouting() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0)
                .edgeWith("a", "b", 5, "accessibilityCost", 999.0).build();
        assertEquals(2, route(g, "a", "b", RouteOptions.accessible(false)).size());
        assertTrue(route(g, "a", "b", RouteOptions.accessible(true)).isEmpty());
    }

    @Test
    public void inaccessibleForBlocksOnlyListedProfile() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0)
                .edgeWith("a", "b", 5, "inaccessibleFor", Arrays.asList("low_vision")).build();
        assertTrue(route(g, "a", "b", new RouteOptions("low_vision", false, false, false)).isEmpty());
        assertEquals(2, route(g, "a", "b", new RouteOptions("wheelchair", false, false, false)).size());
        assertEquals(2, route(g, "a", "b", RouteOptions.accessible(false)).size());
    }

    @Test
    public void wheelchairForcesStepFree() {
        RouteOptions o = new RouteOptions("wheelchair", false, false, false);
        assertTrue(o.stepFree);
        assertEquals(Arrays.asList("a", "b", "c"), route(stairsVsRamp(), "a", "c", o));
    }

    @Test
    public void unknownProfileBecomesNone() {
        assertEquals("none", new RouteOptions("jetpack", false, false, false).mobilityProfile);
    }

    @Test
    public void walkingAidPrefersSlightlyLongerRampOverStairs() {
        // stairs 10 (x1.5 = 15) against ramp route 5 + 5.5 = 10.5
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0).node("c", 10, 0)
                .edgeWith("a", "c", 10, "stairs", true)
                .edgeWith("a", "b", 5, "ramp", true)
                .edgeWith("b", "c", 5.5, "ramp", true).build();
        assertEquals(Arrays.asList("a", "c"), route(g, "a", "c", RouteOptions.accessible(false)));
        assertEquals(Arrays.asList("a", "b", "c"), route(g, "a", "c", new RouteOptions("walking_aid", false, false, false)));
    }

    @Test
    public void preferLiftsPicksLiftOverEqualLengthPlainPath() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("lift", 5, 5).node("walk", 5, -5).node("c", 10, 0)
                .edge("a", "walk", 7.9).edge("walk", "c", 7.9)
                .edgeWith("a", "lift", 8, "elevator", true).edgeWith("lift", "c", 8, "elevator", true).build();
        assertEquals(Arrays.asList("a", "walk", "c"), route(g, "a", "c", RouteOptions.accessible(false)));
        assertEquals(Arrays.asList("a", "lift", "c"), route(g, "a", "c", new RouteOptions("none", false, true, false)));
    }

    @Test
    public void avoidSteepRampsSkipsSteepRamps() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 5).node("c", 10, 0)
                .edgeWith("a", "c", 10, "ramp", true, "steepRamp", true)
                .edge("a", "b", 9).edge("b", "c", 9).build();
        assertEquals(Arrays.asList("a", "c"), route(g, "a", "c", RouteOptions.accessible(false)));
        assertEquals(Arrays.asList("a", "b", "c"), route(g, "a", "c", new RouteOptions("none", false, false, true)));
    }

    @Test
    public void sameNodeReturnsNullWithMessage() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).build();
        PathFinder pf = new PathFinder();
        assertNull(pf.aStarSearch(g.node("a"), g.node("a"), false));
        assertEquals("Start and destination are the same place.", pf.errorMessage);
    }

    @Test
    public void errorMessageIsPerInstance() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0).build();
        PathFinder one = new PathFinder();
        PathFinder two = new PathFinder();
        one.aStarSearch(g.node("a"), g.node("a"), false);
        assertEquals("", two.errorMessage);
    }

    @Test
    public void tiesAreDeterministic() {
        // two equal-length routes; the answer must not change between runs or instances
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("u", 5, 5).node("d", 5, -5).node("c", 10, 0)
                .edge("a", "u", 8).edge("u", "c", 8).edge("a", "d", 8).edge("d", "c", 8).build();
        List<String> first = route(g, "a", "c", RouteOptions.accessible(false));
        for (int i = 0; i < 20; i++) {
            assertEquals(first, route(g, "a", "c", RouteOptions.accessible(false)));
        }
    }

    @Test
    public void pathEdgeIdsFollowThePath() {
        CampusGraph g = stairsVsRamp();
        List<Node> p = new PathFinder().aStarSearch(g.node("a"), g.node("c"), true);
        assertEquals(Arrays.asList("a-b", "b-c"), g.pathEdgeIds(p));
        assertEquals(24.0, g.pathDistance(p), 1e-9);
    }

    @Test
    public void matchesDijkstraOnRandomGraphs() {
        Random rnd = new Random(42);
        for (int round = 0; round < 60; round++) {
            int n = 12;
            GraphBuilder b = new GraphBuilder();
            double[][] xy = new double[n][2];
            for (int i = 0; i < n; i++) {
                xy[i][0] = rnd.nextInt(100);
                xy[i][1] = rnd.nextInt(100);
                b.node("n" + i, xy[i][0], xy[i][1]);
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (rnd.nextInt(4) != 0) continue;
                    double euclid = Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]);
                    // admissible: never shorter than the straight line
                    Map<String, Object> extra = new HashMap<>();
                    if (rnd.nextInt(3) == 0) extra.put("accessibilityCost", 1.0 + rnd.nextInt(3));
                    if (rnd.nextInt(5) == 0) extra.put("status", "blocked");
                    b.edge("n" + i, "n" + j, euclid * (1 + rnd.nextDouble()), extra);
                }
            }
            CampusGraph g = b.build();
            RouteOptions o = RouteOptions.accessible(false);
            for (int t = 0; t < 5; t++) {
                Node s = g.nodes().get(rnd.nextInt(n));
                Node d = g.nodes().get(rnd.nextInt(n));
                if (s == d) continue;
                PathFinder pf = new PathFinder();
                List<Node> path = pf.aStarSearch(s, d, o);
                double expected = dijkstra(g, s, d, o);
                if (path == null) {
                    assertEquals(Double.POSITIVE_INFINITY, expected, 0);
                } else {
                    assertEquals(expected, weightedCost(path, o), 1e-6);
                }
            }
        }
    }

    private static double weightedCost(List<Node> path, RouteOptions o) {
        double c = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            double best = Double.POSITIVE_INFINITY;
            for (Edge e : path.get(i).edges) {
                if (path.get(i).other(e) == path.get(i + 1) && e.isUsable()) best = Math.min(best, PathFinder.edgeCost(e, o));
            }
            c += best;
        }
        return c;
    }

    private static double dijkstra(CampusGraph g, Node s, Node d, RouteOptions o) {
        Map<Node, Double> dist = new HashMap<>();
        PriorityQueue<Object[]> q = new PriorityQueue<>((x, y) -> Double.compare((Double) x[0], (Double) y[0]));
        dist.put(s, 0.0);
        q.add(new Object[]{0.0, s});
        PathFinder pf = new PathFinder();
        while (!q.isEmpty()) {
            Object[] top = q.poll();
            Node u = (Node) top[1];
            if ((Double) top[0] > dist.get(u)) continue;
            if (u == d) return dist.get(u);
            for (Edge e : pf.successors(u, o)) {
                Node v = u.other(e);
                double nd = dist.get(u) + PathFinder.edgeCost(e, o);
                if (nd < dist.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    dist.put(v, nd);
                    q.add(new Object[]{nd, v});
                }
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    @Test
    public void modeSpecificNodeAndEdgeSelection() {
        CampusGraph g = new GraphBuilder()
                .node("a", 0, 0)
                .node("s", "f1", "stairs", 10, 0)
                .node("b", 20, 0)
                .node("r", "f1", "ramp", 10, 10)
                .edgeWith("a", "s", 10, "accessibilityCost", 1.0)
                .edgeWith("s", "b", 10, "accessibilityCost", 999.0)
                .edgeWith("a", "r", 40, "accessibilityCost", 1.0)
                .edgeWith("r", "b", 40, "accessibilityCost", 1.0)
                .build();

        List<String> wheelchair = route(g, "a", "b", new RouteOptions("wheelchair", false, false, false));
        assertEquals(Arrays.asList("a", "r", "b"), wheelchair);

        List<String> lowVision = route(g, "a", "b", new RouteOptions("low_vision", false, false, false));
        assertEquals(Arrays.asList("a", "r", "b"), lowVision);

        List<String> walkingAid = route(g, "a", "b", new RouteOptions("walking_aid", false, false, false));
        assertEquals(Arrays.asList("a", "s", "b"), walkingAid);

        List<String> noPreference = route(g, "a", "b", new RouteOptions("none", false, false, false));
        assertEquals(Arrays.asList("a", "s", "b"), noPreference);
    }

    @Test
    public void commerceLibraryToBusinessSciencesRegression() throws Exception {
        CampusGraph g = RealGraph.load();
        Node from = g.node("nd_mu83zm0ga");
        Node to = g.node("nd_mu842ho1e");
        assertNotNull(from);
        assertNotNull(to);
        List<Node> path = new PathFinder().aStarSearch(from, to, new RouteOptions("wheelchair", true, false, false));
        assertNotNull(path);
        assertEquals(68.4, g.pathDistance(path), 0.06); // 68.35 exactly, shown as 68.4
    }
}
