package com.creativedisability.app.model;

public class FrequentedLocation {

    private final String locationId;
    private final String locationName;
    private final String lastVisitedDate;

    public FrequentedLocation(String locationId, String locationName, String lastVisitedDate) {
        this.locationId = locationId;
        this.locationName = locationName;
        this.lastVisitedDate = lastVisitedDate;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getLastVisitedDate() {
        return lastVisitedDate;
    }
}
