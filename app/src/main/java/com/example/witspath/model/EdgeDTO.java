package com.example.witspath.model;

public class EdgeDTO {
    private String edgeId;
    private String fromNodeId;
    private String toNodeId;
    private double distance;
    private double accessibilityCost;
    private String status;
    private boolean ramp;
    private boolean stairs;
    private boolean elevator;

    public EdgeDTO() {
    }

    public EdgeDTO(String edgeId, String fromNodeId, String toNodeId, double distance, double accessibilityCost, String status, boolean ramp, boolean stairs, boolean elevator) {
        this.edgeId = edgeId;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.distance = distance;
        this.accessibilityCost = accessibilityCost;
        this.status = status;
        this.ramp = ramp;
        this.stairs = stairs;
        this.elevator = elevator;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(String fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getAccessibilityCost() {
        return accessibilityCost;
    }

    public void setAccessibilityCost(double accessibilityCost) {
        this.accessibilityCost = accessibilityCost;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isRamp() {
        return ramp;
    }

    public void setRamp(boolean ramp) {
        this.ramp = ramp;
    }

    public boolean isStairs() {
        return stairs;
    }

    public void setStairs(boolean stairs) {
        this.stairs = stairs;
    }

    public boolean isElevator() {
        return elevator;
    }

    public void setElevator(boolean elevator) {
        this.elevator = elevator;
    }
}
