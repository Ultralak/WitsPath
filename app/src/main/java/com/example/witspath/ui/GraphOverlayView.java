package com.example.witspath.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.example.witspath.model.FloorPlanEdge;
import com.example.witspath.model.FloorPlanNode;
import com.example.witspath.util.MapTransformHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphOverlayView extends View {

    private final Matrix currentMatrix = new Matrix();
    private MapTransformHelper transformHelper;
    
    private final Map<String, FloorPlanNode> nodeMap = new HashMap<>();
    private final List<FloorPlanEdge> edges = new ArrayList<>();
    private final List<String> highlightedRoute = new ArrayList<>();
    
    private String currentPosId;
    private String destinationId;

    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GraphOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        edgePaint.setColor(0x449E9E9E);
        edgePaint.setStrokeWidth(4f);
        edgePaint.setStyle(Paint.Style.STROKE);

        routePaint.setColor(0xFF2196F3); // Default clear blue path color
        routePaint.setStrokeWidth(12f);
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);

        nodePaint.setColor(0xFFFFFFFF);
        nodePaint.setStyle(Paint.Style.FILL);

        markerPaint.setStyle(Paint.Style.FILL);

        float density = context.getResources().getDisplayMetrics().density;
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(12f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(2f * density, 0, 0, 0xFF000000);
    }

    public void setupTransform(int bitmapW, int bitmapH, int jsonW, int jsonH) {
        this.transformHelper = new MapTransformHelper(bitmapW, bitmapH, jsonW, jsonH);
    }

    public void updateTransform(Matrix matrix) {
        this.currentMatrix.set(matrix);
        postInvalidateOnAnimation();
    }

    public void setData(List<FloorPlanNode> nodes, List<FloorPlanEdge> edges) {
        this.nodeMap.clear();
        for (FloorPlanNode n : nodes) nodeMap.put(n.getNodeId(), n);
        this.edges.clear();
        this.edges.addAll(edges);
        invalidate();
    }

    public void setRoute(List<String> routeIds, String currentPosId, String destId) {
        this.highlightedRoute.clear();
        this.highlightedRoute.addAll(routeIds);
        this.currentPosId = currentPosId;
        this.destinationId = destId;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (transformHelper == null || nodeMap.isEmpty()) return;

        // Note: If this view is a child of ZoomableFrameLayout, the canvas 
        // is ALREADY transformed by the zoom/pan matrix. 
        // We only need the JSON-to-Bitmap mapping here.
        float markerRadius = 20f;

        for (FloorPlanEdge edge : edges) {
            FloorPlanNode n1 = nodeMap.get(edge.getFromNodeId());
            FloorPlanNode n2 = nodeMap.get(edge.getToNodeId());
            if (n1 == null || n2 == null) continue;

            boolean inRoute = false;
            for (int i = 0; i < highlightedRoute.size() - 1; i++) {
                if ((highlightedRoute.get(i).equals(edge.getFromNodeId()) && highlightedRoute.get(i + 1).equals(edge.getToNodeId())) ||
                        (highlightedRoute.get(i).equals(edge.getToNodeId()) && highlightedRoute.get(i + 1).equals(edge.getFromNodeId()))) {
                    inRoute = true;
                    break;
                }
            }

            if (!inRoute) continue;

            // Pass currentMatrix instead of identityMatrix so it respects zoomContainer scaling and panning
            float[] p1 = transformHelper.mapToCanvas(n1.getX(), n1.getY(), currentMatrix);
            float[] p2 = transformHelper.mapToCanvas(n2.getX(), n2.getY(), currentMatrix);

            canvas.drawLine(p1[0], p1[1], p2[0], p2[1], routePaint);

            if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
                float midX = (p1[0] + p2[0]) / 2;
                float midY = (p1[1] + p2[1]) / 2;
                canvas.drawText(edge.getLabel(), midX, midY, textPaint);
            }
        }

        for (FloorPlanNode node : nodeMap.values()) {
            // Only draw if the node is part of the travelled highlighted route
            if (!highlightedRoute.contains(node.getNodeId())) {
                continue;
            }

            float[] p = transformHelper.mapToCanvas(node.getX(), node.getY(), currentMatrix);
            
            if (node.getNodeId().equals(currentPosId)) {
                markerPaint.setColor(0xFF4CAF50);
                canvas.drawCircle(p[0], p[1], markerRadius, markerPaint);
            } else if (node.getNodeId().equals(destinationId)) {
                markerPaint.setColor(0xFFF44336);
                canvas.drawCircle(p[0], p[1], markerRadius, markerPaint);
            }
        }
    }
}
