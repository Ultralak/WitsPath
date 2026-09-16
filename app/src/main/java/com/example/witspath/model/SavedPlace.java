package com.example.witspath.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Team Wavelets - WitsPath
 * Frontend-only local model for "Saved places" (Prefs.KEY_SAVED_PLACES_JSON).
 * Mirrors the shape of a users/{uid}/savedPlaces/{id} Firestore document
 * (nodeId, label, detail) so Person 3 can swap this for a live Firestore query
 * without changing item_saved_place.xml or its binding code.
 */
public final class SavedPlace {

    public final String nodeId;
    public final String label;
    public final String detail;

    public SavedPlace(String nodeId, String label, String detail) {
        this.nodeId = nodeId;
        this.label = label;
        this.detail = detail;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("nodeId", nodeId);
        o.put("label", label);
        o.put("detail", detail);
        return o;
    }

    public static SavedPlace fromJson(JSONObject o) throws JSONException {
        return new SavedPlace(o.getString("nodeId"), o.getString("label"), o.getString("detail"));
    }

    public static String toJsonArray(List<SavedPlace> places) {
        JSONArray array = new JSONArray();
        try {
            for (SavedPlace place : places) {
                array.put(place.toJson());
            }
        } catch (JSONException e) {
        }
        return array.toString();
    }

    public static List<SavedPlace> fromJsonArray(String json) {
        List<SavedPlace> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
        }
        return result;
    }
}
