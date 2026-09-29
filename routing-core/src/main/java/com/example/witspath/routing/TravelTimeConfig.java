package com.example.witspath.routing;

public final class TravelTimeConfig {
    private TravelTimeConfig() {}

    public static final double DEFAULT_SPEED_MPS = 1.4;
    public static final double OVERHEAD_SECONDS = 30;
    public static final double UPHILL_FACTOR = 1.8;
    public static final double STAIRS_PENALTY_SECONDS = 20;
    public static final double ELEVATOR_PENALTY_SECONDS = 45;
    public static final double DEFAULT_FLOOR_CHANGE_PENALTY = 30;
    public static final double MIN_SPEED_MULTIPLIER = 0.3;
    public static final double MAX_SPEED_MULTIPLIER = 2.0;

    public static double speedFor(String profile) {
        switch (RouteOptions.normaliseProfile(profile)) {
            case RouteOptions.WHEELCHAIR: return 0.8;
            case RouteOptions.WALKING_AID:
            case RouteOptions.LOW_VISION: return 1.1;
            default: return DEFAULT_SPEED_MPS;
        }
    }
}
