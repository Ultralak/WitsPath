package com.example.witspath.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CampusGraphTest {

    @Test
    public void pixelsAreConvertedToMetres() {
        CampusGraph g = new GraphBuilder().floor("f2", 0.5).node("a", "f2", "room", 100, 40).build();
        Node a = g.node("a");
        assertEquals(50.0, a.point.x, 1e-9);
        assertEquals(20.0, a.point.y, 1e-9);
        assertEquals(1, a.index);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nodeOnUnknownFloorThrows() {
        new GraphBuilder().node("a", "nope", "room", 1, 1).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void nodeWithoutCoordinatesThrows() {
        GraphBuilder b = new GraphBuilder();
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("nodeId", "a");
        n.put("floorId", "f1");
        b.nodes.add(n);
        b.build();
    }

    @Test
    public void edgeWithUnknownNodeIsSkippedWithWarning() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0)
                .edge("a", "b", 5).edge("a", "ghost", 3).build();
        assertEquals(1, g.edges().size());
        assertEquals(1, g.warnings().size());
        assertTrue(g.warnings().get(0).contains("ghost"));
    }

    @Test
    public void edgeIdDefaultsToIndex() {
        GraphBuilder b = new GraphBuilder().node("a", 0, 0).node("b", 5, 0);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("fromNodeId", "a");
        e.put("toNodeId", "b");
        e.put("distance", 5.0);
        b.edges.add(e);
        assertNotNull(b.build().edge("edge_0"));
    }

    @Test
    public void liftIsAnAliasForElevator() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0)
                .edgeWith("a", "b", 5, "lift", true).build();
        assertTrue(g.edges().get(0).elevator);
    }

    @Test
    public void optionalFieldSpellingsAreAccepted() {
        Map<String, Object> costs = new LinkedHashMap<>();
        costs.put("wheelchair", 999.0);
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0).node("c", 9, 0)
                .edgeWith("a", "b", 5, "accessibilityCostByProfile", costs, "rampGrade", "steep", "ramp", true,
                        "inaccessibleFor", new ArrayList<>(Arrays.asList("walking-aid", "no_preference")))
                .edgeWith("b", "c", 4, "steep", true).build();
        Edge e = g.edge("a-b");
        assertEquals(999.0, e.profileCosts.get("wheelchair"), 0);
        assertTrue(e.steepRamp);
        assertTrue(e.inaccessibleFor.contains("walking_aid"));
        assertTrue(e.inaccessibleFor.contains("none"));
        assertTrue(g.edge("b-c").steepRamp);
    }

    @Test
    public void edgesAreUndirected() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0).edge("a", "b", 5).build();
        Node a = g.node("a"), b = g.node("b");
        assertEquals(1, a.edges.size());
        assertEquals(1, b.edges.size());
        assertEquals(b, a.other(a.edges.get(0)));
        assertEquals(a, b.other(b.edges.get(0)));
        assertNull(a.other(new GraphBuilder().node("x", 0, 0).node("y", 1, 0).edge("x", "y", 1).build().edges().get(0)));
    }

    @Test
    public void statusOnlyOkIsUsable() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 5, 0)
                .edgeWith("a", "b", 5, "status", "flagged").build();
        Edge e = g.edges().get(0);
        assertFalse(e.isUsable());
        e.setStatus("ok");
        assertTrue(e.isUsable());
    }

    @Test
    public void realGraphLoadsWithoutWarnings() throws Exception {
        CampusGraph g = RealGraph.load();
        assertFalse(g.nodes().isEmpty());
        assertTrue(g.warnings().toString(), g.warnings().isEmpty());
    }
}
