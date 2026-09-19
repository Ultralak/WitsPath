package com.example.witspath.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.witspath.R;
import com.example.witspath.model.FloorPlanEdge;
import com.example.witspath.model.FloorPlanNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the floor-plan graph (all edges, junctions, ramps/lifts), the currently
 * highlighted route, and current-position/destination markers, on top of
 * {@code floorPlanImageView}.
 */
public class FloorPlanRouteView extends View {

    public interface OnFitMatrixChangeListener {
        void onFitMatrixChanged(Matrix fitMatrix);
    }

    private final Matrix fitMatrix = new Matrix();
    private final float[] mappedPoint = new float[2];

    private int floorPlanWidth = 0;
    private int floorPlanHeight = 0;

    private List<FloorPlanNode> nodes = Collections.emptyList();
    private List<FloorPlanEdge> edges = Collections.emptyList();
    private final Map<String, FloorPlanNode> nodesById = new HashMap<>();

    private List<String> highlightedRouteNodeIds = Collections.emptyList();
    private String currentPositionNodeId;
    private String destinationNodeId;

    private OnFitMatrixChangeListener fitMatrixListener;

    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockedEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accessibleNodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentPositionFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentPositionRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint destinationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FloorPlanRouteView(Context context) {
        this(context, null);
    }

    public FloorPlanRouteView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloorPlanRouteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints(context);
    }

    private void initPaints(Context context) {
        edgePaint.setColor(ContextCompat.getColor(context, R.color.colorRule));
        edgePaint.setStrokeWidth(dp(1.5f));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        edgePaint.setAlpha(40);

        blockedEdgePaint.setColor(ContextCompat.getColor(context, R.color.colorFlag));
        blockedEdgePaint.setStrokeWidth(dp(2));
        blockedEdgePaint.setStyle(Paint.Style.STROKE);
        blockedEdgePaint.setStrokeCap(Paint.Cap.ROUND);
        blockedEdgePaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
        blockedEdgePaint.setAlpha(100);

        routePaint.setColor(ContextCompat.getColor(context, R.color.colorRoute));
        routePaint.setStrokeWidth(dp(6));
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);

        routeBorderPaint.setColor(Color.BLACK);
        routeBorderPaint.setAlpha(80);
        routeBorderPaint.setStrokeWidth(dp(10));
        routeBorderPaint.setStyle(Paint.Style.STROKE);
        routeBorderPaint.setStrokeCap(Paint.Cap.ROUND);
        routeBorderPaint.setStrokeJoin(Paint.Join.ROUND);

        nodePaint.setColor(ContextCompat.getColor(context, R.color.colorInkTextMuted));
        nodePaint.setStyle(Paint.Style.FILL);

        accessibleNodePaint.setColor(ContextCompat.getColor(context, R.color.colorAccessible));
        accessibleNodePaint.setStyle(Paint.Style.FILL);

        currentPositionFillPaint.setColor(ContextCompat.getColor(context, R.color.colorRoute));
        currentPositionFillPaint.setStyle(Paint.Style.FILL);

        currentPositionRingPaint.setColor(ContextCompat.getColor(context, R.color.colorRoute));
        currentPositionRingPaint.setStyle(Paint.Style.STROKE);
        currentPositionRingPaint.setStrokeWidth(dp(2));
        currentPositionRingPaint.setAlpha(140);

        destinationPaint.setColor(ContextCompat.getColor(context, R.color.colorFlag));
        destinationPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(dp(2), 0, 0, Color.BLACK);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    public void setOnFitMatrixChangeListener(OnFitMatrixChangeListener listener) {
        this.fitMatrixListener = listener;
        if (fitMatrixListener != null && !fitMatrix.isIdentity()) {
            fitMatrixListener.onFitMatrixChanged(new Matrix(fitMatrix));
        }
    }

    public void setFloorPlanSize(int widthPx, int heightPx) {
        this.floorPlanWidth = widthPx;
        this.floorPlanHeight = heightPx;
        recomputeFitMatrix();
    }

    public void setGraph(List<FloorPlanNode> nodes, List<FloorPlanEdge> edges) {
        this.nodes = (nodes != null) ? nodes : Collections.<FloorPlanNode>emptyList();
        this.edges = (edges != null) ? edges : Collections.<FloorPlanEdge>emptyList();
        nodesById.clear();
        for (FloorPlanNode node : this.nodes) {
            nodesById.put(node.getNodeId(), node);
        }
        invalidate();
    }

    public void setHighlightedRoute(List<String> orderedNodeIds) {
        this.highlightedRouteNodeIds = (orderedNodeIds != null) ? orderedNodeIds : Collections.<String>emptyList();
        invalidate();
    }

    public void setCurrentPosition(String nodeId) {
        this.currentPositionNodeId = nodeId;
        invalidate();
    }

    public void setDestination(String nodeId) {
        this.destinationNodeId = nodeId;
        invalidate();
    }

    public Matrix getFitMatrix() {
        return new Matrix(fitMatrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputeFitMatrix();
    }

    private void recomputeFitMatrix() {
        fitMatrix.reset();
        if (floorPlanWidth <= 0 || floorPlanHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        // Initial map scale must fill the entire available width of the map area.
        float scale = (float) getWidth() / floorPlanWidth;
        fitMatrix.setScale(scale, scale);

        if (fitMatrixListener != null) {
            fitMatrixListener.onFitMatrixChanged(new Matrix(fitMatrix));
        }
        invalidate();
    }

    private float[] mapNode(FloorPlanNode node) {
        mappedPoint[0] = node.getX();
        mappedPoint[1] = node.getY();
        fitMatrix.mapPoints(mappedPoint);
        return mappedPoint;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (nodes.isEmpty() || fitMatrix.isIdentity()) return;

        drawAllEdges(canvas);
        drawHighlightedRoute(canvas);
        drawNodeMarkers(canvas);
        drawDestinationMarker(canvas);
        drawCurrentPositionMarker(canvas);
    }

    private void drawAllEdges(Canvas canvas) {
        for (FloorPlanEdge edge : edges) {
            FloorPlanNode from = nodesById.get(edge.getFromNodeId());
            FloorPlanNode to = nodesById.get(edge.getToNodeId());
            if (from == null || to == null) continue;

            boolean inRoute = false;
            for (int i = 0; i < highlightedRouteNodeIds.size() - 1; i++) {
                if ((highlightedRouteNodeIds.get(i).equals(edge.getFromNodeId()) && highlightedRouteNodeIds.get(i + 1).equals(edge.getToNodeId())) ||
                        (highlightedRouteNodeIds.get(i).equals(edge.getToNodeId()) && highlightedRouteNodeIds.get(i + 1).equals(edge.getFromNodeId()))) {
                    inRoute = true;
                    break;
                }
            }

            if (!inRoute) continue;

            boolean blocked = "blocked".equals(edge.getStatus()) || edge.getAccessibilityCost() >= 999;

            float[] p1 = mapNode(from);
            float x1 = p1[0], y1 = p1[1];
            float[] p2 = mapNode(to);
            float x2 = p2[0], y2 = p2[1];
            
            canvas.drawLine(x1, y1, x2, y2, blocked ? blockedEdgePaint : edgePaint);

            if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
                canvas.drawText(edge.getLabel(), (x1 + x2) / 2, (y1 + y2) / 2, textPaint);
            }
        }
    }

    private void drawHighlightedRoute(Canvas canvas) {
        if (highlightedRouteNodeIds.size() < 2) return;
        
        Path path = new Path();
        boolean first = true;

        for (String nodeId : highlightedRouteNodeIds) {
            FloorPlanNode node = nodesById.get(nodeId);
            if (node == null) continue;
            float[] p = mapNode(node);
            if (first) {
                path.moveTo(p[0], p[1]);
                first = false;
            } else {
                path.lineTo(p[0], p[1]);
            }
        }

        canvas.drawPath(path, routeBorderPaint);
        canvas.drawPath(path, routePaint);
    }

    private void drawNodeMarkers(Canvas canvas) {
        for (FloorPlanNode node : nodes) {
            boolean isAccessibilityFeature = "ramp".equals(node.getType()) || "lift".equals(node.getType());
            // Only draw accessibility features; intermediate junctions are hidden
            if (!isAccessibilityFeature) continue;

            float[] p = mapNode(node);
            canvas.drawCircle(p[0], p[1], dp(5), accessibleNodePaint);
        }
    }

    private void drawDestinationMarker(Canvas canvas) {
        FloorPlanNode destination = (destinationNodeId != null) ? nodesById.get(destinationNodeId) : null;
        if (destination == null) return;
        float[] p = mapNode(destination);
        canvas.drawCircle(p[0], p[1], dp(7), destinationPaint);
    }

    private void drawCurrentPositionMarker(Canvas canvas) {
        FloorPlanNode current = (currentPositionNodeId != null) ? nodesById.get(currentPositionNodeId) : null;
        if (current == null) return;
        float[] p = mapNode(current);
        canvas.drawCircle(p[0], p[1], dp(10), currentPositionRingPaint);
        canvas.drawCircle(p[0], p[1], dp(6), currentPositionFillPaint);
    }
}
