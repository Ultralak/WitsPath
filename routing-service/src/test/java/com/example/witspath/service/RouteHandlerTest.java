package com.example.witspath.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RouteHandlerTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> realGraph() throws Exception {
        String json = new String(Files.readAllBytes(
                Paths.get("..", "app", "src", "main", "assets", "graph_data.json")), StandardCharsets.UTF_8);
        return Json.parseObject(json);
    }

    private static Map<String, Object> request(Map<String, Object> graph, String from, String to, String profile) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("mobilityProfile", profile);
        options.put("stepFree", true);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("graph", graph);
        r.put("fromNodeId", from);
        r.put("toNodeId", to);
        r.put("options", options);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void commerceLibraryRouteMatchesTheApp() throws Exception {
        Map<String, Object> res = RouteHandler.handle(
                request(realGraph(), "nd_mu83zm0ga", "nd_mu842ho1e", "wheelchair"));
        assertEquals(Boolean.TRUE, res.get("ok"));
        assertEquals(68.4, (Double) res.get("distanceM"), 0.06);
        assertTrue(((List<Object>) res.get("path")).size() >= 2);
        assertEquals(Boolean.TRUE, res.get("stepFree"));
        assertTrue(((Number) res.get("minutes")).intValue() >= 1);
        List<Object> steps = (List<Object>) res.get("steps");
        assertEquals("start_at", ((Map<String, Object>) steps.get(0)).get("phraseKey"));
    }

    @Test
    public void unknownNodeIsRejected() throws Exception {
        Map<String, Object> res = RouteHandler.handle(request(realGraph(), "nope", "nd_mu842ho1e", "none"));
        assertEquals(Boolean.FALSE, res.get("ok"));
        assertEquals("unknown_place", res.get("error"));
    }

    @Test
    public void samePlaceIsRejected() throws Exception {
        Map<String, Object> res = RouteHandler.handle(request(realGraph(), "nd_mu83zm0ga", "nd_mu83zm0ga", "none"));
        assertEquals("same_place", res.get("error"));
    }

    @Test
    public void missingFieldsAreABadRequest() {
        Map<String, Object> res = RouteHandler.handle(new LinkedHashMap<String, Object>());
        assertEquals("bad_request", res.get("error"));
    }

    @Test
    public void invalidGraphIsABadRequest() {
        Map<String, Object> graph = new LinkedHashMap<>();
        Map<String, Object> res = RouteHandler.handle(request(graph, "a", "b", "none"));
        assertFalse(Boolean.TRUE.equals(res.get("ok")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void unverifiedTranslationIsNotShownAndFallsBackToEnglish() throws Exception {
        Map<String, Object> req = request(realGraph(), "nd_mu83zm0ga", "nd_mu842ho1e", "wheelchair");
        Map<String, Object> phrases = new LinkedHashMap<>();
        Map<String, Object> unverified = new LinkedHashMap<>();
        unverified.put("text", "Jika ngasekhohlo");
        unverified.put("verifiedBy", "");
        phrases.put("turn_left", unverified);
        Map<String, Object> verified = new LinkedHashMap<>();
        verified.put("text", "Ufikile e-{place}.");
        verified.put("verifiedBy", "Native Speaker");
        phrases.put("arrive", verified);
        req.put("lang", "zu");
        req.put("phrases", phrases);

        Map<String, Object> res = RouteHandler.handle(req);
        List<Object> steps = (List<Object>) res.get("steps");
        for (Object o : steps) {
            Map<String, Object> step = (Map<String, Object>) o;
            assertFalse(String.valueOf(step.get("text")).contains("Jika ngasekhohlo"));
            if ("arrive".equals(step.get("phraseKey"))) {
                assertEquals("zu", step.get("lang"));
                assertEquals(Boolean.FALSE, step.get("fallback"));
            } else {
                assertEquals("en", step.get("lang"));
                assertEquals(Boolean.TRUE, step.get("fallback"));
            }
        }
        assertEquals(Boolean.TRUE, res.get("fallbackToEnglish"));
    }

    @Test
    public void jsonRoundTrip() {
        Map<String, Object> m = Json.parseObject("{\"a\":[1,2.5,\"x\\n\\\"y\\\"\"],\"b\":{\"c\":true,\"d\":null}}");
        Map<String, Object> again = Json.parseObject(Json.write(m));
        assertEquals(m, again);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badJsonThrows() {
        Json.parseObject("{\"a\":");
    }
}
