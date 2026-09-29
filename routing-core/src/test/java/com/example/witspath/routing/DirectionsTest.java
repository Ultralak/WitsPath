package com.example.witspath.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DirectionsTest {

    /** a east to b, then south to c (a right turn with y pointing down), then east to d. */
    private static CampusGraph graph() {
        return new GraphBuilder().node("a", 0, 0).node("b", 10, 0).node("c", 10, 10).node("d", 20, 10)
                .edge("a", "b", 10).edge("b", "c", 10).edge("c", "d", 10).build();
    }

    private static List<Node> path(CampusGraph g) {
        return new PathFinder().aStarSearch(g.node("a"), g.node("d"), false);
    }

    @Test
    public void buildsStepsFromTemplates() {
        CampusGraph g = graph();
        Directions d = Directions.build(g, path(g), 100, PhraseBook.english());
        assertEquals("Route to D: 30 metres.", d.summary.get(0).text);
        assertEquals("This route is step-free.", d.summary.get(1).text);
        assertEquals("Estimated time: about 2 minutes.", d.summary.get(2).text);
        assertEquals("Start at A.", d.steps.get(0).text);
        assertEquals("Head towards B for 10 metres.", d.steps.get(1).text);
        assertEquals("Turn right and continue 10 metres to C.", d.steps.get(2).text);
        assertEquals("Turn left and continue 10 metres to D.", d.steps.get(3).text);
        assertEquals("You have arrived at D.", d.steps.get(4).text);
        assertEquals(3, d.steps.get(4).pointIndex);
        assertFalse(d.fallbackToEnglish());
    }

    @Test
    public void instructionAtNode() {
        CampusGraph g = graph();
        Directions d = Directions.build(g, path(g), 0, PhraseBook.english());
        assertEquals("Start at A. Head towards B for 10 metres.", d.instructionAt(0));
        assertEquals("Turn right and continue 10 metres to C.", d.instructionAt(1));
        assertEquals("You have arrived at D.", d.instructionAt(3));
    }

    @Test
    public void stairsMakeRouteNotStepFree() {
        CampusGraph g = new GraphBuilder().node("a", 0, 0).node("b", 10, 0)
                .edgeWith("a", "b", 10, "stairs", true).build();
        Directions d = Directions.build(g, path2(g), 0, PhraseBook.english());
        assertEquals("This route is not confirmed step-free.", d.summary.get(1).text);
        assertEquals(2, d.summary.size());
        assertEquals("stairs_warning", d.steps.get(1).phraseKey);
    }

    private static List<Node> path2(CampusGraph g) {
        return new PathFinder().aStarSearch(g.node("a"), g.node("b"), false);
    }

    @Test
    public void turnClassification() {
        CampusGraph g = new GraphBuilder().node("p", 0, 0).node("v", 10, 0)
                .node("straight", 20, 1).node("slightR", 20, 8).node("slightL", 20, -8)
                .node("hardR", 10, 10).node("hardL", 10, -10).node("same", 10, 0).build();
        Node p = g.node("p"), v = g.node("v");
        assertEquals("go_straight", Directions.turnKey(p, v, g.node("straight")));
        assertEquals("slight_right", Directions.turnKey(p, v, g.node("slightR")));
        assertEquals("slight_left", Directions.turnKey(p, v, g.node("slightL")));
        assertEquals("turn_right", Directions.turnKey(p, v, g.node("hardR")));
        assertEquals("turn_left", Directions.turnKey(p, v, g.node("hardL")));
        assertEquals("continue_to", Directions.turnKey(p, v, g.node("same")));
    }

    @Test
    public void unverifiedTranslationFallsBackToEnglish() {
        Map<String, PhraseBook.Translation> zu = new HashMap<>();
        zu.put("turn_left", new PhraseBook.Translation("Jika ngasekhohlo", ""));
        PhraseBook.Resolved r = PhraseBook.forLanguage("zu", zu).resolve("turn_left");
        assertTrue(r.fallback);
        assertEquals("en", r.lang);
        assertTrue(r.template.startsWith("Turn left"));
    }

    @Test
    public void verifiedTranslationIsUsed() {
        Map<String, PhraseBook.Translation> zu = new HashMap<>();
        zu.put("arrive", new PhraseBook.Translation("Ufikile e-{place}.", "Native Speaker"));
        CampusGraph g = graph();
        Directions d = Directions.build(g, path(g), 0, PhraseBook.forLanguage("zu", zu));
        Directions.Step arrive = d.steps.get(d.steps.size() - 1);
        assertEquals("Ufikile e-D.", arrive.text);
        assertEquals("zu", arrive.lang);
        assertFalse(arrive.fallback);
        assertTrue(d.fallbackToEnglish()); // the other lines are still English fallbacks
        assertTrue(d.steps.get(0).fallback);
    }

    @Test
    public void everyPhraseKeyIsResolvable() {
        for (String key : PhraseBook.ENGLISH.keySet()) {
            assertEquals("en", PhraseBook.english().resolve(key).lang);
        }
        assertEquals(Arrays.asList("en"), Arrays.asList(PhraseBook.english().lang()));
    }
}
