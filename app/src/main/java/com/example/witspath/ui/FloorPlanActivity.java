package com.example.witspath.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.ImageView;
import com.example.witspath.model.FloorPlanEdge;
import com.example.witspath.model.FloorPlanGraphConverter;
import com.example.witspath.model.FloorPlanNode;
import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Floor;
import com.example.witspath.routing.Node;
import com.example.witspath.routing.PhraseBook;
import com.example.witspath.util.GraphStore;
import com.example.witspath.util.Prefs;
import com.example.witspath.util.RoutePlanner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.witspath.ui.BaseActivity;

import com.example.witspath.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloorPlanActivity extends BaseActivity {

    private ZoomableFrameLayout zoomContainer;
    private FloorPlanRouteView routeView;
    private TextView fromText;
    private TextView toText;
    private RoutePlanner.Plan plan;
    private CampusGraph graph;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floor_plan);

        zoomContainer = findViewById(R.id.floorPlanZoomContainer);
        routeView = findViewById(R.id.floorPlanRouteView);
        fromText = findViewById(R.id.fromText);
        toText = findViewById(R.id.toText);

        findViewById(R.id.btnZoomIn).setOnClickListener(v -> zoomContainer.zoomIn());
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> zoomContainer.zoomOut());
        findViewById(R.id.btnResetZoom).setOnClickListener(v -> zoomContainer.resetZoom());

        MaterialToolbar toolbar = findViewById(R.id.floorPlanToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.reportIssueButton).setOnClickListener(v -> chooseSegmentToReport());

        graph = GraphStore.get(this);
        calculateAndDisplayRoute(getIntent().getStringExtra("from_node"), getIntent().getStringExtra("to_node"));
    }

    private void calculateAndDisplayRoute(String fromNodeId, String toNodeId) {
        if (fromNodeId == null || toNodeId == null || graph == null) return;

        Node fromNode = graph.node(fromNodeId);
        Node toNode = graph.node(toNodeId);

        if (fromNode == null || toNode == null) {
            Toast.makeText(this, "Location not found in graph", Toast.LENGTH_SHORT).show();
            return;
        }

        fromText.setText(fromNode.displayName());
        toText.setText(toNode.displayName());

        plan = RoutePlanner.plan(graph, fromNode, toNode, new Prefs(this), PhraseBook.english());
        if (!plan.ok()) {
            Toast.makeText(this, "No route found: " + plan.error, Toast.LENGTH_LONG).show();
        }

        String floorId = fromNode.floorId;
        Floor floor = graph.floor(floorId);

        routeView.setGraph(
                FloorPlanGraphConverter.toFloorPlanNodes(graph, floorId),
                FloorPlanGraphConverter.toFloorPlanEdges(graph, floorId));

        if (plan.ok()) {
            List<String> routeIds = new ArrayList<>();
            for (Node n : plan.nodes) routeIds.add(n.nodeId);
            routeView.setHighlightedRoute(routeIds);
        }

        routeView.setCurrentPosition(fromNode.nodeId);
        routeView.setDestination(toNode.nodeId);

        if (floor != null) {
            zoomContainer.setContentSize(floor.imageWidth, floor.imageHeight);
            routeView.setFloorPlanSize(floor.imageWidth, floor.imageHeight);
        }
    }

    /** Reports go against a real edge: the user picks which segment of the route is the problem. */
    private void chooseSegmentToReport() {
        if (plan == null || !plan.ok() || plan.edgeIds.isEmpty()) return;
        String[] labels = new String[plan.edgeIds.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = plan.nodes.get(i).displayName().trim() + " → " + plan.nodes.get(i + 1).displayName().trim();
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.report_issue_on_path)
                .setItems(labels, (d, which) -> reportObstacle(plan.edgeIds.get(which)))
                .show();
    }

    private void reportObstacle(String edgeId) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        Map<String, Object> report = new HashMap<>();
        report.put("userId", userId);
        report.put("edgeId", edgeId);
        report.put("issueType", "path_obstructed");
        report.put("source", "android-app");
        report.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("reports").add(report)
                .addOnSuccessListener(documentReference ->
                    Toast.makeText(this, R.string.report_success, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                    Toast.makeText(this, R.string.report_failed, Toast.LENGTH_SHORT).show());
    }
}
