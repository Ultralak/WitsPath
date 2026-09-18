package com.example.witspath.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata for a single floor plan -- one per {@code floors[]} entry in the
 * graph JSON (e.g. "West Campus", level 0). Distinct from Area: Area is a
 * building/precinct grouping (see Node.Area), Floor is a physical level
 * within a building/site. A single Area can span several Floors, and a
 * single Floor's plan can contain nodes belonging to several Areas -- they
 * are independent axes, not a hierarchy.
 *
 * Holds the data other classes currently have nowhere to look up:
 *  - metresPerPixel: needed by FloorPlanGraphConverter to convert Node's
 *    metre-space coordinates back into the pixel space FloorPlanNode expects.
 *  - imageWidth/imageHeight: needed by FloorPlanRouteView / FloorPlanActivity
 *    to load and scale the correct floor-plan image asset.
 *  - name/level: needed for a floor picker UI (e.g. backing item_floor_header.xml),
 *    ordered by level (ground = 0, going up/down from there).
 */
public class Floor {

    public String floorId;
    public String name;
    public int level;
    public int imageWidth;
    public int imageHeight;
    public double metresPerPixel;

    private static final Map<String, Floor> floors = new HashMap<>();

    public Floor(String floorId, String name, int level, int imageWidth, int imageHeight, double metresPerPixel) {
        this.floorId = floorId;
        this.name = name;
        this.level = level;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.metresPerPixel = metresPerPixel;

        floors.put(floorId, this);
    }

    public static Floor getById(String floorId) {
        return floors.get(floorId);
    }

    /** All known floors, ordered by level (e.g. for a floor-switcher UI). */
    public static ArrayList<Floor> allSortedByLevel() {
        ArrayList<Floor> result = new ArrayList<>(floors.values());
        result.sort(Comparator.comparingInt(f -> f.level));
        return result;
    }

    /** Converts a pixel-space coordinate on this floor's image into metres. */
    public double pixelsToMetres(double pixelValue) {
        return pixelValue * metresPerPixel;
    }

    /** Converts a metre-space coordinate back into this floor's pixel space. */
    public double metresToPixels(double metreValue) {
        return metreValue / metresPerPixel;
    }
}

































