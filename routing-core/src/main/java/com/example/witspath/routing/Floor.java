package com.example.witspath.routing;

/** Metadata for one floor plan image. Node coordinates in the graph JSON are pixels on this image. */
public final class Floor {
    public final String floorId;
    public final String name;
    public final int level;
    public final int imageWidth;
    public final int imageHeight;
    public final double metresPerPixel;

    public Floor(String floorId, String name, int level, int imageWidth, int imageHeight, double metresPerPixel) {
        this.floorId = floorId;
        this.name = name;
        this.level = level;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.metresPerPixel = metresPerPixel;
    }

    public double pixelsToMetres(double px) {
        return px * metresPerPixel;
    }

    public double metresToPixels(double metres) {
        return metresPerPixel > 0 ? metres / metresPerPixel : metres;
    }
}
