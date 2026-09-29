package com.example.witspath.routing;

/** How a route should be planned for one user. Mirrors the Prefs mobility settings. */
public final class RouteOptions {
    public static final String NONE = "none";
    public static final String WHEELCHAIR = "wheelchair";
    public static final String WALKING_AID = "walking_aid";
    public static final String LOW_VISION = "low_vision";

    public final String mobilityProfile;
    public final boolean stepFree;
    public final boolean preferLifts;
    public final boolean avoidSteepRamps;

    public RouteOptions(String mobilityProfile, boolean stepFree, boolean preferLifts, boolean avoidSteepRamps) {
        this.mobilityProfile = normaliseProfile(mobilityProfile);
        this.stepFree = WHEELCHAIR.equals(this.mobilityProfile) || stepFree;
        this.preferLifts = preferLifts;
        this.avoidSteepRamps = avoidSteepRamps;
    }

    public static RouteOptions accessible(boolean requireAccessible) {
        return new RouteOptions(NONE, requireAccessible, false, false);
    }

    /** Unknown ids become NONE. Accepts hyphenated ids. */
    public static String normaliseProfile(String p) {
        if (p == null) return NONE;
        String s = p.trim().toLowerCase().replace('-', '_');
        switch (s) {
            case WHEELCHAIR:
            case WALKING_AID:
            case LOW_VISION:
                return s;
            default:
                return NONE;
        }
    }

    public String dataKey() {
        switch (mobilityProfile) {
            case WALKING_AID: return "walkingAid";
            case LOW_VISION: return "lowVision";
            case WHEELCHAIR: return "wheelchair";
            default: return "noPreference";
        }
    }
}
