package com.example.witspath.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.example.witspath.model.Edge;
import com.example.witspath.model.Floor;
import com.example.witspath.model.FloorPlanEdge;
import com.example.witspath.model.FloorPlanGraphConverter;
import com.example.witspath.model.FloorPlanNode;
import com.example.witspath.model.Graph;
import com.example.witspath.model.Node;
import com.example.witspath.model.PathFinder;
import com.example.witspath.ui.BaseActivity;

import com.example.witspath.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FloorPlanActivity extends BaseActivity {

    private ZoomableFrameLayout zoomContainer;
    private FloorPlanRouteView routeView;
    private TextView fromText;
    private TextView toText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floor_plan);

        zoomContainer = findViewById(R.id.floorPlanZoomContainer);
        routeView = findViewById(R.id.floorPlanRouteView);
        fromText = findViewById(R.id.fromText);
        toText = findViewById(R.id.toText);

        MaterialToolbar toolbar = findViewById(R.id.floorPlanToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.reportIssueButton).setOnClickListener(v -> reportObstacle());

        String from = getIntent().getStringExtra("from_node");
        String to = getIntent().getStringExtra("to_node");

        // Ensure graph is loaded
        if (Node.searchByName("").isEmpty()) {
            Graph.loadFromAssets(this, "graph_data.json");
        }

        calculateAndDisplayRoute(from, to);
    }

    private void calculateAndDisplayRoute(String fromNodeId, String toNodeId) {
        if (fromNodeId == null || toNodeId == null) return;

        Node fromNode = Node.getByID(fromNodeId);
        Node toNode = Node.getByID(toNodeId);

        if (fromNode == null || toNode == null) {
            Toast.makeText(this, "Location not found in graph", Toast.LENGTH_SHORT).show();
            return;
        }

        // Display labels in toolbar
        fromText.setText(fromNode.label != null ? fromNode.label : fromNode.name);
        toText.setText(toNode.label != null ? toNode.label : toNode.name);

        PathFinder pathFinder = new PathFinder();
        LinkedList<Node> route = pathFinder.aStarSearch(fromNode, toNode, false);

        if (route == null || route.isEmpty()) {
            Toast.makeText(this, "No route found: " + PathFinder.errorMessage, Toast.LENGTH_LONG).show();
        }

        // Convert graph for rendering
        String floorId = fromNode.floorId;
        Floor floor = Floor.getById(floorId);
        double metresPerPixel = (floor != null) ? floor.metresPerPixel : 1.0;

        Collection<Node> allNodes = Node.searchByName("");
        List<FloorPlanNode> renderNodes =
                FloorPlanGraphConverter.toFloorPlanNodes(allNodes, floorId, metresPerPixel);
        
        List<Edge> allEdges = new ArrayList<>();
        for (Node node : allNodes) {
            for (Edge edge : node.edges) {
                if (!allEdges.contains(edge)) {
                    allEdges.add(edge);
                }
            }
        }
        List<FloorPlanEdge> renderEdges =
                FloorPlanGraphConverter.toFloorPlanEdges(allEdges);

        routeView.setGraph(renderNodes, renderEdges);
        
        if (route != null) {
            List<String> routeIds = new ArrayList<>();
            for (Node n : route) {
                routeIds.add(n.nodeId);
            }
            routeView.setHighlightedRoute(routeIds);
        }
        
        routeView.setCurrentPosition(fromNode.nodeId);
        routeView.setDestination(toNode.nodeId);
        
        if (floor != null) {
            routeView.setFloorPlanSize(floor.imageWidth, floor.imageHeight);
        }
    }

    private void reportObstacle() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        Map<String, Object> report = new HashMap<>();
        report.put("userId", userId);
        report.put("edgeId", "placeholder_edge_id");
        report.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("reports").add(report)
                .addOnSuccessListener(documentReference -> 
                    Toast.makeText(this, R.string.report_success, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> 
                    Toast.makeText(this, R.string.report_failed, Toast.LENGTH_SHORT).show());
    }
}
