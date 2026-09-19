package com.example.witspath.util;

import android.graphics.Matrix;

public class MapTransformHelper {
    private final float scaleX;
    private final float scaleY;

    public MapTransformHelper(int bitmapW, int bitmapH, int jsonW, int jsonH) {
        this.scaleX = (float) bitmapW / jsonW;
        this.scaleY = (float) bitmapH / jsonH;
    }

    public float[] mapToCanvas(float jsonX, float jsonY, Matrix zoomMatrix) {
        // Subtract from X to move LEFT, subtract from Y to move UP (e.g., 15 pixels)
        float shiftedX = (jsonX * scaleX) - 1500f;
        float shiftedY = (jsonY * scaleY) - 1500000f;

        float[] pts = {shiftedX, shiftedY};
        zoomMatrix.mapPoints(pts);
        return pts;
    }

    public static float getVisualScale(Matrix matrix) {
        float[] v = new float[9];
        matrix.getValues(v);
        float scaleX = v[Matrix.MSCALE_X];
        float skewY = v[Matrix.MSKEW_Y];
        return (float) Math.sqrt(scaleX * scaleX + skewY * skewY);
    }
}
