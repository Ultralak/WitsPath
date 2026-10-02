package com.example.witspath.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class PlaceResolverTest {

    private static CampusGraph graph() {
        return new GraphBuilder()
                .node("lib", 0, 0)
                .node("a1", 10, 0)
                .node("a2", 20, 0)
                .build();
    }

    @Test
    public void aKnownIdIsReturnedAsIs() {
        CampusGraph g = graph();
        assertSame(g.node("lib"), PlaceResolver.resolve(g, "lib", "anything"));
    }

    @Test
    public void anUnknownIdFallsBackToAnExactLabelMatch() {
        CampusGraph g = graph();
        // GraphBuilder labels a node with its id in capitals
        assertSame(g.node("lib"), PlaceResolver.resolve(g, "old-id-from-another-map", "  LIB "));
        assertSame(g.node("lib"), PlaceResolver.resolve(g, null, "lib"));
    }

    @Test
    public void accentsAndCaseAreIgnored() {
        GraphBuilder b = new GraphBuilder().node("x", 0, 0);
        CampusGraph g = b.build();
        assertSame(g.node("x"), PlaceResolver.resolve(g, "nope", "x"));
    }

    @Test
    public void aSimilarButDifferentNameIsNotAMatch() {
        CampusGraph g = graph();
        assertNull(PlaceResolver.resolve(g, "nope", "Commerce Library"));
        assertNull(PlaceResolver.resolve(g, "nope", "LI"));
    }

    @Test
    public void twoPlacesWithTheSameNameGiveNoAnswer() {
        GraphBuilder b = new GraphBuilder().node("same", 0, 0);
        java.util.Map<String, Object> dup = new java.util.LinkedHashMap<>();
        dup.put("nodeId", "same2");
        dup.put("floorId", "f1");
        dup.put("type", "room");
        dup.put("label", "SAME");
        dup.put("x", 5.0);
        dup.put("y", 5.0);
        b.nodes.add(dup);
        assertNull(PlaceResolver.resolve(b.build(), "nope", "same"));
    }

    @Test
    public void nothingToGoOnGivesNull() {
        CampusGraph g = graph();
        assertNull(PlaceResolver.resolve(g, "nope", null));
        assertNull(PlaceResolver.resolve(g, "nope", ""));
        assertNull(PlaceResolver.resolve(g, null, null));
        assertNull(PlaceResolver.resolve(null, "lib", "lib"));
        assertEquals(null, PlaceResolver.resolve(g, "", "   "));
    }
}
