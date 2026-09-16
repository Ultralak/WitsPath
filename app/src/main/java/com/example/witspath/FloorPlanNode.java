package com.example.witspath;

/**
 * A single node on the floor-plan graph (junction, ramp, lift, room entrance, etc).
 * Coordinates are in the floor plan's intrinsic pixel space (imageWidth/imageHeight
 * from the graph JSON), NOT screen pixels — {@link FloorPlanRouteView} maps them.
 */
public class FloorPlanNode {

    private final String nodeId;
    private final String floorId;
    private final String type;
    private final String label;
    private final float x;
    private final float y;

    public FloorPlanNode(String nodeId, String floorId, String type, String label, float x, float y) {
        this.nodeId = nodeId;
        this.floorId = floorId;
        this.type = type;
        this.label = label;
        this.x = x;
        this.y = y;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getFloorId() {
        return floorId;
    }

    /** e.g. "junction", "ramp", "lift", "stairs", "entrance", "room", "toilet" */
    public String getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}
