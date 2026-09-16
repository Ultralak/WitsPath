package com.example.witspath;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.witspath.model.EdgeDTO;
import com.example.witspath.model.FirestoreGraphConverter;
import com.example.witspath.model.NodeDTO;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // TEST 1: Verify Local JSON -> DTO -> Algorithm Graph
        try {
            InputStream is = getAssets().open("wavelets-graph.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String jsonString = new String(buffer, "UTF-8");

            JSONObject root = new JSONObject(jsonString);
            JSONArray nodesArray = root.getJSONArray("nodes");
            JSONArray edgesArray = root.getJSONArray("edges");

            List<NodeDTO> nodeDTOs = new ArrayList<>();
            List<EdgeDTO> edgeDTOs = new ArrayList<>();

            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject obj = nodesArray.getJSONObject(i);
                nodeDTOs.add(new NodeDTO(
                        obj.getString("nodeId"), obj.getString("floorId"),
                        obj.getString("type"), obj.getDouble("x"),
                        obj.getDouble("y"), obj.getString("label")
                ));
            }

            for (int i = 0; i < edgesArray.length(); i++) {
                JSONObject obj = edgesArray.getJSONObject(i);
                edgeDTOs.add(new EdgeDTO(
                        obj.getString("edgeId"), obj.getString("fromNodeId"),
                        obj.getString("toNodeId"), obj.getDouble("distance"),
                        obj.getDouble("accessibilityCost"), obj.getString("status"),
                        false, false, false
                ));
            }

            FirestoreGraphConverter.buildGraphFromDTOs(nodeDTOs, edgeDTOs);
            Log.d("TEST_VERIFY", "SUCCESS: Graph built with " + nodeDTOs.size() + " nodes!");

        } catch (Exception e) {
            Log.e("TEST_VERIFY", "FAILED: " + e.getMessage());
        }

        // TEST 2: Direct Firestore Write (No Auth Required)
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        NodeDTO testNode = new NodeDTO("test_1", "flr_1", "junction", 100, 100, "Test Node");

        db.collection("nodes").document("test_1").set(testNode)
                .addOnSuccessListener(aVoid -> Log.d("FIREBASE_TEST", "Firestore Direct Write Successful!"))
                .addOnFailureListener(e -> Log.e("FIREBASE_TEST", "Firestore Write Failed: " + e.getMessage()));
    }
}