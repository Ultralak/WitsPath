package com.example.witspath.model;

public class NodeDTO {
    private String nodeId;
    private String floorId;
    private String type;
    private double x;
    private double y;
    private String label;

    public NodeDTO() {
    }

    public NodeDTO(String nodeId, String floorId, String type, double x, double y, String label) {
        this.nodeId = nodeId;
        this.floorId = floorId;
        this.type = type;
        this.x = x;
        this.y = y;
        this.label = label;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getFloorId() {
        return floorId;
    }

    public void setFloorId(String floorId) {
        this.floorId = floorId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
