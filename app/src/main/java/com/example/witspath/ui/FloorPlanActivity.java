package com.example.witspath.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.example.witspath.ui.BaseActivity;

import com.example.witspath.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
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
        if (from != null) fromText.setText(from);
        if (to != null) toText.setText(to);
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
