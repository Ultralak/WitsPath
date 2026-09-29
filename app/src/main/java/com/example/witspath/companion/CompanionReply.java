package com.example.witspath.companion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The server's answer to one companion message (B.5). The phone only renders it. */
public final class CompanionReply {

    public static final class RouteCard {
        public String from = "";
        public String to = "";
        public String fromNodeId = "";
        public String toNodeId = "";
        public double distanceM;
        public boolean accessible;
        /** 0 when no time estimate was requested for this route. */
        public int minutes;
        public final List<String> stepTexts = new ArrayList<>();
        public boolean fallbackToEnglish;
        public String directionsLang = "en";
    }

    public String sessionId = "";
    public String reply = "";
    public String languageCode = "";
    /** user, model, speech or unconfirmed. */
    public String languageSource = "unconfirmed";
    /** full, limited or unsupported. */
    public String languageTier = "";
    public RouteCard route;
    public boolean reportFiled;
    public boolean guardTriggered;

    public static CompanionReply parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        CompanionReply r = new CompanionReply();
        r.sessionId = o.optString("sessionId", "");
        r.reply = o.getString("reply");
        r.reportFiled = o.optBoolean("reportFiled", false);
        r.guardTriggered = o.optBoolean("guardTriggered", false);

        JSONObject lang = o.optJSONObject("language");
        if (lang != null) {
            r.languageCode = lang.optString("code", "");
            r.languageSource = lang.optString("source", "unconfirmed");
            r.languageTier = lang.optString("tier", "");
        }

        JSONObject card = o.optJSONObject("route");
        if (card != null) {
            RouteCard c = new RouteCard();
            c.from = card.optString("from", "");
            c.to = card.optString("to", "");
            c.fromNodeId = card.optString("fromNodeId", "");
            c.toNodeId = card.optString("toNodeId", "");
            c.distanceM = card.optDouble("distanceM", 0);
            c.accessible = card.optBoolean("accessible", false);
            JSONObject t = card.optJSONObject("travelTime");
            c.minutes = t == null ? 0 : t.optInt("minutes", 0);
            c.directionsLang = card.optString("directionsLang", "en");
            c.fallbackToEnglish = card.optBoolean("fallbackToEnglish", false);
            JSONArray steps = card.optJSONArray("steps");
            for (int i = 0; steps != null && i < steps.length(); i++) {
                String text = steps.getJSONObject(i).optString("text", "");
                if (!text.isEmpty()) c.stepTexts.add(text);
            }
            r.route = c;
        }
        return r;
    }
}
