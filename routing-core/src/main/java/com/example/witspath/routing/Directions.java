package com.example.witspath.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turn-by-turn directions built from phrase templates. */
public final class Directions {

    public static final class Step {
        public final String phraseKey;
        public final Map<String, String> params;
        public final String text;
        public final String lang;
        public final boolean fallback;
        /** Index in the path this step heads to; navigation mode uses it to highlight "you are here". */
        public final int pointIndex;

        Step(String phraseKey, Map<String, String> params, PhraseBook.Resolved r, int pointIndex) {
            this.phraseKey = phraseKey;
            this.params = params;
            this.text = PhraseBook.fill(r.template, params);
            this.lang = r.lang;
            this.fallback = r.fallback;
            this.pointIndex = pointIndex;
        }
    }

    public final List<Step> summary = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    public final double distanceMetres;
    public final int minutes;
    public final boolean stepFree;

    /** True if any line fell back to English because the translation is not verified. */
    public boolean fallbackToEnglish() {
        for (Step s : summary) if (s.fallback) return true;
        for (Step s : steps) if (s.fallback) return true;
        return false;
    }

    private Directions(double distanceMetres, int minutes, boolean stepFree) {
        this.distanceMetres = distanceMetres;
        this.minutes = minutes;
        this.stepFree = stepFree;
    }

    /**
     * @param travelSeconds estimate for the whole path, or a value {@code <= 0} to omit the time line
     */
    public static Directions build(CampusGraph graph, List<Node> path, double travelSeconds, PhraseBook book) {
        double total = graph.pathDistance(path);
        boolean stepFree = true;
        for (int i = 0; i + 1 < path.size(); i++) {
            Edge e = graph.edgeBetween(path.get(i), path.get(i + 1));
            if (e == null || !e.isStepFree()) stepFree = false;
        }
        int minutes = travelSeconds > 0 ? TravelTimeEstimator.minutes(travelSeconds) : 0;
        Directions d = new Directions(total, minutes, stepFree);
        Node last = path.get(path.size() - 1);

        d.summary.add(d.step(book, "route_summary", 0, "place", last.displayName(), "distance", round(total)));
        d.summary.add(d.step(book, stepFree ? "step_free_route" : "not_step_free", 0));
        if (minutes == 1) {
            d.summary.add(d.step(book, "estimated_time_one", 0));
        } else if (minutes > 1) {
            d.summary.add(d.step(book, "estimated_time", 0, "minutes", String.valueOf(minutes)));
        }

        d.steps.add(d.step(book, "start_at", 0, "place", path.get(0).displayName()));
        for (int i = 1; i < path.size(); i++) {
            Node from = path.get(i - 1);
            Node to = path.get(i);
            Edge e = graph.edgeBetween(from, to);
            String place = to.displayName();
            String dist = round(e == null ? 0 : e.distance);

            if ("ramp".equals(from.type)) {
                d.steps.add(d.step(book, "use_ramp", i, "place", from.displayName()));
            }
            if (e != null && e.elevator) {
                d.steps.add(d.step(book, "take_elevator", i, "place", from.displayName()));
            } else if (e != null && e.isStairsOnly()) {
                d.steps.add(d.step(book, "stairs_warning", i, "place", place));
            } else if (e != null && e.ramp && e.steepRamp) {
                d.steps.add(d.step(book, "caution_slope", i, "place", place));
            }

            String key = i == 1 ? "head_towards" : turnKey(path.get(i - 2), from, to);
            d.steps.add(d.step(book, key, i, "place", place, "distance", dist));
        }
        d.steps.add(d.step(book, "arrive", path.size() - 1, "place", last.displayName()));
        return d;
    }

    /** The summary lines (route, step-free, time) as one paragraph. */
    public String summaryText() {
        StringBuilder b = new StringBuilder();
        for (Step s : summary) {
            if (b.length() > 0) b.append(' ');
            b.append(s.text);
        }
        return b.toString();
    }

    /**
     * What to tell someone standing at path node {@code nodeIndex}: the steps that head to the next
     * point, or the arrival step at the end. The first node also gets "Start at ...".
     */
    public String instructionAt(int nodeIndex) {
        StringBuilder b = new StringBuilder();
        int last = steps.get(steps.size() - 1).pointIndex;
        if (nodeIndex >= last) return steps.get(steps.size() - 1).text;
        for (int i = 0; i < steps.size() - 1; i++) {
            Step s = steps.get(i);
            boolean start = "start_at".equals(s.phraseKey) && nodeIndex == 0;
            if (start || (s.pointIndex == nodeIndex + 1 && !"start_at".equals(s.phraseKey))) {
                if (b.length() > 0) b.append(' ');
                b.append(s.text);
            }
        }
        return b.toString();
    }

    /** Classifies the turn at {@code via}. Map pixel coordinates: y points down. */
    static String turnKey(Node prev, Node via, Node next) {
        if (prev.point == null || via.point == null || next.point == null) return "continue_to";
        double ax = via.point.x - prev.point.x, ay = via.point.y - prev.point.y;
        double bx = next.point.x - via.point.x, by = next.point.y - via.point.y;
        double la = Math.hypot(ax, ay), lb = Math.hypot(bx, by);
        if (la == 0 || lb == 0) return "continue_to";
        double cos = Math.max(-1, Math.min(1, (ax * bx + ay * by) / (la * lb)));
        double angle = Math.toDegrees(Math.acos(cos));
        double cross = ax * by - ay * bx;
        if (angle < 25) return "go_straight";
        if (angle < 55) return cross > 0 ? "slight_right" : "slight_left";
        return cross > 0 ? "turn_right" : "turn_left";
    }

    private static String round(double metres) {
        return String.valueOf(Math.round(metres));
    }

    private Step step(PhraseBook book, String key, int pointIndex, String... kv) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) params.put(kv[i], kv[i + 1]);
        return new Step(key, params, book.resolve(key), pointIndex);
    }
}
